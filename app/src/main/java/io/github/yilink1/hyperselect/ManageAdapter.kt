package io.github.yilink1.hyperselect

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TextActionItem(
    val label: String,
    val packageName: String,
    val componentName: String,
    val icon: Drawable,
    var isEnabled: Boolean
)

sealed class ManageRow {
    data class Header(val title: String, val count: Int, val hint: String? = null) : ManageRow()
    data class Pinned(val item: TextActionItem) : ManageRow()
    data class Flowing(val item: TextActionItem) : ManageRow()
    data class Showing(val item: TextActionItem) : ManageRow()
    data class Hidden(val item: TextActionItem) : ManageRow()
    data class Search(val item: TextActionItem, val isPinned: Boolean) : ManageRow()
}

/**
 * Physically tactile card + Smart search interaction list adapter。
 * With top dual-nav tabs, clearly shows 'Pinned', 'Flowing', and 'Hidden' lists.
 */
class ManageAdapter(
    private val onRestore: (item: TextActionItem) -> Unit,
    private val onPin: (item: TextActionItem) -> Unit,
    private val onUnpin: (item: TextActionItem) -> Unit,
    private val onStash: (item: TextActionItem) -> Unit,
    private val onStartDrag: (holder: RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PINNED = 1
        const val TYPE_FLOWING = 2
        const val TYPE_SHOWING = 3
        const val TYPE_HIDDEN = 4
        const val TYPE_SEARCH = 5
    }

    private var rows: List<ManageRow> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newRows: List<ManageRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ManageRow? = rows.getOrNull(position)

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ManageRow.Header -> TYPE_HEADER
        is ManageRow.Pinned -> TYPE_PINNED
        is ManageRow.Flowing -> TYPE_FLOWING
        is ManageRow.Showing -> TYPE_SHOWING
        is ManageRow.Hidden -> TYPE_HIDDEN
        is ManageRow.Search -> TYPE_SEARCH
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeaderTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
        val tvHeaderCount: TextView = view.findViewById(R.id.tvHeaderCount)
        val tvHeaderHint: TextView = view.findViewById(R.id.tvHeaderHint)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppLabel: TextView = view.findViewById(R.id.tvAppLabel)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val btnUnpin: TextView = view.findViewById(R.id.btnUnpin)
        val btnPin: TextView = view.findViewById(R.id.btnPin)
        val btnRestore: ImageView = view.findViewById(R.id.btnRestore)
        val layoutSearchActions: View = view.findViewById(R.id.layoutSearchActions)
        val btnSearchPin: TextView = view.findViewById(R.id.btnSearchPin)
        val btnSearchHide: TextView = view.findViewById(R.id.btnSearchHide)
        val btnSearchRestore: TextView = view.findViewById(R.id.btnSearchRestore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_manage_header, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_manage_row, parent, false))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            val header = rows[position] as? ManageRow.Header ?: return
            holder.tvHeaderTitle.text = header.title
            holder.tvHeaderCount.text = "${header.count}"
            if (!header.hint.isNullOrBlank()) {
                holder.tvHeaderHint.text = header.hint
                holder.tvHeaderHint.visibility = View.VISIBLE
            } else {
                holder.tvHeaderHint.visibility = View.GONE
            }
            return
        }

        val itemHolder = holder as? ItemViewHolder ?: return

        // Reset animation offsets, scale and z-index to avoid reuse artifacts
        itemHolder.itemView.animate().cancel()
        itemHolder.itemView.translationX = 0f
        itemHolder.itemView.translationZ = 0f
        itemHolder.itemView.scaleX = 1f
        itemHolder.itemView.scaleY = 1f

        when (val row = rows[position]) {
            is ManageRow.Header -> {}

            is ManageRow.Pinned -> {
                val item = row.item
                itemHolder.itemView.alpha = 1.0f
                itemHolder.ivAppIcon.setImageDrawable(item.icon)
                itemHolder.tvAppLabel.text = item.label
                itemHolder.tvPackageName.text = item.packageName

                itemHolder.ivDragHandle.visibility = View.VISIBLE
                itemHolder.btnUnpin.visibility = View.VISIBLE
                itemHolder.btnPin.visibility = View.GONE
                itemHolder.btnRestore.visibility = View.GONE
                itemHolder.layoutSearchActions.visibility = View.GONE

                itemHolder.btnUnpin.setOnClickListener { onUnpin(item) }
                itemHolder.ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(itemHolder)
                    }
                    false
                }
                itemHolder.itemView.setOnClickListener(null)
            }

            is ManageRow.Flowing -> {
                val item = row.item
                itemHolder.itemView.alpha = 1.0f
                itemHolder.ivAppIcon.setImageDrawable(item.icon)
                itemHolder.tvAppLabel.text = item.label
                itemHolder.tvPackageName.text = item.packageName

                itemHolder.ivDragHandle.visibility = View.GONE
                itemHolder.btnUnpin.visibility = View.GONE
                itemHolder.btnPin.visibility = View.VISIBLE
                itemHolder.btnRestore.visibility = View.GONE
                itemHolder.layoutSearchActions.visibility = View.GONE

                itemHolder.btnPin.setOnClickListener { onPin(item) }
                itemHolder.itemView.setOnClickListener(null)
            }

            is ManageRow.Showing -> {
                val item = row.item
                itemHolder.itemView.alpha = 1.0f
                itemHolder.ivAppIcon.setImageDrawable(item.icon)
                itemHolder.tvAppLabel.text = item.label
                itemHolder.tvPackageName.text = item.packageName

                itemHolder.ivDragHandle.visibility = View.VISIBLE
                itemHolder.btnUnpin.visibility = View.GONE
                itemHolder.btnPin.visibility = View.GONE
                itemHolder.btnRestore.visibility = View.GONE
                itemHolder.layoutSearchActions.visibility = View.GONE

                itemHolder.ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(itemHolder)
                    }
                    false
                }
                itemHolder.itemView.setOnClickListener(null)
            }

            is ManageRow.Hidden -> {
                val item = row.item
                itemHolder.itemView.alpha = 0.75f
                itemHolder.ivAppIcon.setImageDrawable(item.icon)
                itemHolder.tvAppLabel.text = item.label
                itemHolder.tvPackageName.text = item.packageName

                itemHolder.ivDragHandle.visibility = View.GONE
                itemHolder.btnUnpin.visibility = View.GONE
                itemHolder.btnPin.visibility = View.GONE
                itemHolder.btnRestore.visibility = View.VISIBLE
                itemHolder.layoutSearchActions.visibility = View.GONE

                val restoreAction = View.OnClickListener { onRestore(item) }
                itemHolder.btnRestore.setOnClickListener(restoreAction)
                itemHolder.itemView.setOnClickListener(restoreAction)
            }

            is ManageRow.Search -> {
                val item = row.item
                itemHolder.ivAppIcon.setImageDrawable(item.icon)
                itemHolder.tvAppLabel.text = item.label
                itemHolder.tvPackageName.text = item.packageName

                itemHolder.ivDragHandle.visibility = View.GONE
                itemHolder.btnUnpin.visibility = View.GONE
                itemHolder.btnPin.visibility = View.GONE
                itemHolder.btnRestore.visibility = View.GONE
                itemHolder.layoutSearchActions.visibility = View.VISIBLE

                if (item.isEnabled) {
                    itemHolder.itemView.alpha = 1.0f
                    itemHolder.btnSearchPin.visibility = View.VISIBLE
                    itemHolder.btnSearchHide.visibility = View.VISIBLE
                    itemHolder.btnSearchRestore.visibility = View.GONE

                    if (row.isPinned) {
                        itemHolder.btnSearchPin.text = "CancelPin"
                        itemHolder.btnSearchPin.setOnClickListener { onUnpin(item) }
                    } else {
                        itemHolder.btnSearchPin.text = "Pin"
                        itemHolder.btnSearchPin.setOnClickListener { onPin(item) }
                    }

                    itemHolder.btnSearchHide.setOnClickListener { onStash(item) }
                    itemHolder.itemView.setOnClickListener(null)
                } else {
                    itemHolder.itemView.alpha = 0.75f
                    itemHolder.btnSearchPin.visibility = View.GONE
                    itemHolder.btnSearchHide.visibility = View.GONE
                    itemHolder.btnSearchRestore.visibility = View.VISIBLE

                    val restoreAction = View.OnClickListener { onRestore(item) }
                    itemHolder.btnSearchRestore.setOnClickListener(restoreAction)
                    itemHolder.itemView.setOnClickListener(restoreAction)
                }
            }
        }
    }
}
