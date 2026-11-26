package io.github.yilink1.hyperselect

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ContentProvider
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class MainHook : XposedModule() {

    companion object {
        private const val TAG = "HyperSelect"
        private const val DEBUG = false
        private const val VOICE_ASSIST_PACKAGE = "com.miui.voiceassist"
        private const val AI_EDITOR_WAKE_UP = "xiaoai.supportAIEditorWakeUp"
        const val EXTRA_RAW_QUERY = "io.github.yilink1.hyperselect.EXTRA_RAW_QUERY"

        // 记录的最长有效期，读取时超过则视为过期并丢弃
        private const val REMOVED_TARGETS_TTL_MS = 10 * 60_000L

        @Volatile
        private var cachedRules: RuleConfig? = null

        @Volatile
        private var observerRegistered = false

        @Volatile
        private var moduleUid = -1

        @Volatile
        private var resolverAppId = -1

        private val workerThread = HandlerThread("HyperSelect-Worker").apply {
            setUncaughtExceptionHandler { _, throwable ->
                Log.e(TAG, "Caught unhandled exception in HyperSelect-Worker:\n${Log.getStackTraceString(throwable)}")
            }
            start()
        }
        private val workerHandler = Handler(workerThread.looper)
    }

    @Volatile private var servicesReady = false
    private val refreshLock = Any()
    private var refreshAt = Long.MAX_VALUE
    private var retryDelayMs = 1_000L
    private var settingsProviderHooked = false
    private val tileSettingsLock = Any()
    private val refreshTask = Runnable {
        synchronized(refreshLock) { refreshAt = Long.MAX_VALUE }
        if (!servicesReady) {
            // Fallback for ROMs without the expected boot-phase method.
            servicesReady = runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java)
                    .invoke(null, "sys.boot_completed") == "1"
            }.getOrDefault(false)
        }
        if (!servicesReady) {
            scheduleRefresh(1_000L)
        } else if (refreshRules()) {
            retryDelayMs = 1_000L
        } else {
            scheduleRefresh(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
        }
    }

    private fun scheduleRefresh(delayMs: Long = 0L) {
        synchronized(refreshLock) {
            val at = SystemClock.uptimeMillis() + delayMs
            if (refreshAt <= at) return
            workerHandler.removeCallbacks(refreshTask)
            refreshAt = at
            workerHandler.postAtTime(refreshTask, at)
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (DEBUG) {
            log(Log.INFO, TAG, "onModuleLoaded: process=${param.processName}, isSystemServer=${param.isSystemServer}")
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == "com.android.intentresolver") {
            hookIntentResolver(param.classLoader)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        val sdkInt = Build.VERSION.SDK_INT
        var resolveHooked = false
        var queryHooked = false
        var queryOptionsHooked = false
        var getPackageHooked = false
        var getApplicationHooked = false
        var queryServicesHooked = false

        // PHASE_THIRD_PARTY_APPS_CAN_START: application providers may now be started.
        runCatching {
            Class.forName("com.android.server.SystemServiceManager", false, classLoader)
                .declaredMethods.filter {
                    it.name == "startBootPhase" && it.parameterTypes.lastOrNull() == Int::class.javaPrimitiveType
                }.forEach { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if ((chain.args.last() as Int) >= 600 && !servicesReady) {
                            servicesReady = true
                            scheduleRefresh()
                        }
                        result
                    }
                }
        }.onFailure { log(Log.WARN, TAG, "Boot-phase hook unavailable; using boot-completed fallback") }
        scheduleRefresh(1_000L)

        runCatching {
            val ipmBaseClass = Class.forName("com.android.server.pm.IPackageManagerBase", false, classLoader)

            // 1. Hook resolveIntent -> Block Query search fallback
            resolveHooked = hookResolveIntent(ipmBaseClass)

            // 2. Hook queryIntentActivities -> Block search fallback & manage PROCESS_TEXT items
            queryHooked = hookQueryIntentActivities(ipmBaseClass, classLoader)
            queryOptionsHooked = hookQueryIntentActivityOptions(ipmBaseClass, classLoader)

            // 3. Hook getPackageInfo -> Globally block AiRecognition across all apps
            getPackageHooked = hookGetPackageInfo(ipmBaseClass)

            getApplicationHooked = hookGetApplicationInfo(ipmBaseClass)

            // 4. Hook queryIntentServices -> Filter hidden QS Tiles for SystemUI
            queryServicesHooked = hookQueryIntentServices(ipmBaseClass, classLoader)
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook system_server IPackageManagerBase on Android $sdkInt:\n${Log.getStackTraceString(it)}")
        }

        // Diagnostic log: printed once on system_server startup
        log(
            Log.INFO,
            TAG,
            "HyperSelect initialized on Android $sdkInt | Hooks: [resolveIntent=$resolveHooked, queryIntent=$queryHooked, queryOptions=$queryOptionsHooked, queryServices=$queryServicesHooked, getPackageInfo=$getPackageHooked, getApplicationInfo=$getApplicationHooked]"
        )
    }

    private fun getSystemContext(): Context? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            val activityThread = currentActivityThreadMethod.invoke(null)
            val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
            getSystemContextMethod.invoke(activityThread) as? Context
        }.getOrNull()
    }

    // Called only on the worker, after system services are ready.
    private fun refreshRules(): Boolean {
        val token = Binder.clearCallingIdentity()
        return try {
            val context = getSystemContext() ?: return false
            if (!settingsProviderHooked) {
                settingsProviderHooked = hookSettingsProvider(context)
            }
            if (!observerRegistered) {
                context.contentResolver.registerContentObserver(
                    ConfigProvider.CONTENT_URI, true,
                    object : ContentObserver(workerHandler) {
                        override fun onChange(selfChange: Boolean) { scheduleRefresh() }
                    }
                )
                // 普通应用无法读取 sysui_qs_tiles，由 system_server 代读并随规则同步给模块。
                runCatching {
                    context.contentResolver.registerContentObserver(
                        Settings.Secure.getUriFor(ConfigProvider.SETTING_QS_TILES), false,
                        object : ContentObserver(workerHandler) {
                            override fun onChange(selfChange: Boolean) { scheduleRefresh() }
                        }
                    )
                }
                observerRegistered = true
            }
            val extras = Bundle().apply {
                readQsTiles(context).getOrNull()?.let {
                    putString(ConfigProvider.EXTRA_QS_TILES, it)
                }
            }
            val bundle = context.contentResolver.call(
                ConfigProvider.CONTENT_URI,
                ConfigProvider.METHOD_GET_RULES,
                null,
                extras
            )
            val json = bundle?.getString(ConfigProvider.EXTRA_RULES_JSON)
            val config = RuleConfig.parseResponse(json) ?: return false
            val previousBlockAiWriting = cachedRules?.blockAiWriting
            cachedRules = config
            if (previousBlockAiWriting != config.blockAiWriting) invalidatePackageInfoCache()
            runCatching {
                val ai = context.packageManager.getApplicationInfo("com.android.intentresolver", 0)
                resolverAppId = ai.uid % 100000
            }
            if (DEBUG) log(Log.INFO, TAG, "Rules updated async: blacklist=${config.blacklist.size}, order=${config.customOrder.size}, share=${config.shareBlacklist.size}, openWith=${config.openWithBlacklist.size}")
            true
        } catch (t: Throwable) {
            if (DEBUG) log(Log.WARN, TAG, "Failed to refresh rules async: ${t.message}")
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    // Runs in system_server. App processes cache ApplicationInfo, including its metadata.
    @SuppressLint("BlockedPrivateApi")
    private fun invalidatePackageInfoCache() {
        runCatching {
            PackageManager::class.java.getDeclaredMethod("invalidatePackageInfoCache").invoke(null)
        }.onFailure { log(Log.WARN, TAG, "Failed to invalidate package info cache", it) }
    }

    private fun isModuleCaller(callingUid: Int, context: Context?): Boolean {
        if (callingUid <= 0) return false
        if (moduleUid > 0 && callingUid == moduleUid) return true
        val ctx = context ?: getSystemContext() ?: return false
        val token = Binder.clearCallingIdentity()
        return try {
            val pkgName = ctx.packageManager.getNameForUid(callingUid)
            if (pkgName == "io.github.yilink1.hyperselect") {
                moduleUid = callingUid
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun isNotesAppCaller(callingUid: Int, context: Context?): Boolean {
        if (callingUid <= 0) return false
        val ctx = context ?: getSystemContext() ?: return false
        val token = Binder.clearCallingIdentity()
        return try {
            ctx.packageManager.getPackagesForUid(callingUid)?.contains("com.miui.notes") == true
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun isIntentResolverCaller(callingUid: Int, context: Context?): Boolean {
        if (callingUid <= 0) return false
        val appId = callingUid % 100000
        if (resolverAppId > 0) {
            return appId == resolverAppId
        }
        val ctx = context ?: getSystemContext() ?: return false
        val token = Binder.clearCallingIdentity()
        return try {
            val ai = ctx.packageManager.getApplicationInfo("com.android.intentresolver", 0)
            val resolvedAppId = ai.uid % 100000
            resolverAppId = resolvedAppId
            appId == resolvedAppId
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun hookResolveIntent(ipmBaseClass: Class<*>): Boolean {
        return runCatching {
            val resolveIntentMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                m.name == "resolveIntent" &&
                    m.parameterTypes.size == 4 &&
                    m.parameterTypes[0] == Intent::class.java &&
                    m.parameterTypes[1] == String::class.java &&
                    (m.parameterTypes[2] == Long::class.javaPrimitiveType || m.parameterTypes[2] == Int::class.javaPrimitiveType) &&
                    m.parameterTypes[3] == Int::class.javaPrimitiveType
            }
            if (resolveIntentMethod != null) {
                hook(resolveIntentMethod).intercept { chain ->
                    val intent = chain.args[0] as? Intent
                    if (HookPolicy.isEditorSearch(intent)) {
                        if (DEBUG) log(Log.INFO, TAG, "Blocked Query resolveIntent: ${intent?.dataString}")
                        return@intercept null
                    }
                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "resolveIntent(Intent, String, long/int, int) not found in IPackageManagerBase")
                false
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook resolveIntent:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }

    private fun hookQueryIntentActivities(ipmBaseClass: Class<*>, classLoader: ClassLoader): Boolean {
        return runCatching {
            val queryMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                m.name == "queryIntentActivities" &&
                    m.parameterTypes.size == 4 &&
                    m.parameterTypes[0] == Intent::class.java &&
                    m.parameterTypes[1] == String::class.java &&
                    (m.parameterTypes[2] == Long::class.javaPrimitiveType || m.parameterTypes[2] == Int::class.javaPrimitiveType) &&
                    m.parameterTypes[3] == Int::class.javaPrimitiveType
            }
            if (queryMethod != null) {
                val sliceClass = Class.forName("android.content.pm.ParceledListSlice", false, classLoader)
                val sliceConstructor = sliceClass.getConstructor(List::class.java)
                val getListMethod = sliceClass.getMethod("getList")

                hook(queryMethod).intercept { chain ->
                    val intent = chain.args[0] as? Intent

                    // 1. Block fallback search
                    if (HookPolicy.isEditorSearch(intent)) {
                        if (DEBUG) log(Log.INFO, TAG, "Blocked Query queryIntentActivities: ${intent?.dataString}")
                        return@intercept sliceConstructor.newInstance(emptyList<Any>())
                    }

                    val callingUid = Binder.getCallingUid()

                    // 2. Filter & sort PROCESS_TEXT items
                    if (intent != null && Intent.ACTION_PROCESS_TEXT == intent.action) {
                        val isBypass = intent.getBooleanExtra(EXTRA_RAW_QUERY, false) && isModuleCaller(callingUid, getSystemContext())

                        if (isBypass) {
                            return@intercept chain.proceed()
                        }

                        val rawResult = chain.proceed()
                        if (rawResult != null) {
                            return@intercept processTextActivities(rawResult, getListMethod, sliceConstructor)
                        }
                        return@intercept rawResult
                    }

                    // 3. Filter Share Menu & Open With Menu for com.android.intentresolver
                    if (intent != null && isIntentResolverCaller(callingUid, getSystemContext())) {
                        val rawResult = chain.proceed()
                        if (rawResult != null) {
                            return@intercept processResolverActivities(intent, rawResult, getListMethod, sliceConstructor)
                        }
                        return@intercept rawResult
                    }

                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "queryIntentActivities(Intent, String, long/int, int) not found in IPackageManagerBase")
                false
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook queryIntentActivities:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }

    private fun hookQueryIntentActivityOptions(ipmBaseClass: Class<*>, classLoader: ClassLoader): Boolean {
        return runCatching {
            val queryOptionsMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                val params = m.parameterTypes
                m.name == "queryIntentActivityOptions" &&
                    params.size == 7 &&
                    params[3] == Intent::class.java &&
                    (params[5] == Long::class.javaPrimitiveType || params[5] == Int::class.javaPrimitiveType) &&
                    params[6] == Int::class.javaPrimitiveType
            }
            if (queryOptionsMethod != null) {
                val sliceClass = Class.forName("android.content.pm.ParceledListSlice", false, classLoader)
                val sliceConstructor = sliceClass.getConstructor(List::class.java)
                val getListMethod = sliceClass.getMethod("getList")

                hook(queryOptionsMethod).intercept { chain ->
                    val intent = chain.args[3] as? Intent

                    // 1. Block fallback search
                    if (HookPolicy.isEditorSearch(intent)) {
                        if (DEBUG) log(Log.INFO, TAG, "Blocked Query queryIntentActivityOptions: ${intent?.dataString}")
                        return@intercept sliceConstructor.newInstance(emptyList<Any>())
                    }

                    // 2. Filter & sort PROCESS_TEXT items (e.g. Firefox Menu.addIntentOptions)
                    if (intent != null && Intent.ACTION_PROCESS_TEXT == intent.action) {
                        val callingUid = Binder.getCallingUid()
                        val isBypass = intent.getBooleanExtra(EXTRA_RAW_QUERY, false) && isModuleCaller(callingUid, getSystemContext())

                        if (isBypass) {
                            return@intercept chain.proceed()
                        }

                        val rawResult = chain.proceed()
                        if (rawResult != null) {
                            return@intercept processTextActivities(rawResult, getListMethod, sliceConstructor)
                        }
                        return@intercept rawResult
                    }

                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "queryIntentActivityOptions not found in IPackageManagerBase")
                false
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook queryIntentActivityOptions:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }

    private fun processTextActivities(
        rawSlice: Any,
        getListMethod: Method,
        sliceConstructor: Constructor<*>
    ): Any {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val originalList = getListMethod.invoke(rawSlice) as? List<ResolveInfo> ?: return rawSlice
            if (originalList.isEmpty()) return rawSlice

            val rules = cachedRules ?: RuleConfig()
            val blacklist = rules.blacklist
            val customOrder = rules.customOrder

            // Filter out items in blacklist
            val filtered = originalList.filterNot { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@filterNot false
                val pkg = activityInfo.packageName
                val component = ComponentName(pkg, activityInfo.name).flattenToShortString()
                blacklist.contains(component) || blacklist.contains(pkg)
            }

            // Reorder remaining items if custom order is defined
            val resultList = if (customOrder.isNotEmpty()) {
                filtered.sortedWith(compareBy { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo
                    val pkg = activityInfo?.packageName ?: ""
                    val component = if (activityInfo != null) ComponentName(pkg, activityInfo.name).flattenToShortString() else ""

                    val orderIndex = customOrder.indexOf(component).takeIf { it >= 0 }
                        ?: customOrder.indexOf(pkg).takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                    orderIndex
                })
            } else {
                filtered
            }

            // Create fresh ParceledListSlice with filtered & sorted list
            sliceConstructor.newInstance(resultList)
        }.getOrDefault(rawSlice)
    }

    private fun processResolverActivities(
        intent: Intent,
        rawSlice: Any,
        getListMethod: Method,
        sliceConstructor: Constructor<*>
    ): Any {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val originalList = getListMethod.invoke(rawSlice) as? List<ResolveInfo> ?: return rawSlice
            if (originalList.isEmpty()) return rawSlice

            val rules = cachedRules ?: RuleConfig()
            val action = intent.action

            // ACTION_VIEW 不能在这里剔除黑名单：点「总是」时 intentresolver 会把它拿到的候选列表
            // 作为 set 存进 PreferredActivity；之后系统用【未过滤】的候选做 sameSet 校验，
            // 发现多出了被隐藏的应用，就会丢弃该「总是」。VIEW 的隐藏交给 intentresolver 进程内
            // 的 sortTargetsList 在显示层完成。分享（SEND）没有「总是」，仍在此处过滤。
            val blacklist = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.shareBlacklist
                else -> emptySet()
            }

            val customOrder = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.shareCustomOrder
                action == Intent.ACTION_VIEW -> rules.openWithCustomOrder
                else -> emptyList()
            }

            val queryMsg = "Resolver query: action=$action, items=${originalList.size}, blacklist=${blacklist.size}, order=${customOrder.size}"
            Log.i(TAG, queryMsg)
            if (DEBUG) log(Log.INFO, TAG, queryMsg)

            if (blacklist.isEmpty() && customOrder.isEmpty()) return rawSlice

            val filtered = if (blacklist.isNotEmpty()) {
                originalList.filterNot { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo ?: return@filterNot false
                    val pkg = activityInfo.packageName
                    val component = ComponentName(pkg, activityInfo.name).flattenToShortString()
                    blacklist.contains(component) || blacklist.contains(pkg)
                }
            } else {
                originalList
            }

            if (blacklist.isNotEmpty()) {
                val filteredMsg = "Resolver filtered: before=${originalList.size}, after=${filtered.size}"
                Log.i(TAG, filteredMsg)
                if (DEBUG) log(Log.INFO, TAG, filteredMsg)
            }

            val resultList = if (customOrder.isNotEmpty()) {
                filtered.sortedWith(compareBy { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo
                    val pkg = activityInfo?.packageName ?: ""
                    val component = if (activityInfo != null) ComponentName(pkg, activityInfo.name).flattenToShortString() else ""

                    val orderIndex = customOrder.indexOf(component).takeIf { it >= 0 }
                        ?: customOrder.indexOf(pkg).takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                    orderIndex
                })
            } else {
                filtered
            }

            sliceConstructor.newInstance(resultList)
        }.getOrDefault(rawSlice)
    }

    private fun hookGetApplicationInfo(ipmBaseClass: Class<*>): Boolean {
        return runCatching {
            val getApplicationInfoMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                val params = m.parameterTypes
                m.name == "getApplicationInfo" && params.size == 3 &&
                    params[0] == String::class.java &&
                    (params[1] == Long::class.javaPrimitiveType || params[1] == Int::class.javaPrimitiveType) &&
                    params[2] == Int::class.javaPrimitiveType &&
                    m.returnType == ApplicationInfo::class.java
            }
            if (getApplicationInfoMethod == null) {
                log(Log.WARN, TAG, "getApplicationInfo(String, long/int, int) not found in IPackageManagerBase")
                return false
            }

            hook(getApplicationInfoMethod).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0] != VOICE_ASSIST_PACKAGE || cachedRules?.blockAiWriting != true ||
                    Binder.getCallingPid() == Process.myPid()
                ) return@intercept result

                val original = result as? ApplicationInfo ?: return@intercept result
                val metadata = original.metaData ?: return@intercept original
                if (!metadata.getBoolean(AI_EDITOR_WAKE_UP)) return@intercept original

                // MiuixEditor reads this flag before adding the item in the host app.
                // Copy the result so the package manager's own metadata stays intact.
                ApplicationInfo(original).apply {
                    metaData = Bundle(metadata).apply { putBoolean(AI_EDITOR_WAKE_UP, false) }
                }
            }
            true
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook getApplicationInfo:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }


    private fun hookQueryIntentServices(ipmBaseClass: Class<*>, classLoader: ClassLoader): Boolean {
        return runCatching {
            val queryServicesMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                val params = m.parameterTypes
                m.name == "queryIntentServices" &&
                    params.size == 4 &&
                    params[0] == Intent::class.java &&
                    params[1] == String::class.java &&
                    (params[2] == Long::class.javaPrimitiveType || params[2] == Int::class.javaPrimitiveType) &&
                    params[3] == Int::class.javaPrimitiveType
            }
            if (queryServicesMethod != null) {
                val sliceClass = Class.forName("android.content.pm.ParceledListSlice", false, classLoader)
                val sliceConstructor = sliceClass.getConstructor(List::class.java)
                val getListMethod = sliceClass.getMethod("getList")

                hook(queryServicesMethod).intercept { chain ->
                    val intent = chain.args[0] as? Intent
                    if (intent != null && intent.action == "android.service.quicksettings.action.QS_TILE") {
                        val callingUid = Binder.getCallingUid()
                        // HyperSelect app itself needs to see all tiles, so bypass filtering
                        if (isModuleCaller(callingUid, getSystemContext())) {
                            return@intercept chain.proceed()
                        }
                        val config = cachedRules
                        if (config != null && config.tileBlacklist.isNotEmpty()) {
                            val rawResult = chain.proceed()
                            if (rawResult != null) {
                                return@intercept processTileServices(rawResult, config.tileBlacklist, getListMethod, sliceConstructor)
                            }
                        }
                    }
                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "queryIntentServices(Intent, String, long/int, int) not found in IPackageManagerBase")
                false
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook queryIntentServices", it)
        }.getOrDefault(false)
    }

    private fun processTileServices(
        rawSlice: Any,
        blacklist: Set<String>,
        getListMethod: Method,
        sliceConstructor: Constructor<*>
    ): Any {
        @Suppress("UNCHECKED_CAST")
        val rawList = getListMethod.invoke(rawSlice) as? List<ResolveInfo> ?: return rawSlice
        val filtered = rawList.filter { rInfo ->
            val sInfo = rInfo.serviceInfo ?: return@filter true
            val pkg = sInfo.packageName
            val comp = ComponentName(pkg, sInfo.name).flattenToShortString()
            !blacklist.contains(comp) && !blacklist.contains(pkg)
        }
        return sliceConstructor.newInstance(filtered)
    }

    private fun hookSettingsProvider(context: Context): Boolean {
        return runCatching {
            // SettingsProvider runs in system_server, but its class comes from its own APK.
            val providerContext = context.createPackageContext(
                "com.android.providers.settings", Context.CONTEXT_INCLUDE_CODE
            )
            val providerClass = providerContext.classLoader.loadClass("com.android.providers.settings.SettingsProvider")
            val call = providerClass.getDeclaredMethod(
                "call", String::class.java, String::class.java, Bundle::class.java
            )
            hook(call).intercept { chain ->
                val method = chain.args[0] as? String
                if (method == TileSettings.READ || method == TileSettings.ADD ||
                    method == TileSettings.REMOVE || method == TileSettings.RESTORE
                ) {
                    val uid = Binder.getCallingUid()
                    if (UserHandle.getUserHandleForUid(uid) != Process.myUserHandle() ||
                        !isModuleCaller(uid, context)
                    ) return@intercept Bundle()

                    val provider = chain.thisObject as ContentProvider
                    val identity = provider.clearCallingIdentity()
                    try {
                        return@intercept handleTileRequest(context, method, chain.args[1] as? String)
                    } finally {
                        provider.restoreCallingIdentity(identity)
                    }
                }
                chain.proceed()
            }
            log(Log.INFO, TAG, "SettingsProvider hook installed for sysui_qs_tiles")
            true
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook SettingsProvider", it)
        }.getOrDefault(false)
    }

    private fun readQsTiles(context: Context): Result<String?> = runCatching {
        Settings.Secure.getString(context.contentResolver, ConfigProvider.SETTING_QS_TILES)
    }.onFailure {
        log(Log.WARN, TAG, "Failed to read sysui_qs_tiles: ${it.javaClass.simpleName}")
    }

    private fun handleTileRequest(context: Context, method: String, value: String?): Bundle =
        synchronized(tileSettingsLock) {
            val current = readQsTiles(context).getOrElse { return@synchronized Bundle() }
            if (method == TileSettings.READ) {
                if (current == null || !TileSettings.hasTiles(current)) {
                    logMissingTileLayout(method, current)
                    return@synchronized Bundle().apply { putBoolean(TileSettings.MISSING_LAYOUT, true) }
                }
                return@synchronized Bundle().apply { putString(TileSettings.TILES, current) }
            }
            val updated = when (method) {
                TileSettings.RESTORE -> value?.takeIf(TileSettings::hasTiles)
                else -> {
                    if (current == null || !TileSettings.hasTiles(current)) {
                        logMissingTileLayout(method, current)
                        return@synchronized Bundle().apply { putBoolean(TileSettings.MISSING_LAYOUT, true) }
                    }
                    value?.let { updateTileList(current, it, method == TileSettings.ADD) }
                }
            }
            if (updated == null) {
                log(Log.WARN, TAG, "Quick settings $method rejected: invalid request or empty result")
            }
            val written = when (updated) {
                null -> false
                current -> true
                else -> writeQsTiles(context, method, current, updated)
            }
            Bundle().apply { putBoolean(TileSettings.WRITTEN, written) }
        }

    private fun logMissingTileLayout(method: String, current: String?) {
        val state = when {
            current == null -> "unset"
            current.isBlank() -> "empty"
            else -> "no-usable-tiles"
        }
        log(Log.WARN, TAG, "Quick settings $method blocked: layout=$state")
    }

    private fun writeQsTiles(context: Context, method: String, current: String?, updated: String): Boolean {
        val latest = readQsTiles(context)
        if (latest.isFailure || latest.getOrNull() != current) {
            log(Log.WARN, TAG, "Quick settings changed before $method")
            return false
        }
        val written = runCatching {
            Settings.Secure.putString(context.contentResolver, ConfigProvider.SETTING_QS_TILES, updated)
        }.onFailure {
            log(Log.WARN, TAG, "Failed to write sysui_qs_tiles: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
        val verified = written && readQsTiles(context).getOrNull() == updated
        log(
            if (verified) Log.INFO else Log.WARN, TAG,
            "Quick settings $method verified=$verified " +
                "before=${current?.split(',')?.count { it.isNotBlank() } ?: "unset"} " +
                "requested=${updated.split(',').count { it.isNotBlank() }}"
        )
        return verified
    }

    private fun updateTileList(current: String, component: String, add: Boolean): String? {
        val target = ComponentName.unflattenFromString(component) ?: return null
        val tiles = current.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val matches: (String) -> Boolean = { spec ->
            spec.startsWith("custom(") && spec.endsWith(")") &&
                ComponentName.unflattenFromString(spec.substring(7, spec.lastIndex)) == target
        }
        if (add) {
            if (tiles.any(matches)) return current
            val index = if (tiles.lastOrNull() == "edit") tiles.lastIndex else tiles.size
            tiles.add(index, "custom(${target.flattenToShortString()})")
        } else {
            if (!tiles.removeAll(matches)) return current
        }
        return tiles.joinToString(",").takeIf(TileSettings::hasTiles)
    }


    private fun hookGetPackageInfo(ipmBaseClass: Class<*>): Boolean {
        return runCatching {
            var hookedAny = false

            fun handlePackageInfo(
                methodName: String,
                pkgName: String?,
                flags: Long,
                proceed: () -> Any?
            ): Any? {
                val rules = cachedRules ?: RuleConfig()
                if (rules.blockAiWriting && pkgName == "com.miui.notes") {
                    val callingPid = Binder.getCallingPid()
                    val callingUid = Binder.getCallingUid()
                    val myPid = Process.myPid()
                    val context = getSystemContext()

                    if (callingPid != myPid && !isNotesAppCaller(callingUid, context)) {
                        if (flags == 0L) {
                            val original = proceed() as? PackageInfo
                            val result = original?.let { HookPolicy.copyPackageInfo(it) } ?: return original
                            if (rules.debugLogging) {
                                log(Log.INFO, TAG, "[$methodName] Spoofing notes versionCode to 1L for pid=$callingPid, uid=$callingUid, flags=$flags")
                            }
                            result.longVersionCode = 1L
                            return result
                        } else {
                            if (rules.debugLogging) {
                                log(Log.INFO, TAG, "[$methodName] Observed notes query with flags=$flags for pid=$callingPid, uid=$callingUid (not spoofed)")
                            }
                        }
                    }
                }
                return proceed()
            }

            // 1. Hook standard getPackageInfo(String, long/int, int)
            val getPackageInfoMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                m.name == "getPackageInfo" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == String::class.java &&
                    (m.parameterTypes[1] == Long::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.javaPrimitiveType) &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            if (getPackageInfoMethod != null) {
                hook(getPackageInfoMethod).intercept { chain ->
                    val pkgName = chain.args[0] as? String
                    val flags = (chain.args[1] as? Number)?.toLong() ?: 0L
                    handlePackageInfo("getPackageInfo", pkgName, flags) { chain.proceed() }
                }
                hookedAny = true
            } else {
                log(Log.WARN, TAG, "getPackageInfo(String, long/int, int) not found in IPackageManagerBase")
            }

            // 2. Hook getPackageInfoVersioned(VersionedPackage, long/int, int) for Android 14/15/16
            // Verified signature in OS 3 services.jar: (Landroid/content/pm/VersionedPackage;JI)Landroid/content/pm/PackageInfo;
            val getPackageInfoVersionedMethod = ipmBaseClass.declaredMethods.firstOrNull { m ->
                m.name == "getPackageInfoVersioned" &&
                    m.parameterTypes.size == 3 &&
                    (m.parameterTypes[1] == Long::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.javaPrimitiveType) &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            if (getPackageInfoVersionedMethod != null) {
                hook(getPackageInfoVersionedMethod).intercept { chain ->
                    val versionedPkg = chain.args[0]
                    val pkgName = versionedPkg?.let { vp ->
                        runCatching {
                            vp.javaClass.getMethod("getPackageName").invoke(vp) as? String
                        }.getOrNull()
                    }
                    val flags = (chain.args[1] as? Number)?.toLong() ?: 0L
                    handlePackageInfo("getPackageInfoVersioned", pkgName, flags) { chain.proceed() }
                }
                hookedAny = true
            }

            hookedAny
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook getPackageInfo:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }

    @Volatile
    private var latestIntentResolverAction: String? = null

    // 点「总是」时，intentresolver 会把弹窗的候选存成 PreferredActivity 的 set，之后系统拿【未过滤】
    // 的候选做 sameSet 校验。sortTargetsList 把隐藏的应用从列表里删了，存下去的 set 就缺了它们，
    // 「总是」会被系统丢弃。这里记下 VIEW 弹窗里被我们删掉的组件，保存「总是」时补回 set。
    private val removedViewTargets = LinkedHashSet<ComponentName>()
    private var removedViewTouchedAt = 0L
    private val removedViewLock = Any()

    private fun recordRemovedViewTargets(removed: Collection<ComponentName>) {
        if (removed.isEmpty()) return
        synchronized(removedViewLock) {
            val now = SystemClock.uptimeMillis()
            if (now - removedViewTouchedAt > REMOVED_TARGETS_TTL_MS) removedViewTargets.clear()
            removedViewTouchedAt = now
            removedViewTargets.addAll(removed)
        }
    }

    private fun takeRemovedViewTargets(): List<ComponentName> =
        synchronized(removedViewLock) {
            val fresh = SystemClock.uptimeMillis() - removedViewTouchedAt <= REMOVED_TARGETS_TTL_MS
            val result = if (fresh) removedViewTargets.toList() else emptyList()
            removedViewTargets.clear()
            result
        }

    private fun hookIntentResolver(classLoader: ClassLoader) {
        // 1. Hook ResolverListAdapter.processSortedList(List list, boolean b)
        runCatching {
            val adapterClass = Class.forName("com.android.intentresolver.ResolverListAdapter", false, classLoader)
            val pslMethod = adapterClass.declaredMethods.firstOrNull {
                it.name == "processSortedList" && it.parameterTypes.size == 2 && List::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            if (pslMethod != null) {
                var mContextField: Field? = null
                var mTargetIntentField: Field? = null
                var curr: Class<*>? = adapterClass
                while (curr != null && curr != Any::class.java) {
                    if (mContextField == null) {
                        runCatching { mContextField = curr.getDeclaredField("mContext").apply { isAccessible = true } }
                    }
                    if (mTargetIntentField == null) {
                        runCatching { mTargetIntentField = curr.getDeclaredField("mTargetIntent").apply { isAccessible = true } }
                    }
                    curr = curr.superclass
                }

                hook(pslMethod).intercept { chain ->
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? MutableList<Any> ?: return@runCatching
                        val adapter = chain.thisObject
                        val context = mContextField?.get(adapter) as? Context
                        val targetIntent = mTargetIntentField?.get(adapter) as? Intent
                        val action = targetIntent?.action
                        if (!action.isNullOrBlank()) {
                            latestIntentResolverAction = action
                        }
                        sortTargetsList(context, action, list, "processSortedList")
                    }.onFailure {
                        Log.e(TAG, "Failed in processSortedList sort hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked com.android.intentresolver.ResolverListAdapter.processSortedList")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook processSortedList:\n${Log.getStackTraceString(it)}")
        }

        // 2. Hook MiuiChooserGridAdapter$UpdateListTask.onPostExecute(Object)
        runCatching {
            val taskClass = Class.forName("com.android.intentresolver.adapter.MiuiChooserGridAdapter\$UpdateListTask", false, classLoader)
            val postMethod = taskClass.declaredMethods.firstOrNull {
                it.name == "onPostExecute" && it.parameterTypes.size == 1
            }
            if (postMethod != null) {
                hook(postMethod).intercept { chain ->
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? MutableList<Any> ?: return@runCatching
                        val action = latestIntentResolverAction ?: Intent.ACTION_SEND
                        sortTargetsList(null, action, list, "MiuiChooserGrid")
                    }.onFailure {
                        Log.e(TAG, "Failed in MiuiChooserGrid onPostExecute hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked MiuiChooserGridAdapter\$UpdateListTask.onPostExecute")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook MiuiChooserGridAdapter:\n${Log.getStackTraceString(it)}")
        }

        // 3. Hook ChooserListAdapter$2.onPostExecute(Object)
        runCatching {
            val taskClass = Class.forName("com.android.intentresolver.ChooserListAdapter\$2", false, classLoader)
            val postMethod = taskClass.declaredMethods.firstOrNull {
                it.name == "onPostExecute" && it.parameterTypes.size == 1
            }
            if (postMethod != null) {
                hook(postMethod).intercept { chain ->
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? MutableList<Any> ?: return@runCatching
                        val action = latestIntentResolverAction ?: Intent.ACTION_SEND
                        sortTargetsList(null, action, list, "ChooserListAdapter2")
                    }.onFailure {
                        Log.e(TAG, "Failed in ChooserListAdapter\$2 onPostExecute hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked ChooserListAdapter\$2.onPostExecute")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook ChooserListAdapter\$2:\n${Log.getStackTraceString(it)}")
        }

        // 4. Hook ResolverListController.sort(List) fallback
        runCatching {
            val controllerClass = Class.forName("com.android.intentresolver.ResolverListController", false, classLoader)
            val sortMethod = controllerClass.declaredMethods.firstOrNull {
                it.name == "sort" && it.parameterTypes.size == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            if (sortMethod != null) {
                var mContextField: Field? = null
                var mTargetIntentField: Field? = null
                var curr: Class<*>? = controllerClass
                while (curr != null && curr != Any::class.java) {
                    if (mContextField == null) {
                        runCatching { mContextField = curr.getDeclaredField("mContext").apply { isAccessible = true } }
                    }
                    if (mTargetIntentField == null) {
                        runCatching { mTargetIntentField = curr.getDeclaredField("mTargetIntent").apply { isAccessible = true } }
                    }
                    curr = curr.superclass
                }

                hook(sortMethod).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        val targets = chain.args[0] as? MutableList<Any> ?: return@runCatching
                        val controller = chain.thisObject
                        val context = mContextField?.get(controller) as? Context
                        val targetIntent = mTargetIntentField?.get(controller) as? Intent
                        val action = targetIntent?.action
                        if (!action.isNullOrBlank()) {
                            latestIntentResolverAction = action
                        }
                        sortTargetsList(context, action, targets, "ResolverListController.sort")
                    }.onFailure {
                        Log.e(TAG, "Failed in ResolverListController.sort hook: ${it.message}", it)
                    }
                    result
                }
                log(Log.INFO, TAG, "Successfully hooked com.android.intentresolver.ResolverListController.sort")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook ResolverListController:\n${Log.getStackTraceString(it)}")
        }

        // 5. ApplicationPackageManager / PackageManager.addPreferredActivity：保存「总是」时，把被我们隐藏的候选补回 set
        runCatching {
            val apmClass = Class.forName("android.app.ApplicationPackageManager")
            // 查找所有名为 addPreferredActivity 且包含 ComponentName[] 参数的方法
            val addMethods = apmClass.declaredMethods.filter { m ->
                m.name.contains("PreferredActivity") &&
                    m.parameterTypes.any { it.isArray && it.componentType == ComponentName::class.java }
            }

            var hookedCount = 0
            for (addMethod in addMethods) {
                val setParamIndex = addMethod.parameterTypes.indexOfFirst { it.isArray && it.componentType == ComponentName::class.java }
                if (setParamIndex >= 0) {
                    runCatching {
                        hook(addMethod).intercept { chain ->
                            val merged = runCatching {
                                @Suppress("UNCHECKED_CAST")
                                val set = chain.args[setParamIndex] as? Array<ComponentName> ?: return@runCatching null
                                val extra = takeRemovedViewTargets().filter { it !in set }
                                Log.i(TAG, "${addMethod.name}: set=${set.size}, restoring=${extra.map { it.flattenToShortString() }}")
                                if (extra.isEmpty()) null else arrayOf(*set, *extra.toTypedArray())
                            }.getOrNull()

                            if (merged != null) {
                                val newArgs = chain.args.toMutableList()
                                newArgs[setParamIndex] = merged
                                chain.proceed(newArgs.toTypedArray())
                            } else {
                                chain.proceed()
                            }
                        }
                        hookedCount++
                    }.onFailure {
                        log(Log.WARN, TAG, "Failed to hook ${addMethod.name}: ${it.message}")
                    }
                }
            }

            if (hookedCount > 0) {
                log(Log.INFO, TAG, "Successfully hooked ApplicationPackageManager preferredActivity methods ($hookedCount methods: ${addMethods.map { it.name }})")
            } else {
                log(Log.WARN, TAG, "ApplicationPackageManager preferredActivity methods with ComponentName[] not found")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook addPreferredActivity:\n${Log.getStackTraceString(it)}")
        }
    }

    private fun sortTargetsList(
        context: Context?,
        actionHint: String?,
        targets: MutableList<Any>,
        sourceTag: String
    ) {
        if (targets.isEmpty()) return
        val ctx = context ?: runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull() ?: return

        val bundle = runCatching {
            ctx.contentResolver.call(
                Uri.parse("content://io.github.yilink1.hyperselect.provider/rules"),
                ConfigProvider.METHOD_GET_RULES,
                null,
                null
            )
        }.getOrNull()

        val json = bundle?.getString(ConfigProvider.EXTRA_RULES_JSON)
        val config = RuleConfig.fromJson(json)

        val blacklist = when {
            actionHint == Intent.ACTION_VIEW -> config.openWithBlacklist
            else -> config.shareBlacklist
        }

        val removedComponents = ArrayList<ComponentName>()
        if (blacklist.isNotEmpty()) {
            val beforeCount = targets.size
            targets.removeAll { item ->
                val pair = extractTargetInfo(item)
                val comp = pair?.first ?: ""
                val pkg = pair?.second ?: ""
                val hit = blacklist.contains(comp) || blacklist.contains(pkg)
                if (hit) {
                    val cn = ComponentName.unflattenFromString(comp)
                    if (cn != null) removedComponents.add(cn)
                }
                hit
            }
            if (targets.size != beforeCount) {
                Log.i(TAG, "IntentResolver blacklist filtered [$sourceTag]: before=$beforeCount, after=${targets.size}")
            }
        }
        // 记录打开方式（VIEW）弹窗中被隐藏的组件，用于在点「总是」时补回 set（用后即清）
        if (actionHint == Intent.ACTION_VIEW) recordRemovedViewTargets(removedComponents)

        val customOrder = when {
            actionHint == Intent.ACTION_VIEW -> config.openWithCustomOrder
            else -> config.shareCustomOrder
        }

        if (customOrder.isNotEmpty()) {
            val orderMap = HashMap<String, Int>(customOrder.size)
            for (i in customOrder.indices) {
                orderMap[customOrder[i]] = i
            }

            val rankMap = java.util.IdentityHashMap<Any, Int>(targets.size)
            for (item in targets) {
                val pair = extractTargetInfo(item)
                val comp = pair?.first ?: ""
                val pkg = pair?.second ?: ""
                val rank = orderMap[comp] ?: orderMap[pkg] ?: Int.MAX_VALUE
                rankMap[item] = rank
            }

            targets.sortWith(compareBy { rankMap[it] ?: Int.MAX_VALUE })
            val top3 = targets.take(3).mapNotNull { extractTargetInfo(it)?.first }
            Log.i(TAG, "IntentResolver sort applied [$sourceTag]: action=$actionHint, customOrderSize=${customOrder.size}, targetsCount=${targets.size}, top3=$top3")
            if (DEBUG) log(Log.INFO, TAG, "IntentResolver sort applied [$sourceTag]: action=$actionHint, targetsCount=${targets.size}")
        }
    }

    @Volatile
    private var cachedComponentNameField: Field? = null

    private fun extractTargetInfo(item: Any): Pair<String, String>? {
        if (item is ResolveInfo) {
            val ai = item.activityInfo ?: return null
            return Pair(ComponentName(ai.packageName, ai.name).flattenToShortString(), ai.packageName)
        }
        runCatching {
            val comp = item.javaClass.getMethod("getResolvedComponentName").invoke(item) as? ComponentName
            if (comp != null) return Pair(comp.flattenToShortString(), comp.packageName)
        }
        cachedComponentNameField?.let { field ->
            runCatching {
                val comp = field.get(item) as? ComponentName
                if (comp != null) return Pair(comp.flattenToShortString(), comp.packageName)
            }
        }
        runCatching {
            val comp = item.javaClass.getMethod("getComponentName").invoke(item) as? ComponentName
            if (comp != null) return Pair(comp.flattenToShortString(), comp.packageName)
        }
        runCatching {
            val ri = item.javaClass.getMethod("getResolveInfo").invoke(item) as? ResolveInfo
            val ai = ri?.activityInfo
            if (ai != null) return Pair(ComponentName(ai.packageName, ai.name).flattenToShortString(), ai.packageName)
        }
        runCatching {
            var clazz: Class<*>? = item.javaClass
            while (clazz != null && clazz != Any::class.java) {
                for (f in clazz.declaredFields) {
                    if (f.name == "name" && ComponentName::class.java.isAssignableFrom(f.type)) {
                        f.isAccessible = true
                        cachedComponentNameField = f
                        val comp = f.get(item) as? ComponentName
                        if (comp != null) return Pair(comp.flattenToShortString(), comp.packageName)
                    }
                }
                clazz = clazz.superclass
            }
        }
        return null
    }

}
