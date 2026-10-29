package io.github.yilink1.hyperselect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
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
        const val EXTRA_RAW_QUERY = "io.github.yilink1.hyperselect.EXTRA_RAW_QUERY"

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
        var getPackageHooked = false

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

            // 3. Hook getPackageInfo -> Globally block AiRecognition across all apps
            getPackageHooked = hookGetPackageInfo(ipmBaseClass)
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook system_server IPackageManagerBase on Android $sdkInt:\n${Log.getStackTraceString(it)}")
        }

        // Diagnostic log: printed once on system_server startup
        log(
            Log.INFO,
            TAG,
            "HyperSelect initialized on Android $sdkInt | Hooks: [resolveIntent=$resolveHooked, queryIntent=$queryHooked, getPackageInfo=$getPackageHooked]"
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

    /** True when the text-selection menu should be modified at all. */
    private fun textMenuEnabled(): Boolean {
        val r = cachedRules ?: return true
        return r.moduleEnabled && r.textEnabled
    }

    // Called only on the worker, after system services are ready.
    private fun refreshRules(): Boolean {
        val token = Binder.clearCallingIdentity()
        return try {
            val context = getSystemContext() ?: return false
            if (!observerRegistered) {
                context.contentResolver.registerContentObserver(
                    ConfigProvider.CONTENT_URI, true,
                    object : ContentObserver(workerHandler) {
                        override fun onChange(selfChange: Boolean) { scheduleRefresh() }
                    }
                )
                observerRegistered = true
            }
            val bundle = context.contentResolver.call(
                ConfigProvider.CONTENT_URI,
                ConfigProvider.METHOD_GET_RULES,
                null,
                null
            )
            val json = bundle?.getString(ConfigProvider.EXTRA_RULES_JSON)
            val config = RuleConfig.parseResponse(json) ?: return false
            cachedRules = config
            runCatching {
                val ai = context.packageManager.getApplicationInfo("com.android.intentresolver", 0)
                resolverAppId = ai.uid % 100000
            }
            if (DEBUG) log(Log.INFO, TAG, "Rules updated async: blacklist=${config.blacklist.size}, order=${config.customorder.size}, share=${config.shareBlacklist.size}, openWith=${config.openWithBlacklist.size}")
            true
        } catch (t: Throwable) {
            if (DEBUG) log(Log.WARN, TAG, "Failed to refresh rules async: ${t.message}")
            false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
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
                    if (textMenuEnabled() && HookPolicy.isEditorSearch(intent)) {
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
                    if (textMenuEnabled() && HookPolicy.isEditorSearch(intent)) {
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
            if (!rules.moduleEnabled || !rules.textEnabled) return rawSlice
            val blacklist = rules.blacklist
            val customorder = rules.customorder

            // Filter out items in blacklist
            val filtered = originalList.filterNot { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@filterNot false
                val pkg = activityInfo.packageName
                val component = ComponentName(pkg, activityInfo.name).flattenToShortString()
                blacklist.contains(component) || blacklist.contains(pkg)
            }

            // Reorder remaining items if custom order is defined
            val resultList = if (customorder.isNotEmpty()) {
                filtered.sortedWith(compareBy { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo
                    val pkg = activityInfo?.packageName ?: ""
                    val component = if (activityInfo != null) ComponentName(pkg, activityInfo.name).flattenToShortString() else ""

                    val orderIndex = customorder.indexOf(component).takeIf { it >= 0 }
                        ?: customorder.indexOf(pkg).takeIf { it >= 0 }
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
            if (!rules.moduleEnabled) return rawSlice
            val action = intent.action

            val isShare = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
            val isView = action == Intent.ACTION_VIEW
            if (isShare && !rules.shareEnabled) return rawSlice
            if (isView && !rules.openWithEnabled) return rawSlice

            val blacklist = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.shareBlacklist
                action == Intent.ACTION_VIEW -> rules.openWithBlacklist
                else -> emptySet()
            }

            val customorder = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.shareCustomorder
                action == Intent.ACTION_VIEW -> rules.openWithCustomorder
                else -> emptyList()
            }

            val queryMsg = "Resolver query: action=$action, items=${originalList.size}, blacklist=${blacklist.size}, order=${customorder.size}"
            Log.i(TAG, queryMsg)
            if (DEBUG) log(Log.INFO, TAG, queryMsg)

            if (blacklist.isEmpty() && customorder.isEmpty()) return rawSlice

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

            val resultList = if (customorder.isNotEmpty()) {
                filtered.sortedWith(compareBy { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo
                    val pkg = activityInfo?.packageName ?: ""
                    val component = if (activityInfo != null) ComponentName(pkg, activityInfo.name).flattenToShortString() else ""

                    val orderIndex = customorder.indexOf(component).takeIf { it >= 0 }
                        ?: customorder.indexOf(pkg).takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                    orderIndex
                })
            } else {
                filtered
            }

            sliceConstructor.newInstance(resultList)
        }.getOrDefault(rawSlice)
    }

    private fun hookGetPackageInfo(ipmBaseClass: Class<*>): Boolean {
        return runCatching {
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

                    // Fast path: Only intercept Notes package info when flags == 0 and blockAiWriting is enabled
                    val rules = cachedRules ?: RuleConfig()
                    if (rules.moduleEnabled && rules.textEnabled && rules.blockAiWriting && pkgName == "com.miui.notes" && flags == 0L) {
                        val callingPid = Binder.getCallingPid()
                        val callingUid = Binder.getCallingUid()
                        val myPid = Process.myPid()
                        val context = getSystemContext()
                        // Don't spoof for system_server, and don't spoof for Notes itself
                        if (callingPid != myPid && !isNotesAppCaller(callingUid, context)) {
                            val original = chain.proceed() as? PackageInfo
                            // Binder marshals after the hook returns. Clone first so even ROMs
                            // returning cached PackageInfo objects keep their original version.
                            val result = original?.let { HookPolicy.copyPackageInfo(it) }
                                ?: return@intercept original
                            if (DEBUG) log(Log.INFO, TAG, "Spoofing notes versionCode for pid=$callingPid, uid=$callingUid")
                            result.longVersionCode = 1L
                            return@intercept result
                        }
                    }
                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "getPackageInfo(String, long/int, int) not found in IPackageManagerBase")
                false
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to hook getPackageInfo:\n${Log.getStackTraceString(it)}")
        }.getOrDefault(false)
    }

    @Volatile
    private var latestIntentResolverAction: String? = null

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

        if (!config.moduleEnabled) return
        val isViewHint = actionHint == Intent.ACTION_VIEW
        if (isViewHint && !config.openWithEnabled) return
        if (!isViewHint && !config.shareEnabled) return

        val blacklist = when {
            actionHint == Intent.ACTION_VIEW -> config.openWithBlacklist
            else -> config.shareBlacklist
        }

        if (blacklist.isNotEmpty()) {
            val beforeCount = targets.size
            targets.removeAll { item ->
                val pair = extractTargetInfo(item)
                val comp = pair?.first ?: ""
                val pkg = pair?.second ?: ""
                blacklist.contains(comp) || blacklist.contains(pkg)
            }
            if (targets.size != beforeCount) {
                Log.i(TAG, "IntentResolver blacklist filtered [$sourceTag]: before=$beforeCount, after=${targets.size}")
            }
        }

        val customorder = when {
            actionHint == Intent.ACTION_VIEW -> config.openWithCustomorder
            else -> config.shareCustomorder
        }

        if (customorder.isNotEmpty()) {
            val orderMap = HashMap<String, Int>(customorder.size)
            for (i in customorder.indices) {
                orderMap[customorder[i]] = i
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
            Log.i(TAG, "IntentResolver sort applied [$sourceTag]: action=$actionHint, customorderSize=${customorder.size}, targetsCount=${targets.size}, top3=$top3")
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
