package io.github.yilink1.hyperselect

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TileBackupAdapter(
    private var currentTiles: String,
    private var backups: MutableList<TileBackupItem>,
    private val onOpenCurrent: () -> Unit,
    private val onOpen: (TileBackupItem) -> Unit,
    private val onLongPress: (TileBackupItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun update(tiles: String, newBackups: MutableList<TileBackupItem>) {
        currentTiles = tiles
        backups = newBackups
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_HEADER else TYPE_ITEM

    override fun getItemCount() = backups.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.header_tile_backup, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_tile_backup, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        if (holder is HeaderViewHolder) {
            val entries = TileVisuals.entries(ctx, currentTiles)
            holder.tvCurrentSubtitle.text = ctx.getString(R.string.format_tile_count, entries.size)
            TileVisuals.fillPanel(holder.panelMini, entries, tileDp = 40, maxRows = 2)
            holder.cardCurrent.setOnClickListener { onOpenCurrent() }
            holder.tvEmpty.visibility = if (backups.isEmpty()) View.VISIBLE else View.GONE
        } else if (holder is ItemViewHolder) {
            val index = position - 1
            val item = backups[index]
            val count = TileVisuals.splitSpecs(item.tiles).size
            val diff = TileVisuals.diff(ctx, item.tiles, currentTiles)
            holder.tvTitle.text = item.name
            holder.tvTag.visibility = if (diff.isSame) View.VISIBLE else View.GONE
            val diffText = if (diff.isSame) ctx.getString(R.string.row_diff_same)
            else ctx.getString(R.string.row_diff_count, diff.count)
            holder.tvSubtitle.text = listOf(
                dateFormat.format(Date(item.timestamp)),
                ctx.getString(R.string.format_tile_count, count),
                diffText
            ).joinToString(" · ")

            val first = index == 0
            val last = index == backups.size - 1
            holder.row.setBackgroundResource(
                when {
                    first && last -> R.drawable.bg_group_single
                    first -> R.drawable.bg_group_top
                    last -> R.drawable.bg_group_bottom
                    else -> R.drawable.bg_group_mid
                }
            )
            holder.divider.visibility = if (first) View.GONE else View.VISIBLE
            holder.row.setOnClickListener { onOpen(item) }
            holder.row.setOnLongClickListener { onLongPress(item, it); true }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardCurrent: LinearLayout = view.findViewById(R.id.cardCurrent)
        val tvCurrentSubtitle: TextView = view.findViewById(R.id.tvCurrentSubtitle)
        val panelMini: FrameLayout = view.findViewById(R.id.panelMini)
        val tvEmpty: TextView = view.findViewById(R.id.tvEmpty)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val row: LinearLayout = view.findViewById(R.id.row)
        val divider: View = view.findViewById(R.id.divider)
        val tvTitle: TextView = view.findViewById(R.id.tvSnapshotTitle)
        val tvTag: TextView = view.findViewById(R.id.tvTagCurrent)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSnapshotSubtitle)
    }
}