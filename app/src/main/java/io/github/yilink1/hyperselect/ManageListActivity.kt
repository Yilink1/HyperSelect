package io.github.yilink1.hyperselect

import android.animation.Animator
import android.animation.Animator Listener Adapter
import android.animation.Keyframe
import android.animation.Property Values Holder
import android.animation.Value Animator
import android.content.pm.Package Manager
import android.content.pm.Resolve Info
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect F
import android.graphics.Typeface
import android.os.Bundle
import android.os.System Clock
import android.text.Editable
import android.text.Text Watcher
import android.view.Haptic Feedback Constants
import android.view.Motion Event
import android.view.View
import android.view.View Group
import android.view.animation.Decelerate Interpolator
import android.view.animation.Overshoot Interpolator
import android.view.inputmethod.Input Method Manager
import android.widget.Edit Text
import android.widget.Frame Layout
import android.widget.Linear Layout
import android.widget.Progress Bar
import android.widget.Text View
import android.widget.Toast
import androidx.activity.On Back Pressed Callback
import androidx.appcompat.app.App Compat Activity
import androidx.core.content.Context Compat
import androidx.core.view.View Compat
import androidx.core.view.Window Insets Compat
import androidx.recyclerview.widget.Default Item Animator
import androidx.recyclerview.widget.Item Touch Helper
import androidx.recyclerview.widget.Linear Layout Manager
import androidx.recyclerview.widget.Recycler View
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Text Selection / System Share / Open with Shared UI and interactions for the three management pages。
 *
 * Interaction model (top dual-nav tabs + tactile cards + smart search):
 *  - Top dual-tab bar (Show N/Hide N): clearly shows status, quick tab switch ；
 *  -  Physical card：Showpage Dragorlong presscardtopbottom Sort，Swipe leftto hide；
 *  - Hidepage：cardright 「+」Restorebutton， Restore Showlist；
 *  - Top Search ：Search bottom 「Pin」、「hide」or「Restore」Actioncapsule；
 *  -  have Takes effect immediately， have Undo。
 */
abstract class Manage List Activity : App Compat Activity() {

    // ───────── Subclass hooks ─────────
    protected abstract val screen Title: String
    protected abstract val empty Text: String

    /**  have Customorderwhen， Sort（Text Selection System Backorder）。 */
    protected open val sort By Label When Noorder: Boolean = true

    /**  after 。 */
    protected abstract fun query Targets(pm: Package Manager): List<Resolve Info>
    protected abstract fun blacklist Of(config: Rule Config): Set<String>
    protected abstract fun order Of(config: Rule Config): List<String>
    protected abstract fun with Rules(config: Rule Config, blacklist: Set<String>, order: List<String>): Rule Config

    protected open fun test Actions(): List<Pair<String, () -> Unit>> = empty List()
    protected open fun on Create Extra(container: View Group) {}
    protected open fun on Config Loaded(config: Rule Config) {}
    protected open fun apply Extra(config: Rule Config): Rule Config = config

    // ───────── Status ─────────
    private companion object {
        const val PREFS = "hyper_select_prefs"
        const val KEY_DRAG_HINT_DONE = "drag_hint_done"
        const val KEY_SWIPE_HINT_SHOWN = "has_shown_swipe_hint"
        const val TAB_VISIBLE = 0
        const val TAB_HIDDEN = 1
    }

    /** Yes No Enable Pin  + By system Tabs（System Shareand Open with Enable）。 */
    protected open val enable Pinned Section: Boolean = false

    private val visible = Array List<Text Action Item>()
    private val hidden = Array List<Text Action Item>()
    private var pinned Count = 0
    private var tab = TAB_VISIBLE

    private var search Open = false
    private var query = ""
    private var loaded = false
    private var drag Dirty = false

    private lateinit var header Bar: View
    private lateinit var search Box: View
    private lateinit var et Search: Edit Text
    private lateinit var layout Extra: Frame Layout
    private lateinit var segment: View
    private lateinit var tv Tab Visible: Text View
    private lateinit var tv Tab Hidden: Text View
    private lateinit var tv Hint: Text View
    private lateinit var recycler View: Recycler View
    private lateinit var progress Bar: Progress Bar
    private lateinit var tv Empty: Text View
    private lateinit var undo Bar: Linear Layout
    private lateinit var tv Undo Msg: Text View

    private val adapter = Manage Adapter(
        on Restore = { item -> restore Item(item) },
        on Pin = { item -> pin Item(item) },
        on Unpin = { item -> unpin Item(item) },
        on Stash = { item -> stash Item(item) },
        on Start Drag = { holder -> item Touch Helper.start Drag(holder) }
    )

    private lateinit var item Touch Helper: Item Touch Helper
    private val executor = Executors.new Single Thread Executor()

    private var undo Snapshot: Triple<List<Text Action Item>, List<Text Action Item>, Int>? = null
    private val hide Undo Runnable = Runnable { hide Undo() }

    private val search Back Callback = object : On Back Pressed Callback(false) {
        override fun handle On Back Pressed() {
            close Search()
        }
    }

    // Swipe gesture background and hint animation drawing
    private val bg Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { text Align = Paint.Align.CENTER }
    private val pill Rect = Rect F()
    private var hint Target View: View? = null
    private var hint Offset Dx = 0f
    private var swipe Hint Animator: Value Animator? = null
    private var is Hint Scheduled = false

    // ───────── Lifecycle ─────────
    override fun on Create(saved Instance State: Bundle?) {
        super.on Create(saved Instance State)
        set Content View(R.layout.activity_manage)

        View Compat.set On Apply Window Insets Listener(find View By Id(android.R.id.content)) { view, insets ->
            val system Bars = insets.get Insets(Window Insets Compat.Type.system Bars())
            view.set Padding(0, system Bars.top, 0, system Bars.bottom)
            insets
        }

        text Paint.text Size = 13f * resources.display Metrics.density
        text Paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        header Bar = find View By Id(R.id.header Bar)
        search Box = find View By Id(R.id.search Box)
        et Search = find View By Id(R.id.et Search)
        layout Extra = find View By Id(R.id.layout Extra)
        segment = find View By Id(R.id.segment)
        tv Tab Visible = find View By Id(R.id.tv Tab Visible)
        tv Tab Hidden = find View By Id(R.id.tv Tab Hidden)
        tv Hint = find View By Id(R.id.tv Hint)
        recycler View = find View By Id(R.id.recycler View)
        progress Bar = find View By Id(R.id.progress Bar)
        tv Empty = find View By Id(R.id.tv Empty)
        undo Bar = find View By Id(R.id.undo Bar)
        tv Undo Msg = find View By Id(R.id.tv Undo Msg)

        find View By Id<Text View>(R.id.tv Title).text = screen Title
        find View By Id<View>(R.id.btn Back).set On Click Listener { finish() }
        find View By Id<View>(R.id.btn Search).set On Click Listener { open Search() }
        find View By Id<View>(R.id.btn Close Search).set On Click Listener { close Search() }
        find View By Id<View>(R.id.btn More).set On Click Listener { show Menu(it) }
        find View By Id<View>(R.id.btn Undo).set On Click Listener {
            val snapshot = undo Snapshot ?: return@set On Click Listener
            restore(snapshot)
            hide Undo()
        }

        tv Tab Visible.set On Click Listener { select Tab(TAB_VISIBLE) }
        tv Tab Hidden.set On Click Listener { select Tab(TAB_HIDDEN) }
        on Back Pressed Dispatcher.add Callback(this, search Back Callback)

        et Search.add Text Changed Listener(object : Text Watcher {
            override fun before Text Changed(s: Char Sequence?, start: Int, count: Int, after: Int) {}
            override fun on Text Changed(s: Char Sequence?, start: Int, before: Int, count: Int) {
                query = s?.to String().or Empty().trim().lowercase(Locale.get Default())
                if (search Open) refresh Rows()
            }
            override fun after Text Changed(s: Editable?) {}
        })

        setup List()
        on Create Extra(layout Extra)
        load()
    }

    override fun on Resume() {
        super.on Resume()
        maybe Play Swipe Hint()
    }

    override fun on Destroy() {
        super.on Destroy()
        swipe Hint Animator?.cancel()
        swipe Hint Animator = null
        undo Bar.remove Callbacks(hide Undo Runnable)
        executor.shutdown()
    }

    // ───────── Data loading / saving ─────────
    private fun load() {
        progress Bar.visibility = View.VISIBLE
        tv Empty.visibility = View.GONE

        executor.execute {
            val pm = package Manager
            val infos = run Catching { query Targets(pm) }.get Or Default(empty List())
            val config = Rule Store.load(this)
            val blacklist = blacklist Of(config)
            val order = order Of(config)

            val seen = Hash Set<String>()
            val all = Array List<Text Action Item>()
            for (info in infos) {
                val activity Info = info.activity Info ?: continue
                val pkg = activity Info.package Name
                if (pkg == package Name) continue

                val component = android.content.Component Name(pkg, activity Info.name).flatten To Short String()
                if (!seen.add(component)) continue

                val label = info.load Label(pm)?.to String()?.take If { it.is Not Blank() }
                    ?: activity Info.load Label(pm)?.to String()
                    ?: activity Info.name
                val icon = info.load Icon(pm) ?: activity Info.load Icon(pm)
                val enabled = !blacklist.contains(component) && !blacklist.contains(pkg)
                all.add(Text Action Item(label, pkg, component, icon, enabled))
            }

            val shown = Array List<Text Action Item>()
            val hidden List = Array List<Text Action Item>()
            for (item in all) if (item.is Enabled) shown.add(item) else hidden List.add(item)

            // Legacy migration fallback: older versions wrote all scanned apps to order.
            //  Pin have ，  order  （  10  ）， old  dump，Reset  0 Pin，Restore By system 。
            val is Legacy Full Dump = enable Pinned Section && order.size > 10 && order.size >= (shown.size * 0.5)
            val effectiveorder = if (is Legacy Full Dump) empty List() else order

            val order Index = Hash Map<String, Int>()
            effectiveorder.for Each Indexed { index, key -> if (!order Index.contains Key(key)) order Index[key] = index }

            var initial Pinned Count = 0
            if (enable Pinned Section) {
                val pinned List = Array List<Text Action Item>()
                val flowing List = Array List<Text Action Item>()
                for (item in shown) {
                    if (order Index.contains Key(item.component Name) || order Index.contains Key(item.package Name)) {
                        pinned List.add(item)
                    } else {
                        flowing List.add(item)
                    }
                }
                pinned List.sort With(compare By { item ->
                    order Index[item.component Name] ?: order Index[item.package Name] ?: Int.MAX_VALUE
                })
                flowing List.sort By { it.label }
                shown.clear()
                shown.add All(pinned List)
                shown.add All(flowing List)
                initial Pinned Count = pinned List.size
            } else {
                if (order.is Not Empty()) {
                    shown.sort With(compare By { item ->
                        order Index[item.component Name] ?: order Index[item.package Name] ?: Int.MAX_VALUE
                    })
                } else if (sort By Label When Noorder) {
                    shown.sort By { it.label }
                }
            }

            run On Ui Thread {
                if (is Finishing || is Destroyed) return@run On Ui Thread
                visible.clear()
                visible.add All(shown)
                pinned Count = initial Pinned Count
                hidden.clear()
                hidden.add All(hidden List)
                loaded = true
                progress Bar.visibility = View.GONE
                on Config Loaded(config)
                refresh Rows()
                maybe Play Swipe Hint()
            }
        }
    }

    /**  before「Show / Hide / order」， System 。 */
    protected fun save Rules() {
        if (!loaded) return
        val new Blacklist = Hash Set<String>()
        val neworder = Array List<String>()

        if (enable Pinned Section) {
            val limit = pinned Count.coerce At Most(visible.size)
            for (i in 0 until limit) {
                neworder.add(visible[i].component Name)
            }
        } else {
            for (item in visible) neworder.add(item.component Name)
        }

        for (item in hidden) {
            new Blacklist.add(item.component Name)
        }
        val config = apply Extra(with Rules(Rule Store.load(this), new Blacklist, neworder))
        Rule Store.save(this, config)
        content Resolver.notify Change(Config Provider.CONTENT_URI, null)
    }

    // ───────── List building & refresh ─────────
    private fun matches(item: Text Action Item): Boolean {
        if (query.is Empty()) return true
        return item.label.lowercase(Locale.get Default()).contains(query) ||
            item.package Name.lowercase(Locale.get Default()).contains(query)
    }

    private fun build Rows(): List<Manage Row> {
        if (search Open) {
            val pinned Set = if (enable Pinned Section) {
                visible.take(pinned Count).map { it.component Name }.to Set()
            } else empty Set()
            val filtered = (visible + hidden).filter { matches(it) }
            return filtered.map { Manage Row.Search(it, is Pinned = pinned Set.contains(it.component Name)) }
        }
        if (tab == TAB_HIDDEN) {
            return hidden.map { Manage Row.Hidden(it) }
        }
        // tab == TAB_VISIBLE
        if (enable Pinned Section) {
            val rows = Array List<Manage Row>()
            val actual Pinned = pinned Count.coerce At Most(visible.size)
            val flowing Count = visible.size - actual Pinned

            if (actual Pinned > 0) {
                rows.add(Manage Row.Header("Pin App", actual Pinned, "Always pinned at top, long press handle to reorder"))
                for (i in 0 until actual Pinned) {
                    rows.add(Manage Row.Pinned(visible[i]))
                }
            }

            if (flowing Count > 0) {
                val flowing Hint = if (actual Pinned > 0) "Sorted by system usage habits" else "Sorted by system usage habits，Tap「Pin」canpin totop"
                rows.add(Manage Row.Header("  apps", flowing Count, flowing Hint))
                for (i in actual Pinned until visible.size) {
                    rows.add(Manage Row.Flowing(visible[i]))
                }
            }
            return rows
        } else {
            return visible.map { Manage Row.Showing(it) }
        }
    }

    private fun refresh Rows() {
        val rows = build Rows()
        adapter.submit(rows)
        update Chrome(rows)
    }

    private fun update Chrome(rows: List<Manage Row>) {
        tv Tab Visible.text = "Show ${visible.size}"
        tv Tab Hidden.text = "Hide ${hidden.size}"
        style Tab(tv Tab Visible, tab == TAB_VISIBLE)
        style Tab(tv Tab Hidden, tab == TAB_HIDDEN)
        segment.visibility = if (search Open) View.GONE else View.VISIBLE

        val drag Hint Done = get Shared Preferences(PREFS, MODE_PRIVATE).get Boolean(KEY_DRAG_HINT_DONE, false)
        tv Hint.visibility =
            if (loaded && !search Open && tab == TAB_VISIBLE && !drag Hint Done && visible.size > 1 && !enable Pinned Section) View.VISIBLE
            else View.GONE

        if (loaded && rows.is Empty()) {
            tv Empty.text = when {
                visible.is Empty() && hidden.is Empty() -> empty Text
                search Open -> " have of the apps"
                tab == TAB_VISIBLE -> " hide Hide"
                else -> " have hideof the apps"
            }
            tv Empty.visibility = View.VISIBLE
        } else {
            tv Empty.visibility = View.GONE
        }
    }

    private fun style Tab(tv: Text View, selected: Boolean) {
        tv.set Background Resource(if (selected) R.drawable.bg_segment_thumb else 0)
        tv.set Text Color(
            Context Compat.get Color(this, if (selected) R.color.hyper_text_primary else R.color.hyper_text_secondary)
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun select Tab(new Tab: Int) {
        if (tab == new Tab) return
        tab = new Tab
        refresh Rows()
        recycler View.scroll To Position(0)
    }

    // ───────── Search ─────────
    private fun open Search() {
        if (search Open) return
        search Open = true
        search Back Callback.is Enabled = true
        header Bar.visibility = View.GONE
        search Box.alpha = 0f
        search Box.visibility = View.VISIBLE
        search Box.animate().alpha(1f).set Duration(140L).start()
        query = ""
        refresh Rows()
        et Search.request Focus()
        et Search.post {
            get System Service(Input Method Manager::class.java)?.show Soft Input(et Search, Input Method Manager.SHOW_IMPLICIT)
        }
    }

    private fun close Search() {
        if (!search Open) return
        search Open = false
        search Back Callback.is Enabled = false
        et Search.set Text("")
        get System Service(Input Method Manager::class.java)?.hide Soft Input From Window(et Search.window Token, 0)
        search Box.visibility = View.GONE
        header Bar.visibility = View.VISIBLE
        refresh Rows()
    }

    // ───────── card Action（Pin / hide / Restore） ─────────
    private fun snapshot(): Triple<List<Text Action Item>, List<Text Action Item>, Int> =
        Triple(visible.map { it.copy() }, hidden.map { it.copy() }, pinned Count)

    private fun pin Item(item: Text Action Item) {
        val before = snapshot()
        if (enable Pinned Section) {
            val index = visible.index Of First { it.component Name == item.component Name }
            if (index >= 0) {
                if (index >= pinned Count) {
                    val moved = visible.remove At(index)
                    visible.add(pinned Count, moved)
                    pinned Count++
                }
            } else {
                hidden.remove All { it.component Name == item.component Name }
                item.is Enabled = true
                visible.add(pinned Count, item)
                pinned Count++
            }
        } else {
            visible.remove All { it.component Name == item.component Name }
            hidden.remove All { it.component Name == item.component Name }
            item.is Enabled = true
            visible.add(0, item)
        }
        save Rules()
        refresh Rows()
        show Undo(" Pin「${item.label}」", before)
        if (!search Open && tab == TAB_VISIBLE) {
            recycler View.scroll To Position(0)
        }
    }

    private fun unpin Item(item: Text Action Item) {
        val before = snapshot()
        if (enable Pinned Section) {
            val index = visible.index Of First { it.component Name == item.component Name }
            if (index in 0 until pinned Count) {
                val moved = visible.remove At(index)
                pinned Count--
                visible.add(pinned Count, moved)
                save Rules()
                refresh Rows()
                show Undo(" Cancel Pin「${item.label}」", before)
            }
        }
    }

    private fun stash Item(item: Text Action Item) {
        val before = snapshot()
        val index = visible.index Of First { it.component Name == item.component Name }
        if (index >= 0) {
            visible.remove At(index)
            if (enable Pinned Section && index < pinned Count) {
                pinned Count = (pinned Count - 1).coerce At Least(0)
            }
        }
        hidden.remove All { it.component Name == item.component Name }
        item.is Enabled = false
        hidden.add(0, item)
        save Rules()
        refresh Rows()
        show Undo(" hide「${item.label}」", before)
    }

    private fun restore Item(item: Text Action Item) {
        val before = snapshot()
        hidden.remove All { it.component Name == item.component Name }
        visible.remove All { it.component Name == item.component Name }
        item.is Enabled = true
        if (enable Pinned Section) {
            visible.add(pinned Count, item)
        } else {
            visible.add(0, item)
        }
        save Rules()
        refresh Rows()
        show Undo(" Restore「${item.label}」", before)
    }

    private fun bulk(enable: Boolean) {
        val targets: List<Text Action Item> = when {
            search Open -> (visible + hidden).filter { matches(it) && it.is Enabled != enable }
            enable -> Array List(hidden)
            else -> Array List(visible)
        }
        if (targets.is Empty()) {
            Toast.make Text(this, if (enable) " have Restoreof the apps" else " have hideof the apps", Toast.LENGTH_SHORT).show()
            return
        }
        val before = snapshot()
        for (item in targets.as Reversed()) {
            if (enable) {
                hidden.remove All { it.component Name == item.component Name }
                item.is Enabled = true
                if (enable Pinned Section) {
                    visible.add(pinned Count, item)
                } else {
                    visible.add(0, item)
                }
            } else {
                val idx = visible.index Of First { it.component Name == item.component Name }
                if (idx >= 0) {
                    visible.remove At(idx)
                    if (enable Pinned Section && idx < pinned Count) {
                        pinned Count = (pinned Count - 1).coerce At Least(0)
                    }
                }
                item.is Enabled = false
                hidden.add(0, item)
            }
        }
        if (!enable && enable Pinned Section) {
            pinned Count = 0
        }
        save Rules()
        refresh Rows()
        show Undo(if (enable) " Restore ${targets.size}  " else " hide ${targets.size}  ", before)
    }

    private fun restore(snapshot: Triple<List<Text Action Item>, List<Text Action Item>, Int>) {
        visible.clear()
        visible.add All(snapshot.first.map { it.copy() })
        hidden.clear()
        hidden.add All(snapshot.second.map { it.copy() })
        pinned Count = snapshot.third
        save Rules()
        refresh Rows()
        Toast.make Text(this, " Undo ", Toast.LENGTH_SHORT).show()
    }

    private fun reset Pinnedorder() {
        if (pinned Count <= 1) return
        val before = snapshot()
        val pinned Sublist = Array List(visible.sub List(0, pinned Count))
        pinned Sublist.sort By { it.label }
        for (i in 0 until pinned Count) {
            visible[i] = pinned Sublist[i]
        }
        save Rules()
        refresh Rows()
        show Undo(" Reset Pin Sort", before)
    }

    private fun clear All Pins() {
        if (pinned Count == 0) return
        val before = snapshot()
        pinned Count = 0
        save Rules()
        refresh Rows()
        show Undo(" have Pin", before)
    }

    private fun resetorder() {
        val config = Rule Store.load(this)
        Rule Store.save(this, with Rules(config, blacklist Of(config), empty List()))
        content Resolver.notify Change(Config Provider.CONTENT_URI, null)
        load()
        Toast.make Text(this, " Restore Sort", Toast.LENGTH_SHORT).show()
    }

    // ───────── Undo  ─────────
    private fun show Undo(message: String, before: Triple<List<Text Action Item>, List<Text Action Item>, Int>) {
        undo Snapshot = before
        tv Undo Msg.text = message
        undo Bar.remove Callbacks(hide Undo Runnable)
        undo Bar.animate().cancel()
        undo Bar.visibility = View.VISIBLE
        undo Bar.alpha = 0f
        undo Bar.translation Y = dp(12).to Float()
        undo Bar.animate().alpha(1f).translation Y(0f).set Duration(180L).start()
        undo Bar.post Delayed(hide Undo Runnable, 4000L)
    }

    private fun hide Undo() {
        undo Snapshot = null
        undo Bar.remove Callbacks(hide Undo Runnable)
        undo Bar.animate().cancel()
        undo Bar.animate()
            .alpha(0f)
            .translation Y(dp(12).to Float())
            .set Duration(160L)
            .with End Action { undo Bar.visibility = View.GONE }
            .start()
    }

    // ───────── Menu / Instructions ─────────
    private fun show Menu(anchor: View) {
        val items = Array List<Pair<String, () -> Unit>>()
        if (search Open) {
            items.add(Pair("Restore Search ", { bulk(true) }))
            items.add(Pair("hide Search ", { bulk(false) }))
        } else if (tab == TAB_VISIBLE) {
            if (enable Pinned Section) {
                if (pinned Count > 1) {
                    items.add(Pair("Reset Pin Sort", { reset Pinnedorder() }))
                }
                if (pinned Count > 0) {
                    items.add(Pair(" have Pin", { clear All Pins() }))
                }
            } else {
                items.add(Pair("Restore Sort", { resetorder() }))
            }
            items.add(Pair(" hide", { bulk(false) }))
            items.add(Pair(" Restore", { bulk(true) }))
        } else {
            items.add(Pair(" Restore", { bulk(true) }))
            items.add(Pair(" hide", { bulk(false) }))
        }
        items.add All(test Actions())
        items.add(Pair("Usage Instructions", { show Help() }))
        show Popup Menu(anchor, items)
    }

    private fun show Help() {
        val items = if (enable Pinned Section) {
            list Of(
                Help Feature Item(
                    R.drawable.ic_help_gesture,
                    "Swipe left to hide",
                    " Hide ， Systempanelin Show"
                ),
                Help Feature Item(
                    R.drawable.ic_help_pin,
                    "Tap「Pin」 ",
                    " before； Pin apps Continue By system Sort"
                ),
                Help Feature Item(
                    R.drawable.ic_help_drag,
                    "Draghandle ",
                    "Long pressright handle，adjust Pin appsof theorderorder"
                ),
                Help Feature Item(
                    R.drawable.ic_add,
                    "Restore",
                    " 「Hide」Tabs，Tap can Restore"
                )
            )
        } else {
            list Of(
                Help Feature Item(
                    R.drawable.ic_help_gesture,
                    "Swipe left to hide",
                    "Hide Unnecessaryof the Feature ，Streamlinelong press menu"
                ),
                Help Feature Item(
                    R.drawable.ic_help_drag,
                    "Drag the handle to sort",
                    "Long pressright handle，adjust Menu of theorderorder"
                ),
                Help Feature Item(
                    R.drawable.ic_help_sync,
                    "Takes effect immediately",
                    " Syncs in real time System，support Undo"
                )
            )
        }
        show Feature Help Sheet("$screen Title · Instructions", items)
    }

    // ───────── list （Drag Sort + left hide） ─────────
    private fun setup List() {
        recycler View.apply {
            layout Manager = Linear Layout Manager(this@Manage List Activity)
            adapter = this@Manage List Activity.adapter
            item Animator = Default Item Animator().apply {
                supports Change Animations = false
                remove Duration = 100L
                move Duration = 150L
                add Duration = 100L
            }
        }

        // first of theleft Demo 
        recycler View.add Item Decoration(object : Recycler View.Item Decoration() {
            override fun on Draw(c: Canvas, parent: Recycler View, state: Recycler View.State) {
                val target = hint Target View
                if (target != null && hint Offset Dx < 0f) {
                    draw Swipe Background(c, target, hint Offset Dx, false, 0.88f)
                }
            }
        })

        recycler View.add On Item Touch Listener(object : Recycler View.Simple On Item Touch Listener() {
            override fun on Intercept Touch Event(rv: Recycler View, e: Motion Event): Boolean {
                if (hint Target View != null) swipe Hint Animator?.cancel()
                return false
            }
        })

        item Touch Helper = Item Touch Helper(object : Item Touch Helper.Simple Callback(0, 0) {
            private var is Threshold Crossed = false
            private var current Scale = 0.85f
            private var is Swiping Out = false
            private var swipe Start Time = 0L

            override fun get Movement Flags(rv: Recycler View, view Holder: Recycler View.View Holder): Int {
                if (is Swiping Out) {
                    if (System Clock.uptime Millis() - swipe Start Time > 260L) is Swiping Out = false
                    else return make Movement Flags(0, 0)
                }
                //  Search 「Show」Tabswhen， Drag Sortandleft hide
                if (search Open || tab != TAB_VISIBLE) return make Movement Flags(0, 0)

                val pos = view Holder.binding Adapter Position
                val row = adapter.get Item(pos) ?: return make Movement Flags(0, 0)

                return if (enable Pinned Section) {
                    when (row) {
                        is Manage Row.Pinned -> make Movement Flags(Item Touch Helper.UP or Item Touch Helper.DOWN, Item Touch Helper.LEFT)
                        is Manage Row.Flowing -> make Movement Flags(0, Item Touch Helper.LEFT)
                        else -> make Movement Flags(0, 0)
                    }
                } else {
                    if (pos !in visible.indices) make Movement Flags(0, 0)
                    else make Movement Flags(Item Touch Helper.UP or Item Touch Helper.DOWN, Item Touch Helper.LEFT)
                }
            }

            override fun can Drop Over(
                rv: Recycler View,
                current: Recycler View.View Holder,
                target: Recycler View.View Holder
            ): Boolean {
                val to Pos = target.binding Adapter Position
                val to Row = adapter.get Item(to Pos)
                return if (enable Pinned Section) {
                    to Row is Manage Row.Pinned
                } else {
                    to Pos in visible.indices
                }
            }

            override fun on Move(
                rv: Recycler View,
                view Holder: Recycler View.View Holder,
                target: Recycler View.View Holder
            ): Boolean {
                if (search Open || tab != TAB_VISIBLE) return false
                val from Pos = view Holder.binding Adapter Position
                val to Pos = target.binding Adapter Position

                if (enable Pinned Section) {
                    val from Row = adapter.get Item(from Pos)
                    val to Row = adapter.get Item(to Pos)
                    if (from Row !is Manage Row.Pinned || to Row !is Manage Row.Pinned) return false
                    val from Index = from Pos - 1
                    val to Index = to Pos - 1
                    if (from Index !in 0 until pinned Count || to Index !in 0 until pinned Count) return false

                    val moved = visible.remove At(from Index)
                    visible.add(to Index, moved)
                    adapter.notify Item Moved(from Pos, to Pos)
                    drag Dirty = true
                    view Holder.item View.perform Haptic Feedback(Haptic Feedback Constants.CLOCK_TICK)
                    return true
                } else {
                    if (from Pos !in visible.indices || to Pos !in visible.indices) return false
                    val moved = visible.remove At(from Pos)
                    visible.add(to Pos, moved)
                    adapter.notify Item Moved(from Pos, to Pos)
                    drag Dirty = true
                    view Holder.item View.perform Haptic Feedback(Haptic Feedback Constants.CLOCK_TICK)
                    return true
                }
            }

            override fun on Selected Changed(view Holder: Recycler View.View Holder?, action State: Int) {
                super.on Selected Changed(view Holder, action State)
                if (action State == Item Touch Helper.ACTION_STATE_DRAG && view Holder != null) {
                    view Holder.item View.perform Haptic Feedback(Haptic Feedback Constants.LONG_PRESS)
                    view Holder.item View.set Background Resource(R.drawable.bg_row_lift)
                    view Holder.item View.animate()
                        .scale X(1.025f)
                        .scale Y(1.025f)
                        .translation Z(dp(14).to Float())
                        .set Duration(140L)
                        .start()
                }
            }

            override fun get Swipe Threshold(view Holder: Recycler View.View Holder): Float = 0.22f
            override fun get Swipe Escape Velocity(default Value: Float): Float = default Value * 0.45f
            override fun get Swipe Velocity Threshold(default Value: Float): Float = default Value * 0.5f

            override fun get Animation Duration(
                rv: Recycler View,
                animation Type: Int,
                animate Dx: Float,
                animate Dy: Float
            ): Long {
                if (animation Type == Item Touch Helper.ANIMATION_TYPE_SWIPE_SUCCESS) {
                    is Swiping Out = true
                    swipe Start Time = System Clock.uptime Millis()
                }
                return if (animation Type == Item Touch Helper.ANIMATION_TYPE_SWIPE_CANCEL) 150L else 100L
            }

            override fun on Swiped(view Holder: Recycler View.View Holder, direction: Int) {
                if (direction == Item Touch Helper.LEFT) {
                    val position = view Holder.binding Adapter Position
                    val row = adapter.get Item(position)
                    val item = when (row) {
                        is Manage Row.Pinned -> row.item
                        is Manage Row.Flowing -> row.item
                        is Manage Row.Showing -> row.item
                        else -> null
                    }
                    if (item != null) stash Item(item)
                }
                recycler View.post Delayed({ is Swiping Out = false }, 135L)
            }

            override fun on Child Draw(
                c: Canvas,
                rv: Recycler View,
                view Holder: Recycler View.View Holder,
                d X: Float,
                d Y: Float,
                action State: Int,
                is Currently Active: Boolean
            ) {
                if (action State == Item Touch Helper.ACTION_STATE_SWIPE && d X < 0) {
                    val item View = view Holder.item View
                    val width = item View.width.to Float().coerce At Least(1f)
                    val ratio = (-d X / width).coerce In(0f, 1f)
                    val crossed Now = ratio >= get Swipe Threshold(view Holder)

                    if (crossed Now != is Threshold Crossed && is Currently Active) {
                        is Threshold Crossed = crossed Now
                        item View.perform Haptic Feedback(Haptic Feedback Constants.CLOCK_TICK)
                    }

                    val target Scale = if (crossed Now) 1.06f else 0.88f
                    current Scale += (target Scale - current Scale) * 0.25f

                    draw Swipe Background(c, item View, d X, crossed Now, current Scale)
                    item View.alpha = 1.0f - (ratio * 0.12f)
                } else {
                    view Holder.item View.alpha = 1.0f
                }
                super.on Child Draw(c, rv, view Holder, d X, d Y, action State, is Currently Active)
            }

            override fun clear View(rv: Recycler View, view Holder: Recycler View.View Holder) {
                super.clear View(rv, view Holder)
                //  bottom：Smooth ， 
                view Holder.item View.animate()
                    .scale X(1f)
                    .scale Y(1f)
                    .translation Z(0f)
                    .set Duration(160L)
                    .start()
                view Holder.item View.set Background Resource(R.drawable.bg_card_item)
                view Holder.item View.alpha = 1.0f
                is Threshold Crossed = false
                current Scale = 0.85f
                if (drag Dirty) {
                    drag Dirty = false
                    save Rules()
                    get Shared Preferences(PREFS, MODE_PRIVATE).edit().put Boolean(KEY_DRAG_HINT_DONE, true).apply()
                    tv Hint.visibility = View.GONE
                }
            }
        })
        item Touch Helper.attach To Recycler View(recycler View)
    }

    private fun draw Swipe Background(
        c: Canvas,
        item View: View,
        d X: Float,
        crossed: Boolean,
        scale: Float
    ) {
        val density = resources.display Metrics.density
        val pill Width = 56f * density
        val pill Height = 32f * density
        val radius = pill Height / 2f

        val revealed = (-d X).coerce At Least(0f)
        val fade Start = 20f * density
        if (revealed <= fade Start) return
        val alpha Progress = ((revealed - fade Start) / (36f * density)).coerce In(0f, 1f)

        bg Paint.color = Context Compat.get Color(this, if (crossed) R.color.hyper_accent else R.color.hyper_segment_track)
        bg Paint.alpha = (alpha Progress * 255).to Int()
        text Paint.color = if (crossed) Color.WHITE else Context Compat.get Color(this, R.color.hyper_text_secondary)
        text Paint.alpha = (alpha Progress * 255).to Int()

        val right Bound = item View.right.to Float() - 16f * density
        val base Offset = pill Width / 2f + 8f * density
        val extra Pull = ((revealed - (pill Width + 16f * density)).coerce At Least(0f)) * 0.15f
        val center X = right Bound - base Offset - extra Pull
        val center Y = (item View.top + item View.bottom) / 2f
        pill Rect.set(center X - pill Width / 2f, center Y - pill Height / 2f, center X + pill Width / 2f, center Y + pill Height / 2f)

        val save Count = c.save()
        c.clip Rect(item View.right.to Float() + d X, item View.top.to Float(), item View.right.to Float(), item View.bottom.to Float())
        c.scale(scale, scale, center X, center Y)
        c.draw Round Rect(pill Rect, radius, radius, bg Paint)
        val fm = text Paint.font Metrics
        c.draw Text("hide", center X, center Y - (fm.ascent + fm.descent) / 2f, text Paint)
        c.restore To Count(save Count)
    }

    // ───────── first of theleft Demo ─────────
    private fun maybe Play Swipe Hint() {
        if (!loaded) return
        val sp = get Shared Preferences(PREFS, MODE_PRIVATE)
        if (!sp.get Boolean("intro_shown", false) || sp.get Boolean(KEY_SWIPE_HINT_SHOWN, false)) return
        if (is Hint Scheduled || swipe Hint Animator?.is Running == true) return
        if (search Open || tab != TAB_VISIBLE || visible.is Empty()) return

        is Hint Scheduled = true
        recycler View.post Delayed({
            is Hint Scheduled = false
            if (is Finishing || is Destroyed) return@post Delayed
            play Swipe Hint Animation()
        }, 450L)
    }

    private fun play Swipe Hint Animation() {
        val sp = get Shared Preferences(PREFS, MODE_PRIVATE)
        if (sp.get Boolean(KEY_SWIPE_HINT_SHOWN, false)) return
        val holder = recycler View.find View Holder For Adapter Position(0) ?: return

        val target View = holder.item View
        val max Peek Dx = -62f * resources.display Metrics.density

        swipe Hint Animator?.cancel()
        hint Target View = target View

        val kf0 = Keyframe.of Float(0f, 0f)
        val kf1 = Keyframe.of Float(0.38f, max Peek Dx).apply { interpolator = Decelerate Interpolator(1.6f) }
        val kf2 = Keyframe.of Float(0.52f, max Peek Dx)
        val kf3 = Keyframe.of Float(1.0f, 0f).apply { interpolator = Overshoot Interpolator(0.6f) }
        val holder Values = Property Values Holder.of Keyframe("translation X", kf0, kf1, kf2, kf3)

        swipe Hint Animator = Value Animator.of Property Values Holder(holder Values).apply {
            duration = 750L
            add Update Listener { animator ->
                val dx = animator.get Animated Value("translation X") as Float
                hint Offset Dx = dx
                target View.translation X = dx
                val ratio = (-dx / target View.width.to Float().coerce At Least(1f)).coerce In(0f, 1f)
                target View.alpha = 1.0f - (ratio * 0.12f)
                recycler View.invalidate()
            }
            add Listener(object : Animator Listener Adapter() {
                override fun on Animation End(animation: Animator) {
                    cleanup()
                }

                override fun on Animation Cancel(animation: Animator) {
                    cleanup()
                }

                private fun cleanup() {
                    hint Target View = null
                    hint Offset Dx = 0f
                    target View.translation X = 0f
                    target View.alpha = 1.0f
                    recycler View.invalidate()
                    sp.edit().put Boolean(KEY_SWIPE_HINT_SHOWN, true).apply()
                }
            })
            start()
        }
    }
}
