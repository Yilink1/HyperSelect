package io.github.yilink1.hyperselect

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Text Selection / System Share / Open with Shared UI and interactions for the three management pages。
 *
 * Interaction model (top dual-nav tabs + tactile cards + smart search):
 *  - Top dual-tab bar (Show N / Hide N): clearly shows status, quick tab switch;
 *  -  Physical card：Showpage Dragorlong presscardtopbottomSort，Swipe leftto hide；
 *  - Hide page: the "+" restore button on the right of each card restores it to the Show list;
 *  - Top search: Pin / Hide / Restore action capsules below the search field;
 *  - All changes take effect immediately, with undo support.
 */
abstract class ManageListActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    // ───────── Subclass hooks ─────────
    protected abstract val screenTitle: String
    protected abstract val emptyText: String

    /**  have Customorderwhen， Sort（Text Selection SystemBackorder）。 */
    protected open val sortByLabelWhenNoorder: Boolean = true

    /**  after 。 */
    protected abstract fun queryTargets(pm: PackageManager): List<ResolveInfo>
    protected abstract fun blacklistOf(config: RuleConfig): Set<String>
    protected abstract fun orderOf(config: RuleConfig): List<String>
    protected abstract fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig

    protected open fun testActions(): List<Pair<String, () -> Unit>> = emptyList()
    protected open fun onCreateExtra(container: ViewGroup) {}
    protected open fun onConfigLoaded(config: RuleConfig) {}
    protected open fun applyExtra(config: RuleConfig): RuleConfig = config

    // ───────── Status ─────────
    private companion object {
        const val PREFS = "hyper_select_prefs"
        const val KEY_DRAG_HINT_DONE = "drag_hint_done"
        const val KEY_SWIPE_HINT_SHOWN = "has_shown_swipe_hint"
        const val TAB_VISIBLE = 0
        const val TAB_HIDDEN = 1
    }

    /** YesNoEnablePin  + By system Tabs（System ShareandOpen with Enable）。 */
    protected open val enablePinnedSection: Boolean = false

    private val visible = ArrayList<TextActionItem>()
    private val hidden = ArrayList<TextActionItem>()
    private var pinnedCount = 0
    private var tab = TAB_VISIBLE

    private var searchOpen = false
    private var query = ""
    private var loaded = false
    private var dragDirty = false

    private lateinit var headerBar: View
    private lateinit var searchBox: View
    private lateinit var etSearch: EditText
    private lateinit var layoutExtra: FrameLayout
    private lateinit var segment: View
    private lateinit var tvTabVisible: TextView
    private lateinit var tvTabHidden: TextView
    private lateinit var tvHint: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var undoBar: LinearLayout
    private lateinit var tvUndoMsg: TextView

    private val adapter = ManageAdapter(
        onRestore = { item -> restoreItem(item) },
        onPin = { item -> pinItem(item) },
        onUnpin = { item -> unpinItem(item) },
        onStash = { item -> stashItem(item) },
        onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
    )

    private lateinit var itemTouchHelper: ItemTouchHelper
    private val executor = Executors.newSingleThreadExecutor()

    private data class UndoEntry(
        val state: Triple<List<TextActionItem>, List<TextActionItem>, Int>,
        val message: String
    )
    private val undoHistory = ArrayDeque<UndoEntry>()
    private val maxUndoDepth = 10
    private val hideUndoRunnable = Runnable { hideUndo() }

    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSearch()
        }
    }

    // Swipe gesture background and hint animation drawing
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pillRect = RectF()
    private var hintTargetView: View? = null
    private var hintOffsetDx = 0f
    private var swipeHintAnimator: ValueAnimator? = null
    private var isHintScheduled = false

    // ───────── Lifecycle ─────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        textPaint.textSize = 13f * resources.displayMetrics.density
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        headerBar = findViewById(R.id.headerBar)
        searchBox = findViewById(R.id.searchBox)
        etSearch = findViewById(R.id.etSearch)
        layoutExtra = findViewById(R.id.layoutExtra)
        segment = findViewById(R.id.segment)
        tvTabVisible = findViewById(R.id.tvTabVisible)
        tvTabHidden = findViewById(R.id.tvTabHidden)
        tvHint = findViewById(R.id.tvHint)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        undoBar = findViewById(R.id.undoBar)
        tvUndoMsg = findViewById(R.id.tvUndoMsg)

        findViewById<TextView>(R.id.tvTitle).text = screenTitle
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSearch).setOnClickListener { openSearch() }
        findViewById<View>(R.id.btnCloseSearch).setOnClickListener { closeSearch() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.btnUndo).setOnClickListener {
            val entry = undoHistory.removeLastOrNull() ?: return@setOnClickListener
            restore(entry.state)
            if (undoHistory.isEmpty()) hideUndo() else presentUndoBar()
        }

        tvTabVisible.setOnClickListener { selectTab(TAB_VISIBLE) }
        tvTabHidden.setOnClickListener { selectTab(TAB_HIDDEN) }
        onBackPressedDispatcher.addCallback(this, searchBackCallback)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                if (searchOpen) refreshRows()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupList()
        onCreateExtra(layoutExtra)
        load()
    }

    override fun onResume() {
        super.onResume()
        maybePlaySwipeHint()
    }

    override fun onDestroy() {
        super.onDestroy()
        swipeHintAnimator?.cancel()
        swipeHintAnimator = null
        undoBar.removeCallbacks(hideUndoRunnable)
        executor.shutdown()
    }

    // ───────── Data loading / saving ─────────
    private fun load() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        executor.execute {
            val pm = packageManager
            val infos = runCatching { queryTargets(pm) }.getOrDefault(emptyList())
            val config = RuleStore.load(this)
            val blacklist = blacklistOf(config)
            val order = orderOf(config)

            val seen = HashSet<String>()
            val all = ArrayList<TextActionItem>()
            for (info in infos) {
                val activityInfo = info.activityInfo ?: continue
                val pkg = activityInfo.packageName
                if (pkg == packageName) continue

                val component = android.content.ComponentName(pkg, activityInfo.name).flattenToShortString()
                if (!seen.add(component)) continue

                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: activityInfo.loadLabel(pm)?.toString()
                    ?: activityInfo.name
                val icon = info.loadIcon(pm) ?: activityInfo.loadIcon(pm)
                val enabled = !blacklist.contains(component) && !blacklist.contains(pkg)
                all.add(TextActionItem(label, pkg, component, icon, enabled))
            }

            val shown = ArrayList<TextActionItem>()
            val hiddenList = ArrayList<TextActionItem>()
            for (item in all) if (item.isEnabled) shown.add(item) else hiddenList.add(item)

            // Legacy migration fallback: older versions wrote all scanned apps to order.
            //  Pin have ，  order  （  10  ）， old  dump，Reset  0 Pin，Restore By system 。
            val isLegacyFullDump = enablePinnedSection && order.size > 10 && order.size >= (shown.size * 0.5)
            val effectiveorder = if (isLegacyFullDump) emptyList() else order

            val orderIndex = HashMap<String, Int>()
            effectiveorder.forEachIndexed { index, key -> if (!orderIndex.containsKey(key)) orderIndex[key] = index }

            var initialPinnedCount = 0
            if (enablePinnedSection) {
                val pinnedList = ArrayList<TextActionItem>()
                val flowingList = ArrayList<TextActionItem>()
                for (item in shown) {
                    if (orderIndex.containsKey(item.componentName) || orderIndex.containsKey(item.packageName)) {
                        pinnedList.add(item)
                    } else {
                        flowingList.add(item)
                    }
                }
                pinnedList.sortWith(compareBy { item ->
                    orderIndex[item.componentName] ?: orderIndex[item.packageName] ?: Int.MAX_VALUE
                })
                flowingList.sortBy { it.label }
                shown.clear()
                shown.addAll(pinnedList)
                shown.addAll(flowingList)
                initialPinnedCount = pinnedList.size
            } else {
                if (order.isNotEmpty()) {
                    shown.sortWith(compareBy { item ->
                        orderIndex[item.componentName] ?: orderIndex[item.packageName] ?: Int.MAX_VALUE
                    })
                } else if (sortByLabelWhenNoorder) {
                    shown.sortBy { it.label }
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                visible.clear()
                visible.addAll(shown)
                pinnedCount = initialPinnedCount
                hidden.clear()
                hidden.addAll(hiddenList)
                loaded = true
                progressBar.visibility = View.GONE
                onConfigLoaded(config)
                refreshRows()
                maybePlaySwipeHint()
            }
        }
    }

    /** Persist current Show / Hide / order state to the system config. */
    protected fun saveRules() {
        if (!loaded) return
        val newBlacklist = HashSet<String>()
        val neworder = ArrayList<String>()

        if (enablePinnedSection) {
            val limit = pinnedCount.coerceAtMost(visible.size)
            for (i in 0 until limit) {
                neworder.add(visible[i].componentName)
            }
        } else {
            for (item in visible) neworder.add(item.componentName)
        }

        for (item in hidden) {
            newBlacklist.add(item.componentName)
        }
        val config = applyExtra(withRules(RuleStore.load(this), newBlacklist, neworder))
        RuleStore.save(this, config)
        contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
    }

    // ───────── List building & refresh ─────────
    private fun matches(item: TextActionItem): Boolean {
        if (query.isEmpty()) return true
        return item.label.lowercase(Locale.getDefault()).contains(query) ||
            item.packageName.lowercase(Locale.getDefault()).contains(query)
    }

    private fun buildRows(): List<ManageRow> {
        if (searchOpen) {
            val pinnedSet = if (enablePinnedSection) {
                visible.take(pinnedCount).map { it.componentName }.toSet()
            } else emptySet()
            val filtered = (visible + hidden).filter { matches(it) }
            return filtered.map { ManageRow.Search(it, isPinned = pinnedSet.contains(it.componentName)) }
        }
        if (tab == TAB_HIDDEN) {
            return hidden.map { ManageRow.Hidden(it) }
        }
        // tab == TAB_VISIBLE
        if (enablePinnedSection) {
            val rows = ArrayList<ManageRow>()
            val actualPinned = pinnedCount.coerceAtMost(visible.size)
            val flowingCount = visible.size - actualPinned

            if (actualPinned > 0) {
                rows.add(ManageRow.Header(getString(R.string.pinned_apps_header), actualPinned, getString(R.string.pinned_header_hint)))
                for (i in 0 until actualPinned) {
                    rows.add(ManageRow.Pinned(visible[i]))
                }
            }

            if (flowingCount > 0) {
                val flowingHint = if (actualPinned > 0) getString(R.string.sorted_hint) else getString(R.string.sorted_hint_tap)
                rows.add(ManageRow.Header(getString(R.string.all_apps), flowingCount, flowingHint))
                for (i in actualPinned until visible.size) {
                    rows.add(ManageRow.Flowing(visible[i]))
                }
            }
            return rows
        } else {
            return visible.map { ManageRow.Showing(it) }
        }
    }

    private fun refreshRows() {
        val rows = buildRows()
        adapter.submit(rows)
        updateChrome(rows)
    }

    private fun updateChrome(rows: List<ManageRow>) {
        tvTabVisible.text = getString(R.string.show_count, visible.size)
        tvTabHidden.text = getString(R.string.hide_tab_count, hidden.size)
        styleTab(tvTabVisible, tab == TAB_VISIBLE)
        styleTab(tvTabHidden, tab == TAB_HIDDEN)
        segment.visibility = if (searchOpen) View.GONE else View.VISIBLE

        val dragHintDone = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DRAG_HINT_DONE, false)
        tvHint.visibility =
            if (loaded && !searchOpen && tab == TAB_VISIBLE && !dragHintDone && visible.size > 1 && !enablePinnedSection) View.VISIBLE
            else View.GONE

        if (loaded && rows.isEmpty()) {
            tvEmpty.text = when {
                visible.isEmpty() && hidden.isEmpty() -> emptyText
                searchOpen -> getString(R.string.no_apps_found)
                tab == TAB_VISIBLE -> getString(R.string.no_hidden_apps)
                else -> getString(R.string.no_hidden_apps)
            }
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    private fun styleTab(tv: TextView, selected: Boolean) {
        tv.setBackgroundResource(if (selected) R.drawable.bg_segment_thumb else 0)
        tv.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.hyper_text_primary else R.color.hyper_text_secondary)
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun selectTab(newTab: Int) {
        if (tab == newTab) return
        tab = newTab
        refreshRows()
        recyclerView.scrollToPosition(0)
    }

    // ───────── Search ─────────
    private fun openSearch() {
        if (searchOpen) return
        searchOpen = true
        searchBackCallback.isEnabled = true
        headerBar.visibility = View.GONE
        searchBox.alpha = 0f
        searchBox.visibility = View.VISIBLE
        searchBox.animate().alpha(1f).setDuration(140L).start()
        query = ""
        refreshRows()
        etSearch.requestFocus()
        etSearch.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        if (!searchOpen) return
        searchOpen = false
        searchBackCallback.isEnabled = false
        etSearch.setText("")
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(etSearch.windowToken, 0)
        searchBox.visibility = View.GONE
        headerBar.visibility = View.VISIBLE
        refreshRows()
    }

    // ───────── cardAction（Pin / hide / Restore） ─────────
    private fun snapshot(): Triple<List<TextActionItem>, List<TextActionItem>, Int> =
        Triple(visible.map { it.copy() }, hidden.map { it.copy() }, pinnedCount)

    private fun pinItem(item: TextActionItem) {
        val before = snapshot()
        if (enablePinnedSection) {
            val index = visible.indexOfFirst { it.componentName == item.componentName }
            if (index >= 0) {
                if (index >= pinnedCount) {
                    val moved = visible.removeAt(index)
                    visible.add(pinnedCount, moved)
                    pinnedCount++
                }
            } else {
                hidden.removeAll { it.componentName == item.componentName }
                item.isEnabled = true
                visible.add(pinnedCount, item)
                pinnedCount++
            }
        } else {
            visible.removeAll { it.componentName == item.componentName }
            hidden.removeAll { it.componentName == item.componentName }
            item.isEnabled = true
            visible.add(0, item)
        }
        saveRules()
        refreshRows()
        showUndo(getString(R.string.undo_pinned, item.label), before)
        if (!searchOpen && tab == TAB_VISIBLE) {
            recyclerView.scrollToPosition(0)
        }
    }

    private fun unpinItem(item: TextActionItem) {
        val before = snapshot()
        if (enablePinnedSection) {
            val index = visible.indexOfFirst { it.componentName == item.componentName }
            if (index in 0 until pinnedCount) {
                val moved = visible.removeAt(index)
                pinnedCount--
                visible.add(pinnedCount, moved)
                saveRules()
                refreshRows()
                showUndo(getString(R.string.undo_unpinned, item.label), before)
            }
        }
    }

    private fun stashItem(item: TextActionItem) {
        val before = snapshot()
        val index = visible.indexOfFirst { it.componentName == item.componentName }
        if (index >= 0) {
            visible.removeAt(index)
            if (enablePinnedSection && index < pinnedCount) {
                pinnedCount = (pinnedCount - 1).coerceAtLeast(0)
            }
        }
        hidden.removeAll { it.componentName == item.componentName }
        item.isEnabled = false
        hidden.add(0, item)
        saveRules()
        refreshRows()
        showUndo(getString(R.string.undo_hid, item.label), before)
    }

    private fun restoreItem(item: TextActionItem) {
        val before = snapshot()
        hidden.removeAll { it.componentName == item.componentName }
        visible.removeAll { it.componentName == item.componentName }
        item.isEnabled = true
        if (enablePinnedSection) {
            visible.add(pinnedCount, item)
        } else {
            visible.add(0, item)
        }
        saveRules()
        refreshRows()
        showUndo(getString(R.string.undo_restored, item.label), before)
    }

    private fun bulk(enable: Boolean) {
        val targets: List<TextActionItem> = when {
            searchOpen -> (visible + hidden).filter { matches(it) && it.isEnabled != enable }
            enable -> ArrayList(hidden)
            else -> ArrayList(visible)
        }
        if (targets.isEmpty()) {
            Toast.makeText(this, if (enable) getString(R.string.no_apps_restore) else getString(R.string.no_hidden_apps), Toast.LENGTH_SHORT).show()
            return
        }
        val before = snapshot()
        for (item in targets.asReversed()) {
            if (enable) {
                hidden.removeAll { it.componentName == item.componentName }
                item.isEnabled = true
                if (enablePinnedSection) {
                    visible.add(pinnedCount, item)
                } else {
                    visible.add(0, item)
                }
            } else {
                val idx = visible.indexOfFirst { it.componentName == item.componentName }
                if (idx >= 0) {
                    visible.removeAt(idx)
                    if (enablePinnedSection && idx < pinnedCount) {
                        pinnedCount = (pinnedCount - 1).coerceAtLeast(0)
                    }
                }
                item.isEnabled = false
                hidden.add(0, item)
            }
        }
        if (!enable && enablePinnedSection) {
            pinnedCount = 0
        }
        saveRules()
        refreshRows()
        showUndo(if (enable) getString(R.string.bulk_restore_count, targets.size) else getString(R.string.bulk_hide_count, targets.size), before)
    }

    private fun restore(snapshot: Triple<List<TextActionItem>, List<TextActionItem>, Int>) {
        visible.clear()
        visible.addAll(snapshot.first.map { it.copy() })
        hidden.clear()
        hidden.addAll(snapshot.second.map { it.copy() })
        pinnedCount = snapshot.third
        saveRules()
        refreshRows()
        Toast.makeText(this, getString(R.string.undo), Toast.LENGTH_SHORT).show()
    }

    private fun resetPinnedorder() {
        if (pinnedCount <= 1) return
        val before = snapshot()
        val pinnedSublist = ArrayList(visible.subList(0, pinnedCount))
        pinnedSublist.sortBy { it.label }
        for (i in 0 until pinnedCount) {
            visible[i] = pinnedSublist[i]
        }
        saveRules()
        refreshRows()
        showUndo(getString(R.string.reset_pinned_order), before)
    }

    private fun clearAllPins() {
        if (pinnedCount == 0) return
        val before = snapshot()
        pinnedCount = 0
        saveRules()
        refreshRows()
        showUndo(getString(R.string.unpin_all), before)
    }

    private fun resetorder() {
        val config = RuleStore.load(this)
        RuleStore.save(this, withRules(config, blacklistOf(config), emptyList()))
        contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
        load()
        Toast.makeText(this, getString(R.string.restore_default_order), Toast.LENGTH_SHORT).show()
    }

    // ───────── Undo  ─────────
    private fun showUndo(message: String, before: Triple<List<TextActionItem>, List<TextActionItem>, Int>) {
        undoHistory.addLast(UndoEntry(before, message))
        while (undoHistory.size > maxUndoDepth) undoHistory.removeFirst()
        presentUndoBar()
    }

    /** Show the undo bar for the most recent entry, restarting the auto-hide timer. */
    private fun presentUndoBar() {
        val top = undoHistory.lastOrNull() ?: return
        tvUndoMsg.text = top.message
        undoBar.removeCallbacks(hideUndoRunnable)
        undoBar.animate().cancel()
        undoBar.visibility = View.VISIBLE
        undoBar.alpha = 0f
        undoBar.translationY = dp(12).toFloat()
        undoBar.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        undoBar.postDelayed(hideUndoRunnable, 4000L)
    }

    private fun hideUndo() {
        undoHistory.clear()
        undoBar.removeCallbacks(hideUndoRunnable)
        undoBar.animate().cancel()
        undoBar.animate()
            .alpha(0f)
            .translationY(dp(12).toFloat())
            .setDuration(160L)
            .withEndAction { undoBar.visibility = View.GONE }
            .start()
    }

    // ───────── Menu / Instructions ─────────
    private fun showMenu(anchor: View) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (searchOpen) {
            items.add(Pair(getString(R.string.restore_all_results), { bulk(true) }))
            items.add(Pair(getString(R.string.hide_all_results), { bulk(false) }))
        } else if (tab == TAB_VISIBLE) {
            if (enablePinnedSection) {
                if (pinnedCount > 1) {
                    items.add(Pair(getString(R.string.reset_pinned_order), { resetPinnedorder() }))
                }
                if (pinnedCount > 0) {
                    items.add(Pair(getString(R.string.unpin_all), { clearAllPins() }))
                }
            } else {
                items.add(Pair(getString(R.string.restore_default_order), { resetorder() }))
            }
            items.add(Pair(getString(R.string.hide_all), { bulk(false) }))
            items.add(Pair(getString(R.string.restore_all), { bulk(true) }))
        } else {
            items.add(Pair(getString(R.string.restore_all), { bulk(true) }))
            items.add(Pair(getString(R.string.hide_all), { bulk(false) }))
        }
        items.addAll(testActions())
        items.add(Pair(getString(R.string.usage_instructions), { showHelp() }))
        showPopupMenu(anchor, items)
    }

    private fun showHelp() {
        val items = if (enablePinnedSection) {
            listOf(
                HelpFeatureItem(
                    R.drawable.ic_help_gesture,
                    getString(R.string.help_title_swipe),
                    getString(R.string.help_desc_swipe)
                ),
                HelpFeatureItem(
                    R.drawable.ic_help_pin,
                    getString(R.string.help_title_tap_pin),
                    getString(R.string.help_desc_pin)
                ),
                HelpFeatureItem(
                    R.drawable.ic_help_drag,
                    getString(R.string.help_title_drag),
                    getString(R.string.help_desc_drag)
                ),
                HelpFeatureItem(
                    R.drawable.ic_add,
                    getString(R.string.help_title_restore),
                    getString(R.string.help_desc_restore)
                )
            )
        } else {
            listOf(
                HelpFeatureItem(
                    R.drawable.ic_help_gesture,
                    getString(R.string.help_title_swipe),
                    getString(R.string.help_desc_swipe_plain)
                ),
                HelpFeatureItem(
                    R.drawable.ic_help_drag,
                    getString(R.string.help_title_sort),
                    getString(R.string.help_desc_sort)
                ),
                HelpFeatureItem(
                    R.drawable.ic_help_sync,
                    getString(R.string.help_title_immediate),
                    getString(R.string.help_desc_immediate)
                )
            )
        }
        showFeatureHelpSheet(getString(R.string.help_sheet_title, screenTitle), items)
    }

    // ───────── list （DragSort + left hide） ─────────
    private fun setupList() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ManageListActivity)
            adapter = this@ManageListActivity.adapter
            itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                removeDuration = 100L
                moveDuration = 150L
                addDuration = 100L
            }
        }

        // first of/theleft Demo 
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                val target = hintTargetView
                if (target != null && hintOffsetDx < 0f) {
                    drawSwipeBackground(c, target, hintOffsetDx, false, 0.88f)
                }
            }
        })

        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (hintTargetView != null) swipeHintAnimator?.cancel()
                return false
            }
        })

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, 0) {
            private var isThresholdCrossed = false
            private var currentScale = 0.85f
            private var isSwipingOut = false
            private var swipeStartTime = 0L

            override fun getMovementFlags(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (isSwipingOut) {
                    if (SystemClock.uptimeMillis() - swipeStartTime > 260L) isSwipingOut = false
                    else return makeMovementFlags(0, 0)
                }
                // Disable drag-sort and swipe-to-hide on search or non-Show tabs
                if (searchOpen || tab != TAB_VISIBLE) return makeMovementFlags(0, 0)

                val pos = viewHolder.bindingAdapterPosition
                val row = adapter.getItem(pos) ?: return makeMovementFlags(0, 0)

                return if (enablePinnedSection) {
                    when (row) {
                        is ManageRow.Pinned -> makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT)
                        is ManageRow.Flowing -> makeMovementFlags(0, ItemTouchHelper.LEFT)
                        else -> makeMovementFlags(0, 0)
                    }
                } else {
                    if (pos !in visible.indices) makeMovementFlags(0, 0)
                    else makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT)
                }
            }

            override fun canDropOver(
                rv: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val toPos = target.bindingAdapterPosition
                val toRow = adapter.getItem(toPos)
                return if (enablePinnedSection) {
                    toRow is ManageRow.Pinned
                } else {
                    toPos in visible.indices
                }
            }

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (searchOpen || tab != TAB_VISIBLE) return false
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition

                if (enablePinnedSection) {
                    val fromRow = adapter.getItem(fromPos)
                    val toRow = adapter.getItem(toPos)
                    if (fromRow !is ManageRow.Pinned || toRow !is ManageRow.Pinned) return false
                    val fromIndex = fromPos - 1
                    val toIndex = toPos - 1
                    if (fromIndex !in 0 until pinnedCount || toIndex !in 0 until pinnedCount) return false

                    val moved = visible.removeAt(fromIndex)
                    visible.add(toIndex, moved)
                    adapter.notifyItemMoved(fromPos, toPos)
                    dragDirty = true
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return true
                } else {
                    if (fromPos !in visible.indices || toPos !in visible.indices) return false
                    val moved = visible.removeAt(fromPos)
                    visible.add(toPos, moved)
                    adapter.notifyItemMoved(fromPos, toPos)
                    dragDirty = true
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return true
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewHolder.itemView.setBackgroundResource(R.drawable.bg_row_lift)
                    viewHolder.itemView.animate()
                        .scaleX(1.025f)
                        .scaleY(1.025f)
                        .translationZ(dp(14).toFloat())
                        .setDuration(140L)
                        .start()
                }
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.22f
            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 0.45f
            override fun getSwipeVelocityThreshold(defaultValue: Float): Float = defaultValue * 0.5f

            override fun getAnimationDuration(
                rv: RecyclerView,
                animationType: Int,
                animateDx: Float,
                animateDy: Float
            ): Long {
                if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_SUCCESS) {
                    isSwipingOut = true
                    swipeStartTime = SystemClock.uptimeMillis()
                }
                return if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) 150L else 100L
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.LEFT) {
                    val position = viewHolder.bindingAdapterPosition
                    val row = adapter.getItem(position)
                    val item = when (row) {
                        is ManageRow.Pinned -> row.item
                        is ManageRow.Flowing -> row.item
                        is ManageRow.Showing -> row.item
                        else -> null
                    }
                    if (item != null) stashItem(item)
                }
                recyclerView.postDelayed({ isSwipingOut = false }, 135L)
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    val itemView = viewHolder.itemView
                    val width = itemView.width.toFloat().coerceAtLeast(1f)
                    val ratio = (-dX / width).coerceIn(0f, 1f)
                    val crossedNow = ratio >= getSwipeThreshold(viewHolder)

                    if (crossedNow != isThresholdCrossed && isCurrentlyActive) {
                        isThresholdCrossed = crossedNow
                        itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }

                    val targetScale = if (crossedNow) 1.06f else 0.88f
                    currentScale += (targetScale - currentScale) * 0.25f

                    drawSwipeBackground(c, itemView, dX, crossedNow, currentScale)
                    itemView.alpha = 1.0f - (ratio * 0.12f)
                } else {
                    viewHolder.itemView.alpha = 1.0f
                }
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                //  bottom：Smooth ， 
                viewHolder.itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationZ(0f)
                    .setDuration(160L)
                    .start()
                viewHolder.itemView.setBackgroundResource(R.drawable.bg_card_item)
                viewHolder.itemView.alpha = 1.0f
                isThresholdCrossed = false
                currentScale = 0.85f
                if (dragDirty) {
                    dragDirty = false
                    saveRules()
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DRAG_HINT_DONE, true).apply()
                    tvHint.visibility = View.GONE
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun drawSwipeBackground(
        c: Canvas,
        itemView: View,
        dX: Float,
        crossed: Boolean,
        scale: Float
    ) {
        val density = resources.displayMetrics.density
        val pillWidth = 56f * density
        val pillHeight = 32f * density
        val radius = pillHeight / 2f

        val revealed = (-dX).coerceAtLeast(0f)
        val fadeStart = 20f * density
        if (revealed <= fadeStart) return
        val alphaProgress = ((revealed - fadeStart) / (36f * density)).coerceIn(0f, 1f)

        bgPaint.color = ContextCompat.getColor(this, if (crossed) R.color.hyper_accent else R.color.hyper_segment_track)
        bgPaint.alpha = (alphaProgress * 255).toInt()
        textPaint.color = if (crossed) Color.WHITE else ContextCompat.getColor(this, R.color.hyper_text_secondary)
        textPaint.alpha = (alphaProgress * 255).toInt()

        val rightBound = itemView.right.toFloat() - 16f * density
        val baseOffset = pillWidth / 2f + 8f * density
        val extraPull = ((revealed - (pillWidth + 16f * density)).coerceAtLeast(0f)) * 0.15f
        val centerX = rightBound - baseOffset - extraPull
        val centerY = (itemView.top + itemView.bottom) / 2f
        pillRect.set(centerX - pillWidth / 2f, centerY - pillHeight / 2f, centerX + pillWidth / 2f, centerY + pillHeight / 2f)

        val saveCount = c.save()
        c.clipRect(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
        c.scale(scale, scale, centerX, centerY)
        c.drawRoundRect(pillRect, radius, radius, bgPaint)
        val fm = textPaint.fontMetrics
        c.drawText(getString(R.string.hide), centerX, centerY - (fm.ascent + fm.descent) / 2f, textPaint)
        c.restoreToCount(saveCount)
    }

    // ───────── first of/theleft Demo ─────────
    private fun maybePlaySwipeHint() {
        if (!loaded) return
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!sp.getBoolean("intro_shown", false) || sp.getBoolean(KEY_SWIPE_HINT_SHOWN, false)) return
        if (isHintScheduled || swipeHintAnimator?.isRunning == true) return
        if (searchOpen || tab != TAB_VISIBLE || visible.isEmpty()) return

        isHintScheduled = true
        recyclerView.postDelayed({
            isHintScheduled = false
            if (isFinishing || isDestroyed) return@postDelayed
            playSwipeHintAnimation()
        }, 450L)
    }

    private fun playSwipeHintAnimation() {
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (sp.getBoolean(KEY_SWIPE_HINT_SHOWN, false)) return
        val holder = recyclerView.findViewHolderForAdapterPosition(0) ?: return

        val targetView = holder.itemView
        val maxPeekDx = -62f * resources.displayMetrics.density

        swipeHintAnimator?.cancel()
        hintTargetView = targetView

        val kf0 = Keyframe.ofFloat(0f, 0f)
        val kf1 = Keyframe.ofFloat(0.38f, maxPeekDx).apply { interpolator = DecelerateInterpolator(1.6f) }
        val kf2 = Keyframe.ofFloat(0.52f, maxPeekDx)
        val kf3 = Keyframe.ofFloat(1.0f, 0f).apply { interpolator = OvershootInterpolator(0.6f) }
        val holderValues = PropertyValuesHolder.ofKeyframe("translationX", kf0, kf1, kf2, kf3)

        swipeHintAnimator = ValueAnimator.ofPropertyValuesHolder(holderValues).apply {
            duration = 750L
            addUpdateListener { animator ->
                val dx = animator.getAnimatedValue("translationX") as Float
                hintOffsetDx = dx
                targetView.translationX = dx
                val ratio = (-dx / targetView.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                targetView.alpha = 1.0f - (ratio * 0.12f)
                recyclerView.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanup()
                }

                override fun onAnimationCancel(animation: Animator) {
                    cleanup()
                }

                private fun cleanup() {
                    hintTargetView = null
                    hintOffsetDx = 0f
                    targetView.translationX = 0f
                    targetView.alpha = 1.0f
                    recyclerView.invalidate()
                    sp.edit().putBoolean(KEY_SWIPE_HINT_SHOWN, true).apply()
                }
            })
            start()
        }
    }
}
