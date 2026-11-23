package io.github.yilink1.hyperselect

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** 单个磁贴的展示信息;monochrome 表示图标可按控制中心样式重新着色 */
data class TileEntry(val spec: String, val label: String, val icon: Drawable?, val monochrome: Boolean)

/** 快照相对当前布局的差异 */
data class TileDiff(
    val added: List<TileEntry>,
    val removed: List<TileEntry>,
    val moved: Set<String>
) {
    val isSame get() = added.isEmpty() && removed.isEmpty() && moved.isEmpty()
    val count get() = added.size + removed.size + moved.size
    val changedSpecs: Set<String> get() = added.map { it.spec }.toSet() + moved
}

object TileVisuals {

    /** 系统控制中心编辑区不展示的固定磁贴 */
    private val HIDDEN_SPECS = setOf("wifi", "cell", "vowifi1", "vowifi2", "edit")

    private val SYSTEM_ICONS = mapOf(
        "mute" to R.drawable.ic_qs_mute,
        "bt" to R.drawable.ic_qs_bt,
        "nfc" to R.drawable.ic_qs_nfc,
        "flashlight" to R.drawable.ic_qs_flashlight,
        "screenshot" to R.drawable.ic_qs_screenshot,
        "batterysaver" to R.drawable.ic_qs_batterysaver,
        "rotation" to R.drawable.ic_qs_rotation,
        "papermode" to R.drawable.ic_qs_papermode,
        "gps" to R.drawable.ic_qs_gps,
        "hotspot" to R.drawable.ic_qs_hotspot,
        "airplane" to R.drawable.ic_qs_airplane,
        "quietmode" to R.drawable.ic_qs_quietmode,
        "night" to R.drawable.ic_qs_night,
        "reduce_brightness" to R.drawable.ic_qs_reduce_brightness,
        "screenrecorder" to R.drawable.ic_qs_screenrecorder,
        "aisubtitles" to R.drawable.ic_qs_aisubtitles,
        "freeformhang" to R.drawable.ic_qs_freeformhang,
        "taplus_tile" to R.drawable.ic_qs_taplus_tile,
        "cast" to R.drawable.ic_qs_cast,
        "wifi" to R.drawable.ic_qs_wifi,
        "cell" to R.drawable.ic_qs_cell
    )

    private val cache = HashMap<String, TileEntry>()

    fun splitSpecs(tiles: String): List<String> =
        tiles.split(",").map { it.trim() }.filter { it.isNotEmpty() && it !in HIDDEN_SPECS }

    fun entries(context: Context, tiles: String): List<TileEntry> =
        splitSpecs(tiles).map { entryOf(context, it) }

    fun entryOf(context: Context, spec: String): TileEntry = cache.getOrPut(spec) {
        val label = TileBackupStore.parsePreviewLabels(context, spec).firstOrNull() ?: spec
        val sys = SYSTEM_ICONS[spec]
        if (sys != null) {
            TileEntry(spec, label, ContextCompat.getDrawable(context, sys), true)
        } else {
            val raw = loadCustomIcon(context, spec)
            // processTileIcon 对单色图标返回新的着色副本,彩色图标原样返回
            val mono = raw != null && TileIconHelper.processTileIcon(context, raw) !== raw
            TileEntry(spec, label, raw, mono)
        }
    }

    private fun loadCustomIcon(context: Context, spec: String): Drawable? {
        if (!spec.startsWith("custom(") || !spec.endsWith(")")) return null
        val inner = spec.substring(7, spec.length - 1)
        val slash = inner.indexOf('/')
        if (slash == -1) return null
        val pkg = inner.substring(0, slash)
        val cls = inner.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
        val pm = context.packageManager
        val sInfo = runCatching { pm.getServiceInfo(ComponentName(pkg, cls), 0) }.getOrNull()
        // 直接从目标应用资源加载,避免 HyperOS 主题给非启动器图标套白色方底
        return sInfo?.let { si ->
            runCatching {
                val res = si.iconResource.takeIf { it != 0 } ?: si.applicationInfo.icon
                if (res == 0) null
                else pm.getResourcesForApplication(si.applicationInfo).getDrawable(res, null)
            }.getOrNull() ?: runCatching { si.loadIcon(pm) }.getOrNull()
        } ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
    }

    /** 新增 / 移除 / 位置变化(不在最长公共子序列中的共同磁贴) */
    fun diff(context: Context, snapshotTiles: String, currentTiles: String): TileDiff {
        val s = splitSpecs(snapshotTiles)
        val c = splitSpecs(currentTiles)
        val sSet = s.toSet()
        val cSet = c.toSet()
        val added = s.filter { it !in cSet }.map { entryOf(context, it) }
        val removed = c.filter { it !in sSet }.map { entryOf(context, it) }
        val cs = s.filter { it in cSet }
        val cc = c.filter { it in sSet }
        val dp = Array(cs.size + 1) { IntArray(cc.size + 1) }
        for (i in cs.indices.reversed()) for (j in cc.indices.reversed()) {
            dp[i][j] = if (cs[i] == cc[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val stable = HashSet<String>()
        var i = 0
        var j = 0
        while (i < cs.size && j < cc.size) {
            when {
                cs[i] == cc[j] -> { stable.add(cs[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return TileDiff(added, removed, cs.filter { it !in stable }.toSet())
    }

    /** 控制中心风格的圆形磁贴:灰底浅色图标,highlight 时为系统蓝 */
    private fun tileView(context: Context, entry: TileEntry, size: Int, highlight: Boolean): View {
        val bg = ContextCompat.getColor(context, if (highlight) R.color.qs_tile_on else R.color.qs_tile_off)
        val fg = ContextCompat.getColor(context, R.color.qs_tile_icon)
        val circle = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        }
        val icon = entry.icon
        if (icon != null) {
            val d = (icon.constantState?.newDrawable(context.resources) ?: icon).mutate()
            if (entry.monochrome) d.setTint(fg)
            val pad = (size * if (entry.monochrome) 0.25f else 0.20f).toInt()
            circle.addView(ImageView(context).apply {
                setImageDrawable(d)
                setPadding(pad, pad, pad, pad)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(size, size))
        } else {
            circle.addView(TextView(context).apply {
                text = entry.label.take(1)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.34f)
                setTextColor(fg)
            }, FrameLayout.LayoutParams(size, size))
        }
        return circle
    }

    /**
     * 在 container 中绘制 4 列控制中心预览。
     * maxRows 不为空时只露出 maxRows 行再加半行,底部渐隐到面板底色。
     */
    fun fillPanel(
        container: FrameLayout,
        entries: List<TileEntry>,
        tileDp: Int,
        highlight: Set<String> = emptySet(),
        maxRows: Int? = null,
        showLabel: Boolean = false
    ) {
        container.removeAllViews()
        val ctx = container.context
        val dens = ctx.resources.displayMetrics.density
        val size = (tileDp * dens).toInt()
        val labelMargin = (5 * dens).toInt()
        val labelPad = (2 * dens).toInt()
        val gap = if (showLabel) (14 * dens).toInt() else (tileDp * 0.24f * dens).toInt()
        val rows = entries.chunked(4)
        val clipped = maxRows != null && rows.size > maxRows
        val shownRows = if (clipped) rows.take(maxRows!! + 1) else rows

        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val rowHeight = if (showLabel) ViewGroup.LayoutParams.WRAP_CONTENT else size
        shownRows.forEachIndexed { index, rowItems ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 4) {
                val item = rowItems.getOrNull(i)
                if (item != null) {
                    if (showLabel) {
                        val cell = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                        val circle = tileView(ctx, item, size, item.spec in highlight)
                        cell.addView(circle, LinearLayout.LayoutParams(size, size).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                        })
                        val label = TextView(ctx).apply {
                            text = item.label
                            gravity = Gravity.CENTER_HORIZONTAL
                            isSingleLine = true
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            setTextColor(ContextCompat.getColor(ctx, R.color.qs_tile_label))
                            setPadding(labelPad, 0, labelPad, 0)
                        }
                        cell.addView(label, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = labelMargin
                        })
                        row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    } else {
                        val cell = FrameLayout(ctx)
                        cell.addView(
                            tileView(ctx, item, size, item.spec in highlight),
                            FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                        )
                        row.addView(cell, LinearLayout.LayoutParams(0, size, 1f))
                    }
                } else {
                    val placeholder = View(ctx)
                    val h = if (showLabel) ViewGroup.LayoutParams.WRAP_CONTENT else size
                    row.addView(placeholder, LinearLayout.LayoutParams(0, h, 1f))
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight).apply {
                if (index > 0) topMargin = gap
            })
        }

        if (clipped) {
            val visible = maxRows!! * (size + gap) + size / 2
            val clip = FrameLayout(ctx).apply { clipChildren = true }
            clip.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val panel = ContextCompat.getColor(ctx, R.color.qs_panel_bg)
            clip.addView(View(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(panel and 0x00FFFFFF, panel)
                )
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size, Gravity.BOTTOM))
            container.addView(clip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, visible))
        } else {
            container.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}