package io.github.yilink1.hyperselect

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TileBackupAdapter(
    private var currentTiles: String,
    private var currentLabels: List<String>,
    private var backups: MutableList<TileBackupItem>,
    private val onSaveCurrent: () -> Unit,
    private val onApply: (TileBackupItem) -> Unit,
    private val onCopy: (TileBackupItem) -> Unit,
    private val onRename: (TileBackupItem) -> Unit,
    private val onDelete: (TileBackupItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateCurrent(tiles: String, labels: List<String>) {
        this.currentTiles = tiles
        this.currentLabels = labels
        notifyItemChanged(0)
    }

    fun updateBackups(newBackups: MutableList<TileBackupItem>) {
        this.backups = newBackups
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_ITEM

    override fun getItemCount(): Int = backups.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.header_tile_backup, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_tile_backup, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            val ctx = holder.itemView.context
            val count = currentLabels.size
            holder.tvCurrentSubtitle.text = ctx.getString(R.string.desc_current_tiles, count)
            populateChips(holder.layoutCurrentChips, currentLabels)
            holder.btnSaveCurrent.setOnClickListener { onSaveCurrent() }
            holder.tvSnapshotCount.text = ctx.getString(R.string.format_snapshot_count, backups.size)
        } else if (holder is ItemViewHolder) {
            val item = backups[position - 1]
            val ctx = holder.itemView.context
            holder.tvSnapshotTitle.text = item.name
            val dateStr = dateFormat.format(Date(item.timestamp))
            holder.tvSnapshotSubtitle.text = ctx.getString(R.string.format_snapshot_subtitle, dateStr, item.count)
            populateChips(holder.layoutChips, item.previewLabels)

            holder.btnApply.setOnClickListener { onApply(item) }
            holder.btnOptions.setOnClickListener { v ->
                val popup = PopupMenu(ctx, v)
                popup.menu.add(0, 1, 0, R.string.action_copy)
                popup.menu.add(0, 2, 1, R.string.action_rename)
                popup.menu.add(0, 3, 2, R.string.action_delete)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> { onCopy(item); true }
                        2 -> { onRename(item); true }
                        3 -> { onDelete(item); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    private fun populateChips(container: LinearLayout, labels: List<String>) {
        container.removeAllViews()
        val ctx = container.context
        val maxChips = 15
        val shownLabels = labels.take(maxChips)
        val density = ctx.resources.displayMetrics.density

        for (label in shownLabels) {
            val tv = TextView(ctx).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(ctx, R.color.hyper_pill_grey_text))
                setBackgroundResource(R.drawable.bg_btn_pill_neutral)
                val padH = (9 * density).toInt()
                val padV = (3 * density).toInt()
                setPadding(padH, padV, padH, padV)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                layoutParams = lp
            }
            container.addView(tv)
        }
        if (labels.size > maxChips) {
            val more = TextView(ctx).apply {
                text = "+${labels.size - maxChips}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(ctx, R.color.hyper_text_secondary))
                setPadding((4 * density).toInt(), 0, (6 * density).toInt(), 0)
            }
            container.addView(more)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCurrentSubtitle: TextView = view.findViewById(R.id.tvCurrentSubtitle)
        val layoutCurrentChips: LinearLayout = view.findViewById(R.id.layoutCurrentChips)
        val btnSaveCurrent: TextView = view.findViewById(R.id.btnSaveCurrent)
        val tvSnapshotCount: TextView = view.findViewById(R.id.tvSnapshotCount)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSnapshotTitle: TextView = view.findViewById(R.id.tvSnapshotTitle)
        val tvSnapshotSubtitle: TextView = view.findViewById(R.id.tvSnapshotSubtitle)
        val layoutChips: LinearLayout = view.findViewById(R.id.layoutChips)
        val btnApply: TextView = view.findViewById(R.id.btnApply)
        val btnOptions: ImageView = view.findViewById(R.id.btnOptions)
    }
}
