package io.github.yilink1.hyperselect

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 快照详情:控制中心样式预览 + 文字差异 + 恢复按钮。无 EXTRA_ID 时展示当前布局 */
class TileBackupDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "backup_id"
    }

    private var item: TileBackupItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_backup_detail)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val id = intent.getStringExtra(EXTRA_ID)
        item = id?.let { i -> TileBackupStore.loadBackups(this).firstOrNull { it.id == i } }
        if (id != null && item == null) { finish(); return }

        val more = findViewById<ImageView>(R.id.btnMore)
        if (item == null) more.visibility = View.GONE
        else more.setOnClickListener { showMenu(it) }

        bind()
    }

    private fun bind() {
        val current = TileBackupActions.readVerifiedCurrentTiles(this)
        val displayCurrent = current ?: RuleStore.loadQsTiles(this)
        val snapshot = item
        val tiles = snapshot?.tiles ?: displayCurrent.orEmpty()
        val entries = TileVisuals.entries(this, tiles)

        findViewById<TextView>(R.id.tvTitle).text = snapshot?.name ?: getString(R.string.header_current_layout)
        val count = getString(R.string.format_tile_count, entries.size)
        findViewById<TextView>(R.id.tvMeta).text = if (snapshot != null) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(snapshot.timestamp)) + " · " + count
        } else when {
            current != null -> count
            displayCurrent != null -> getString(R.string.tile_layout_cached)
            else -> getString(R.string.tile_layout_status_unavailable)
        }

        TileVisuals.fillPanel(
            findViewById<FrameLayout>(R.id.panelPreview), entries,
            tileDp = 48, showLabel = true
        )

        val title = findViewById<TextView>(R.id.tvDiffTitle)
        val group = findViewById<LinearLayout>(R.id.groupDiff)
        val restore = findViewById<TextView>(R.id.btnRestore)
        group.removeAllViews()

        if (snapshot == null || current == null) {
            title.visibility = View.GONE
            group.visibility = View.GONE
            restore.visibility = if (snapshot != null && TileSettings.hasTiles(snapshot.tiles)) View.VISIBLE else View.GONE
            restore.setOnClickListener {
                if (snapshot != null) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.tile_restore_without_preview)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_restore_layout) { _, _ ->
                            if (TileBackupActions.apply(this, snapshot)) bind()
                        }
                        .show()
                }
            }
            return
        }

        val diff = TileVisuals.diff(this, snapshot.tiles, current)
        val rows = mutableListOf<Pair<String, String>>()
        if (diff.isSame) {
            rows += getString(R.string.detail_same) to ""
        } else {
            if (diff.added.isNotEmpty()) rows += getString(R.string.detail_added) to diff.added.joinToString("、") { it.label }
            if (diff.removed.isNotEmpty()) rows += getString(R.string.detail_removed) to diff.removed.joinToString("、") { it.label }
            if (diff.moved.isNotEmpty()) rows += getString(R.string.detail_moved) to getString(R.string.detail_moved_value)
        }
        rows.forEachIndexed { i, (k, v) -> group.addView(diffRow(k, v, i, rows.size)) }

        title.visibility = View.VISIBLE
        group.visibility = View.VISIBLE
        restore.visibility = if (diff.isSame || !TileSettings.hasTiles(snapshot.tiles)) View.GONE else View.VISIBLE
        restore.setOnClickListener {
            if (TileBackupActions.apply(this, snapshot)) bind()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun diffRow(key: String, value: String, index: Int, total: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(when {
                total == 1 -> R.drawable.bg_group_single
                index == 0 -> R.drawable.bg_group_top
                index == total - 1 -> R.drawable.bg_group_bottom
                else -> R.drawable.bg_group_mid
            })
        }
        row.addView(TextView(this).apply {
            text = key
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.hyper_text_primary))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (value.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = value
                gravity = Gravity.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(context, R.color.hyper_text_secondary))
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (index > 0) {
            val wrap = FrameLayout(this)
            wrap.addView(row)
            wrap.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.hyper_divider))
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(1) / 2 + 1).apply { marginStart = dp(16) })
            return wrap
        }
        return row
    }

    private fun showMenu(anchor: View) {
        val snapshot = item ?: return
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.action_rename)
            menu.add(0, 2, 1, R.string.action_copy)
            menu.add(0, 3, 2, R.string.action_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> TileBackupActions.rename(this@TileBackupDetailActivity, snapshot) { bind() }
                    2 -> TileBackupActions.copy(this@TileBackupDetailActivity, snapshot)
                    3 -> TileBackupActions.confirmDelete(this@TileBackupDetailActivity, snapshot) { finish() }
                }
                true
            }
        }.show()
    }
}
