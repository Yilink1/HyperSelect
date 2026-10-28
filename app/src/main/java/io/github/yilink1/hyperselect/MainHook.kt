package io.github.yilink1.hyperselect

import android.content.Component Name
import android.content.Context
import android.content.Intent
import android.content.pm.Package Info
import android.content.pm.Resolve Info
import android.database.Content Observer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Handler Thread
import android.os.Process
import android.os.System Clock
import android.util.Log
import io.github.libxposed.api.Xposed Module
import io.github.libxposed.api.Xposed Module Interface.Module Loaded Param
import io.github.libxposed.api.Xposed Module Interface.Package Ready Param
import io.github.libxposed.api.Xposed Module Interface.System Server Starting Param
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class Main Hook : Xposed Module() {

    companion object {
        private const val TAG = "Hyper Select"
        private const val DEBUG = false
        const val EXTRA_RAW_QUERY = "io.github.yilink1.hyperselect.EXTRA_RAW_QUERY"

        @Volatile
        private var cached Rules: Rule Config? = null

        @Volatile
        private var observer Registered = false

        @Volatile
        private var module Uid = -1

        @Volatile
        private var resolver App Id = -1

        private val worker Thread = Handler Thread("Hyper Select-Worker").apply {
            set Uncaught Exception Handler { _, throwable ->
                Log.e(TAG, "Caught unhandled exception in Hyper Select-Worker:\n${Log.get Stack Trace String(throwable)}")
            }
            start()
        }
        private val worker Handler = Handler(worker Thread.looper)
    }

    @Volatile private var services Ready = false
    private val refresh Lock = Any()
    private var refresh At = Long.MAX_VALUE
    private var retry Delay Ms = 1_000L
    private val refresh Task = Runnable {
        synchronized(refresh Lock) { refresh At = Long.MAX_VALUE }
        if (!services Ready) {
            // Fallback for ROMs without the expected boot-phase method.
            services Ready = run Catching {
                Class.for Name("android.os.System Properties")
                    .get Method("get", String::class.java)
                    .invoke(null, "sys.boot_completed") == "1"
            }.get Or Default(false)
        }
        if (!services Ready) {
            schedule Refresh(1_000L)
        } else if (refresh Rules()) {
            retry Delay Ms = 1_000L
        } else {
            schedule Refresh(retry Delay Ms)
            retry Delay Ms = (retry Delay Ms * 2).coerce At Most(60_000L)
        }
    }

    private fun schedule Refresh(delay Ms: Long = 0L) {
        synchronized(refresh Lock) {
            val at = System Clock.uptime Millis() + delay Ms
            if (refresh At <= at) return
            worker Handler.remove Callbacks(refresh Task)
            refresh At = at
            worker Handler.post At Time(refresh Task, at)
        }
    }

    override fun on Module Loaded(param: Module Loaded Param) {
        if (DEBUG) {
            log(Log.INFO, TAG, "on Module Loaded: process=${param.process Name}, is System Server=${param.is System Server}")
        }
    }

    override fun on Package Ready(param: Package Ready Param) {
        if (param.package Name == "com.android.intentresolver") {
            hook Intent Resolver(param.class Loader)
        }
    }

    override fun on System Server Starting(param: System Server Starting Param) {
        val class Loader = param.class Loader
        val sdk Int = Build.VERSION.SDK_INT
        var resolve Hooked = false
        var query Hooked = false
        var get Package Hooked = false

        // PHASE_THIRD_PARTY_APPS_CAN_START: application providers may now be started.
        run Catching {
            Class.for Name("com.android.server.System Service Manager", false, class Loader)
                .declared Methods.filter {
                    it.name == "start Boot Phase" && it.parameter Types.last Or Null() == Int::class.java Primitive Type
                }.for Each { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if ((chain.args.last() as Int) >= 600 && !services Ready) {
                            services Ready = true
                            schedule Refresh()
                        }
                        result
                    }
                }
        }.on Failure { log(Log.WARN, TAG, "Boot-phase hook unavailable; using boot-completed fallback") }
        schedule Refresh(1_000L)

        run Catching {
            val ipm Base Class = Class.for Name("com.android.server.pm.IPackage Manager Base", false, class Loader)

            // 1. Hook resolve Intent -> Block Query search fallback
            resolve Hooked = hook Resolve Intent(ipm Base Class)

            // 2. Hook query Intent Activities -> Block search fallback & manage PROCESS_TEXT items
            query Hooked = hook Query Intent Activities(ipm Base Class, class Loader)

            // 3. Hook get Package Info -> Globally block Ai Recognition across all apps
            get Package Hooked = hook Get Package Info(ipm Base Class)
        }.on Failure {
            log(Log.ERROR, TAG, "Failed to hook system_server IPackage Manager Base on Android $sdk Int:\n${Log.get Stack Trace String(it)}")
        }

        // Diagnostic log: printed once on system_server startup
        log(
            Log.INFO,
            TAG,
            "Hyper Select initialized on Android $sdk Int | Hooks: [resolve Intent=$resolve Hooked, query Intent=$query Hooked, get Package Info=$get Package Hooked]"
        )
    }

    private fun get System Context(): Context? {
        return run Catching {
            val activity Thread Class = Class.for Name("android.app.Activity Thread")
            val current Activity Thread Method = activity Thread Class.get Declared Method("current Activity Thread")
            val activity Thread = current Activity Thread Method.invoke(null)
            val get System Context Method = activity Thread Class.get Declared Method("get System Context")
            get System Context Method.invoke(activity Thread) as? Context
        }.get Or Null()
    }

    // Called only on the worker, after system services are ready.
    private fun refresh Rules(): Boolean {
        val token = Binder.clear Calling Identity()
        return try {
            val context = get System Context() ?: return false
            if (!observer Registered) {
                context.content Resolver.register Content Observer(
                    Config Provider.CONTENT_URI, true,
                    object : Content Observer(worker Handler) {
                        override fun on Change(self Change: Boolean) { schedule Refresh() }
                    }
                )
                observer Registered = true
            }
            val bundle = context.content Resolver.call(
                Config Provider.CONTENT_URI,
                Config Provider.METHOD_GET_RULES,
                null,
                null
            )
            val json = bundle?.get String(Config Provider.EXTRA_RULES_JSON)
            val config = Rule Config.parse Response(json) ?: return false
            cached Rules = config
            run Catching {
                val ai = context.package Manager.get Application Info("com.android.intentresolver", 0)
                resolver App Id = ai.uid % 100000
            }
            if (DEBUG) log(Log.INFO, TAG, "Rules updated async: blacklist=${config.blacklist.size}, order=${config.customorder.size}, share=${config.share Blacklist.size}, open With=${config.open With Blacklist.size}")
            true
        } catch (t: Throwable) {
            if (DEBUG) log(Log.WARN, TAG, "Failed to refresh rules async: ${t.message}")
            false
        } finally {
            Binder.restore Calling Identity(token)
        }
    }

    private fun is Module Caller(calling Uid: Int, context: Context?): Boolean {
        if (calling Uid <= 0) return false
        if (module Uid > 0 && calling Uid == module Uid) return true
        val ctx = context ?: get System Context() ?: return false
        val token = Binder.clear Calling Identity()
        return try {
            val pkg Name = ctx.package Manager.get Name For Uid(calling Uid)
            if (pkg Name == "io.github.yilink1.hyperselect") {
                module Uid = calling Uid
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restore Calling Identity(token)
        }
    }

    private fun is Notes App Caller(calling Uid: Int, context: Context?): Boolean {
        if (calling Uid <= 0) return false
        val ctx = context ?: get System Context() ?: return false
        val token = Binder.clear Calling Identity()
        return try {
            ctx.package Manager.get Packages For Uid(calling Uid)?.contains("com.miui.notes") == true
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restore Calling Identity(token)
        }
    }

    private fun is Intent Resolver Caller(calling Uid: Int, context: Context?): Boolean {
        if (calling Uid <= 0) return false
        val app Id = calling Uid % 100000
        if (resolver App Id > 0) {
            return app Id == resolver App Id
        }
        val ctx = context ?: get System Context() ?: return false
        val token = Binder.clear Calling Identity()
        return try {
            val ai = ctx.package Manager.get Application Info("com.android.intentresolver", 0)
            val resolved App Id = ai.uid % 100000
            resolver App Id = resolved App Id
            app Id == resolved App Id
        } catch (_: Throwable) {
            false
        } finally {
            Binder.restore Calling Identity(token)
        }
    }

    private fun hook Resolve Intent(ipm Base Class: Class<*>): Boolean {
        return run Catching {
            val resolve Intent Method = ipm Base Class.declared Methods.first Or Null { m ->
                m.name == "resolve Intent" &&
                    m.parameter Types.size == 4 &&
                    m.parameter Types[0] == Intent::class.java &&
                    m.parameter Types[1] == String::class.java &&
                    (m.parameter Types[2] == Long::class.java Primitive Type || m.parameter Types[2] == Int::class.java Primitive Type) &&
                    m.parameter Types[3] == Int::class.java Primitive Type
            }
            if (resolve Intent Method != null) {
                hook(resolve Intent Method).intercept { chain ->
                    val intent = chain.args[0] as? Intent
                    if (Hook Policy.is Editor Search(intent)) {
                        if (DEBUG) log(Log.INFO, TAG, "Blocked Query resolve Intent: ${intent?.data String}")
                        return@intercept null
                    }
                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "resolve Intent(Intent, String, long/int, int) not found in IPackage Manager Base")
                false
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook resolve Intent:\n${Log.get Stack Trace String(it)}")
        }.get Or Default(false)
    }

    private fun hook Query Intent Activities(ipm Base Class: Class<*>, class Loader: Class Loader): Boolean {
        return run Catching {
            val query Method = ipm Base Class.declared Methods.first Or Null { m ->
                m.name == "query Intent Activities" &&
                    m.parameter Types.size == 4 &&
                    m.parameter Types[0] == Intent::class.java &&
                    m.parameter Types[1] == String::class.java &&
                    (m.parameter Types[2] == Long::class.java Primitive Type || m.parameter Types[2] == Int::class.java Primitive Type) &&
                    m.parameter Types[3] == Int::class.java Primitive Type
            }
            if (query Method != null) {
                val slice Class = Class.for Name("android.content.pm.Parceled List Slice", false, class Loader)
                val slice Constructor = slice Class.get Constructor(List::class.java)
                val get List Method = slice Class.get Method("get List")

                hook(query Method).intercept { chain ->
                    val intent = chain.args[0] as? Intent

                    // 1. Block fallback search
                    if (Hook Policy.is Editor Search(intent)) {
                        if (DEBUG) log(Log.INFO, TAG, "Blocked Query query Intent Activities: ${intent?.data String}")
                        return@intercept slice Constructor.new Instance(empty List<Any>())
                    }

                    val calling Uid = Binder.get Calling Uid()

                    // 2. Filter & sort PROCESS_TEXT items
                    if (intent != null && Intent.ACTION_PROCESS_TEXT == intent.action) {
                        val is Bypass = intent.get Boolean Extra(EXTRA_RAW_QUERY, false) && is Module Caller(calling Uid, get System Context())

                        if (is Bypass) {
                            return@intercept chain.proceed()
                        }

                        val raw Result = chain.proceed()
                        if (raw Result != null) {
                            return@intercept process Text Activities(raw Result, get List Method, slice Constructor)
                        }
                        return@intercept raw Result
                    }

                    // 3. Filter Share Menu & Open With Menu for com.android.intentresolver
                    if (intent != null && is Intent Resolver Caller(calling Uid, get System Context())) {
                        val raw Result = chain.proceed()
                        if (raw Result != null) {
                            return@intercept process Resolver Activities(intent, raw Result, get List Method, slice Constructor)
                        }
                        return@intercept raw Result
                    }

                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "query Intent Activities(Intent, String, long/int, int) not found in IPackage Manager Base")
                false
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook query Intent Activities:\n${Log.get Stack Trace String(it)}")
        }.get Or Default(false)
    }

    private fun process Text Activities(
        raw Slice: Any,
        get List Method: Method,
        slice Constructor: Constructor<*>
    ): Any {
        return run Catching {
            @Suppress("UNCHECKED_CAST")
            val original List = get List Method.invoke(raw Slice) as? List<Resolve Info> ?: return raw Slice
            if (original List.is Empty()) return raw Slice

            val rules = cached Rules ?: Rule Config()
            val blacklist = rules.blacklist
            val customorder = rules.customorder

            // Filter out items in blacklist
            val filtered = original List.filter Not { resolve Info ->
                val activity Info = resolve Info.activity Info ?: return@filter Not false
                val pkg = activity Info.package Name
                val component = Component Name(pkg, activity Info.name).flatten To Short String()
                blacklist.contains(component) || blacklist.contains(pkg)
            }

            // Reorder remaining items if custom order is defined
            val result List = if (customorder.is Not Empty()) {
                filtered.sorted With(compare By { resolve Info ->
                    val activity Info = resolve Info.activity Info
                    val pkg = activity Info?.package Name ?: ""
                    val component = if (activity Info != null) Component Name(pkg, activity Info.name).flatten To Short String() else ""

                    val order Index = customorder.index Of(component).take If { it >= 0 }
                        ?: customorder.index Of(pkg).take If { it >= 0 }
                        ?: Int.MAX_VALUE
                    order Index
                })
            } else {
                filtered
            }

            // Create fresh Parceled List Slice with filtered & sorted list
            slice Constructor.new Instance(result List)
        }.get Or Default(raw Slice)
    }

    private fun process Resolver Activities(
        intent: Intent,
        raw Slice: Any,
        get List Method: Method,
        slice Constructor: Constructor<*>
    ): Any {
        return run Catching {
            @Suppress("UNCHECKED_CAST")
            val original List = get List Method.invoke(raw Slice) as? List<Resolve Info> ?: return raw Slice
            if (original List.is Empty()) return raw Slice

            val rules = cached Rules ?: Rule Config()
            val action = intent.action

            val blacklist = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.share Blacklist
                action == Intent.ACTION_VIEW -> rules.open With Blacklist
                else -> empty Set()
            }

            val customorder = when {
                action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE -> rules.share Customorder
                action == Intent.ACTION_VIEW -> rules.open With Customorder
                else -> empty List()
            }

            val query Msg = "Resolver query: action=$action, items=${original List.size}, blacklist=${blacklist.size}, order=${customorder.size}"
            Log.i(TAG, query Msg)
            if (DEBUG) log(Log.INFO, TAG, query Msg)

            if (blacklist.is Empty() && customorder.is Empty()) return raw Slice

            val filtered = if (blacklist.is Not Empty()) {
                original List.filter Not { resolve Info ->
                    val activity Info = resolve Info.activity Info ?: return@filter Not false
                    val pkg = activity Info.package Name
                    val component = Component Name(pkg, activity Info.name).flatten To Short String()
                    blacklist.contains(component) || blacklist.contains(pkg)
                }
            } else {
                original List
            }

            if (blacklist.is Not Empty()) {
                val filtered Msg = "Resolver filtered: before=${original List.size}, after=${filtered.size}"
                Log.i(TAG, filtered Msg)
                if (DEBUG) log(Log.INFO, TAG, filtered Msg)
            }

            val result List = if (customorder.is Not Empty()) {
                filtered.sorted With(compare By { resolve Info ->
                    val activity Info = resolve Info.activity Info
                    val pkg = activity Info?.package Name ?: ""
                    val component = if (activity Info != null) Component Name(pkg, activity Info.name).flatten To Short String() else ""

                    val order Index = customorder.index Of(component).take If { it >= 0 }
                        ?: customorder.index Of(pkg).take If { it >= 0 }
                        ?: Int.MAX_VALUE
                    order Index
                })
            } else {
                filtered
            }

            slice Constructor.new Instance(result List)
        }.get Or Default(raw Slice)
    }

    private fun hook Get Package Info(ipm Base Class: Class<*>): Boolean {
        return run Catching {
            val get Package Info Method = ipm Base Class.declared Methods.first Or Null { m ->
                m.name == "get Package Info" &&
                    m.parameter Types.size == 3 &&
                    m.parameter Types[0] == String::class.java &&
                    (m.parameter Types[1] == Long::class.java Primitive Type || m.parameter Types[1] == Int::class.java Primitive Type) &&
                    m.parameter Types[2] == Int::class.java Primitive Type
            }
            if (get Package Info Method != null) {
                hook(get Package Info Method).intercept { chain ->
                    val pkg Name = chain.args[0] as? String
                    val flags = (chain.args[1] as? Number)?.to Long() ?: 0L

                    // Fast path: Only intercept Notes package info when flags == 0 and block Ai Writing is enabled
                    val rules = cached Rules ?: Rule Config()
                    if (rules.block Ai Writing && pkg Name == "com.miui.notes" && flags == 0L) {
                        val calling Pid = Binder.get Calling Pid()
                        val calling Uid = Binder.get Calling Uid()
                        val my Pid = Process.my Pid()
                        val context = get System Context()
                        // Don't spoof for system_server, and don't spoof for Notes itself
                        if (calling Pid != my Pid && !is Notes App Caller(calling Uid, context)) {
                            val original = chain.proceed() as? Package Info
                            // Binder marshals after the hook returns. Clone first so even ROMs
                            // returning cached Package Info objects keep their original version.
                            val result = original?.let { Hook Policy.copy Package Info(it) }
                                ?: return@intercept original
                            if (DEBUG) log(Log.INFO, TAG, "Spoofing notes version Code for pid=$calling Pid, uid=$calling Uid")
                            result.long Version Code = 1L
                            return@intercept result
                        }
                    }
                    chain.proceed()
                }
                true
            } else {
                log(Log.WARN, TAG, "get Package Info(String, long/int, int) not found in IPackage Manager Base")
                false
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook get Package Info:\n${Log.get Stack Trace String(it)}")
        }.get Or Default(false)
    }

    @Volatile
    private var latest Intent Resolver Action: String? = null

    private fun hook Intent Resolver(class Loader: Class Loader) {
        // 1. Hook Resolver List Adapter.process Sorted List(List list, boolean b)
        run Catching {
            val adapter Class = Class.for Name("com.android.intentresolver.Resolver List Adapter", false, class Loader)
            val psl Method = adapter Class.declared Methods.first Or Null {
                it.name == "process Sorted List" && it.parameter Types.size == 2 && List::class.java.is Assignable From(it.parameter Types[0])
            }
            if (psl Method != null) {
                var m Context Field: Field? = null
                var m Target Intent Field: Field? = null
                var curr: Class<*>? = adapter Class
                while (curr != null && curr != Any::class.java) {
                    if (m Context Field == null) {
                        run Catching { m Context Field = curr.get Declared Field("m Context").apply { is Accessible = true } }
                    }
                    if (m Target Intent Field == null) {
                        run Catching { m Target Intent Field = curr.get Declared Field("m Target Intent").apply { is Accessible = true } }
                    }
                    curr = curr.superclass
                }

                hook(psl Method).intercept { chain ->
                    run Catching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? Mutable List<Any> ?: return@run Catching
                        val adapter = chain.this Object
                        val context = m Context Field?.get(adapter) as? Context
                        val target Intent = m Target Intent Field?.get(adapter) as? Intent
                        val action = target Intent?.action
                        if (!action.is Null Or Blank()) {
                            latest Intent Resolver Action = action
                        }
                        sort Targets List(context, action, list, "process Sorted List")
                    }.on Failure {
                        Log.e(TAG, "Failed in process Sorted List sort hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked com.android.intentresolver.Resolver List Adapter.process Sorted List")
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook process Sorted List:\n${Log.get Stack Trace String(it)}")
        }

        // 2. Hook Miui Chooser Grid Adapter$Update List Task.on Post Execute(Object)
        run Catching {
            val task Class = Class.for Name("com.android.intentresolver.adapter.Miui Chooser Grid Adapter\$Update List Task", false, class Loader)
            val post Method = task Class.declared Methods.first Or Null {
                it.name == "on Post Execute" && it.parameter Types.size == 1
            }
            if (post Method != null) {
                hook(post Method).intercept { chain ->
                    run Catching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? Mutable List<Any> ?: return@run Catching
                        val action = latest Intent Resolver Action ?: Intent.ACTION_SEND
                        sort Targets List(null, action, list, "Miui Chooser Grid")
                    }.on Failure {
                        Log.e(TAG, "Failed in Miui Chooser Grid on Post Execute hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked Miui Chooser Grid Adapter\$Update List Task.on Post Execute")
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook Miui Chooser Grid Adapter:\n${Log.get Stack Trace String(it)}")
        }

        // 3. Hook Chooser List Adapter$2.on Post Execute(Object)
        run Catching {
            val task Class = Class.for Name("com.android.intentresolver.Chooser List Adapter\$2", false, class Loader)
            val post Method = task Class.declared Methods.first Or Null {
                it.name == "on Post Execute" && it.parameter Types.size == 1
            }
            if (post Method != null) {
                hook(post Method).intercept { chain ->
                    run Catching {
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? Mutable List<Any> ?: return@run Catching
                        val action = latest Intent Resolver Action ?: Intent.ACTION_SEND
                        sort Targets List(null, action, list, "Chooser List Adapter2")
                    }.on Failure {
                        Log.e(TAG, "Failed in Chooser List Adapter\$2 on Post Execute hook: ${it.message}", it)
                    }
                    chain.proceed()
                }
                log(Log.INFO, TAG, "Successfully hooked Chooser List Adapter\$2.on Post Execute")
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook Chooser List Adapter\$2:\n${Log.get Stack Trace String(it)}")
        }

        // 4. Hook Resolver List Controller.sort(List) fallback
        run Catching {
            val controller Class = Class.for Name("com.android.intentresolver.Resolver List Controller", false, class Loader)
            val sort Method = controller Class.declared Methods.first Or Null {
                it.name == "sort" && it.parameter Types.size == 1 && List::class.java.is Assignable From(it.parameter Types[0])
            }
            if (sort Method != null) {
                var m Context Field: Field? = null
                var m Target Intent Field: Field? = null
                var curr: Class<*>? = controller Class
                while (curr != null && curr != Any::class.java) {
                    if (m Context Field == null) {
                        run Catching { m Context Field = curr.get Declared Field("m Context").apply { is Accessible = true } }
                    }
                    if (m Target Intent Field == null) {
                        run Catching { m Target Intent Field = curr.get Declared Field("m Target Intent").apply { is Accessible = true } }
                    }
                    curr = curr.superclass
                }

                hook(sort Method).intercept { chain ->
                    val result = chain.proceed()
                    run Catching {
                        @Suppress("UNCHECKED_CAST")
                        val targets = chain.args[0] as? Mutable List<Any> ?: return@run Catching
                        val controller = chain.this Object
                        val context = m Context Field?.get(controller) as? Context
                        val target Intent = m Target Intent Field?.get(controller) as? Intent
                        val action = target Intent?.action
                        if (!action.is Null Or Blank()) {
                            latest Intent Resolver Action = action
                        }
                        sort Targets List(context, action, targets, "Resolver List Controller.sort")
                    }.on Failure {
                        Log.e(TAG, "Failed in Resolver List Controller.sort hook: ${it.message}", it)
                    }
                    result
                }
                log(Log.INFO, TAG, "Successfully hooked com.android.intentresolver.Resolver List Controller.sort")
            }
        }.on Failure {
            log(Log.WARN, TAG, "Failed to hook Resolver List Controller:\n${Log.get Stack Trace String(it)}")
        }
    }

    private fun sort Targets List(
        context: Context?,
        action Hint: String?,
        targets: Mutable List<Any>,
        source Tag: String
    ) {
        if (targets.is Empty()) return
        val ctx = context ?: run Catching {
            Class.for Name("android.app.Activity Thread")
                .get Method("current Application")
                .invoke(null) as? Context
        }.get Or Null() ?: return

        val bundle = run Catching {
            ctx.content Resolver.call(
                Uri.parse("content://io.github.yilink1.hyperselect.provider/rules"),
                Config Provider.METHOD_GET_RULES,
                null,
                null
            )
        }.get Or Null()

        val json = bundle?.get String(Config Provider.EXTRA_RULES_JSON)
        val config = Rule Config.from Json(json)

        val blacklist = when {
            action Hint == Intent.ACTION_VIEW -> config.open With Blacklist
            else -> config.share Blacklist
        }

        if (blacklist.is Not Empty()) {
            val before Count = targets.size
            targets.remove All { item ->
                val pair = extract Target Info(item)
                val comp = pair?.first ?: ""
                val pkg = pair?.second ?: ""
                blacklist.contains(comp) || blacklist.contains(pkg)
            }
            if (targets.size != before Count) {
                Log.i(TAG, "Intent Resolver blacklist filtered [$source Tag]: before=$before Count, after=${targets.size}")
            }
        }

        val customorder = when {
            action Hint == Intent.ACTION_VIEW -> config.open With Customorder
            else -> config.share Customorder
        }

        if (customorder.is Not Empty()) {
            val order Map = Hash Map<String, Int>(customorder.size)
            for (i in customorder.indices) {
                order Map[customorder[i]] = i
            }

            val rank Map = java.util.Identity Hash Map<Any, Int>(targets.size)
            for (item in targets) {
                val pair = extract Target Info(item)
                val comp = pair?.first ?: ""
                val pkg = pair?.second ?: ""
                val rank = order Map[comp] ?: order Map[pkg] ?: Int.MAX_VALUE
                rank Map[item] = rank
            }

            targets.sort With(compare By { rank Map[it] ?: Int.MAX_VALUE })
            val top3 = targets.take(3).map Not Null { extract Target Info(it)?.first }
            Log.i(TAG, "Intent Resolver sort applied [$source Tag]: action=$action Hint, customorder Size=${customorder.size}, targets Count=${targets.size}, top3=$top3")
            if (DEBUG) log(Log.INFO, TAG, "Intent Resolver sort applied [$source Tag]: action=$action Hint, targets Count=${targets.size}")
        }
    }

    @Volatile
    private var cached Component Name Field: Field? = null

    private fun extract Target Info(item: Any): Pair<String, String>? {
        if (item is Resolve Info) {
            val ai = item.activity Info ?: return null
            return Pair(Component Name(ai.package Name, ai.name).flatten To Short String(), ai.package Name)
        }
        run Catching {
            val comp = item.java Class.get Method("get Resolved Component Name").invoke(item) as? Component Name
            if (comp != null) return Pair(comp.flatten To Short String(), comp.package Name)
        }
        cached Component Name Field?.let { field ->
            run Catching {
                val comp = field.get(item) as? Component Name
                if (comp != null) return Pair(comp.flatten To Short String(), comp.package Name)
            }
        }
        run Catching {
            val comp = item.java Class.get Method("get Component Name").invoke(item) as? Component Name
            if (comp != null) return Pair(comp.flatten To Short String(), comp.package Name)
        }
        run Catching {
            val ri = item.java Class.get Method("get Resolve Info").invoke(item) as? Resolve Info
            val ai = ri?.activity Info
            if (ai != null) return Pair(Component Name(ai.package Name, ai.name).flatten To Short String(), ai.package Name)
        }
        run Catching {
            var clazz: Class<*>? = item.java Class
            while (clazz != null && clazz != Any::class.java) {
                for (f in clazz.declared Fields) {
                    if (f.name == "name" && Component Name::class.java.is Assignable From(f.type)) {
                        f.is Accessible = true
                        cached Component Name Field = f
                        val comp = f.get(item) as? Component Name
                        if (comp != null) return Pair(comp.flatten To Short String(), comp.package Name)
                    }
                }
                clazz = clazz.superclass
            }
        }
        return null
    }

}
