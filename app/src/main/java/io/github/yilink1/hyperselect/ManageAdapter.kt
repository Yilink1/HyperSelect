package io.github.yilink1.hyperselect

import android.annotation.Suppress Lint
import android.graphics.drawable.Drawable
import android.view.Layout Inflater
import android.view.Motion Event
import android.view.View
import android.view.View Group
import android.widget.Image View
import android.widget.Text View
import androidx.recyclerview.widget.Recycler View

data class Text Action Item(
    val label: String,
    val package Name: String,
    val component Name: String,
    val icon: Drawable,
    var is Enabled: Boolean
)

sealed class Manage Row {
    data class Header(val title: String, val count: Int, val hint: String? = null) : Manage Row()
    data class Pinned(val item: Text Action Item) : Manage Row()
    data class Flowing(val item: Text Action Item) : Manage Row()
    data class Showing(val item: Text Action Item) : Manage Row()
    data class Hidden(val item: Text Action Item) : Manage Row()
    data class Search(val item: Text Action Item, val is Pinned: Boolean) : Manage Row()
}

/**
 * Physically tactile card + Smart search interaction list adapter。
 * With top dual-nav tabs, clearly shows 'Pinned', 'Flowing', and 'Hidden' lists.
 */
class Manage Adapter(
    private val on Restore: (item: Text Action Item) -> Unit,
    private val on Pin: (item: Text Action Item) -> Unit,
    private val on Unpin: (item: Text Action Item) -> Unit,
    private val on Stash: (item: Text Action Item) -> Unit,
    private val on Start Drag: (holder: Recycler View.View Holder) -> Unit
) : Recycler View.Adapter<Recycler View.View Holder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PINNED = 1
        const val TYPE_FLOWING = 2
        const val TYPE_SHOWING = 3
        const val TYPE_HIDDEN = 4
        const val TYPE_SEARCH = 5
    }

    private var rows: List<Manage Row> = empty List()

    @Suppress Lint("Notify Data Set Changed")
    fun submit(new Rows: List<Manage Row>) {
        rows = new Rows
        notify Data Set Changed()
    }

    fun get Item(position: Int): Manage Row? = rows.get Or Null(position)

    override fun get Item Count(): Int = rows.size

    override fun get Item View Type(position: Int): Int = when (rows[position]) {
        is Manage Row.Header -> TYPE_HEADER
        is Manage Row.Pinned -> TYPE_PINNED
        is Manage Row.Flowing -> TYPE_FLOWING
        is Manage Row.Showing -> TYPE_SHOWING
        is Manage Row.Hidden -> TYPE_HIDDEN
        is Manage Row.Search -> TYPE_SEARCH
    }

    class Header View Holder(view: View) : Recycler View.View Holder(view) {
        val tv Header Title: Text View = view.find View By Id(R.id.tv Header Title)
        val tv Header Count: Text View = view.find View By Id(R.id.tv Header Count)
        val tv Header Hint: Text View = view.find View By Id(R.id.tv Header Hint)
    }

    class Item View Holder(view: View) : Recycler View.View Holder(view) {
        val iv App Icon: Image View = view.find View By Id(R.id.iv App Icon)
        val tv App Label: Text View = view.find View By Id(R.id.tv App Label)
        val tv Package Name: Text View = view.find View By Id(R.id.tv Package Name)
        val iv Drag Handle: Image View = view.find View By Id(R.id.iv Drag Handle)
        val btn Unpin: Text View = view.find View By Id(R.id.btn Unpin)
        val btn Pin: Text View = view.find View By Id(R.id.btn Pin)
        val btn Restore: Image View = view.find View By Id(R.id.btn Restore)
        val layout Search Actions: View = view.find View By Id(R.id.layout Search Actions)
        val btn Search Pin: Text View = view.find View By Id(R.id.btn Search Pin)
        val btn Search Hide: Text View = view.find View By Id(R.id.btn Search Hide)
        val btn Search Restore: Text View = view.find View By Id(R.id.btn Search Restore)
    }

    override fun on Create View Holder(parent: View Group, view Type: Int): Recycler View.View Holder {
        val inflater = Layout Inflater.from(parent.context)
        return if (view Type == TYPE_HEADER) {
            Header View Holder(inflater.inflate(R.layout.item_manage_header, parent, false))
        } else {
            Item View Holder(inflater.inflate(R.layout.item_manage_row, parent, false))
        }
    }

    @Suppress Lint("Clickable View Accessibility")
    override fun on Bind View Holder(holder: Recycler View.View Holder, position: Int) {
        if (holder is Header View Holder) {
            val header = rows[position] as? Manage Row.Header ?: return
            holder.tv Header Title.text = header.title
            holder.tv Header Count.text = "${header.count}"
            if (!header.hint.is Null Or Blank()) {
                holder.tv Header Hint.text = header.hint
                holder.tv Header Hint.visibility = View.VISIBLE
            } else {
                holder.tv Header Hint.visibility = View.GONE
            }
            return
        }

        val item Holder = holder as? Item View Holder ?: return

        // Reset animation offsets, scale and z-index to avoid reuse artifacts
        item Holder.item View.animate().cancel()
        item Holder.item View.translation X = 0f
        item Holder.item View.translation Z = 0f
        item Holder.item View.scale X = 1f
        item Holder.item View.scale Y = 1f

        when (val row = rows[position]) {
            is Manage Row.Header -> {}

            is Manage Row.Pinned -> {
                val item = row.item
                item Holder.item View.alpha = 1.0f
                item Holder.iv App Icon.set Image Drawable(item.icon)
                item Holder.tv App Label.text = item.label
                item Holder.tv Package Name.text = item.package Name

                item Holder.iv Drag Handle.visibility = View.VISIBLE
                item Holder.btn Unpin.visibility = View.VISIBLE
                item Holder.btn Pin.visibility = View.GONE
                item Holder.btn Restore.visibility = View.GONE
                item Holder.layout Search Actions.visibility = View.GONE

                item Holder.btn Unpin.set On Click Listener { on Unpin(item) }
                item Holder.iv Drag Handle.set On Touch Listener { _, event ->
                    if (event.action Masked == Motion Event.ACTION_DOWN) {
                        on Start Drag(item Holder)
                    }
                    false
                }
                item Holder.item View.set On Click Listener(null)
            }

            is Manage Row.Flowing -> {
                val item = row.item
                item Holder.item View.alpha = 1.0f
                item Holder.iv App Icon.set Image Drawable(item.icon)
                item Holder.tv App Label.text = item.label
                item Holder.tv Package Name.text = item.package Name

                item Holder.iv Drag Handle.visibility = View.GONE
                item Holder.btn Unpin.visibility = View.GONE
                item Holder.btn Pin.visibility = View.VISIBLE
                item Holder.btn Restore.visibility = View.GONE
                item Holder.layout Search Actions.visibility = View.GONE

                item Holder.btn Pin.set On Click Listener { on Pin(item) }
                item Holder.item View.set On Click Listener(null)
            }

            is Manage Row.Showing -> {
                val item = row.item
                item Holder.item View.alpha = 1.0f
                item Holder.iv App Icon.set Image Drawable(item.icon)
                item Holder.tv App Label.text = item.label
                item Holder.tv Package Name.text = item.package Name

                item Holder.iv Drag Handle.visibility = View.VISIBLE
                item Holder.btn Unpin.visibility = View.GONE
                item Holder.btn Pin.visibility = View.GONE
                item Holder.btn Restore.visibility = View.GONE
                item Holder.layout Search Actions.visibility = View.GONE

                item Holder.iv Drag Handle.set On Touch Listener { _, event ->
                    if (event.action Masked == Motion Event.ACTION_DOWN) {
                        on Start Drag(item Holder)
                    }
                    false
                }
                item Holder.item View.set On Click Listener(null)
            }

            is Manage Row.Hidden -> {
                val item = row.item
                item Holder.item View.alpha = 0.75f
                item Holder.iv App Icon.set Image Drawable(item.icon)
                item Holder.tv App Label.text = item.label
                item Holder.tv Package Name.text = item.package Name

                item Holder.iv Drag Handle.visibility = View.GONE
                item Holder.btn Unpin.visibility = View.GONE
                item Holder.btn Pin.visibility = View.GONE
                item Holder.btn Restore.visibility = View.VISIBLE
                item Holder.layout Search Actions.visibility = View.GONE

                val restore Action = View.On Click Listener { on Restore(item) }
                item Holder.btn Restore.set On Click Listener(restore Action)
                item Holder.item View.set On Click Listener(restore Action)
            }

            is Manage Row.Search -> {
                val item = row.item
                item Holder.iv App Icon.set Image Drawable(item.icon)
                item Holder.tv App Label.text = item.label
                item Holder.tv Package Name.text = item.package Name

                item Holder.iv Drag Handle.visibility = View.GONE
                item Holder.btn Unpin.visibility = View.GONE
                item Holder.btn Pin.visibility = View.GONE
                item Holder.btn Restore.visibility = View.GONE
                item Holder.layout Search Actions.visibility = View.VISIBLE

                if (item.is Enabled) {
                    item Holder.item View.alpha = 1.0f
                    item Holder.btn Search Pin.visibility = View.VISIBLE
                    item Holder.btn Search Hide.visibility = View.VISIBLE
                    item Holder.btn Search Restore.visibility = View.GONE

                    if (row.is Pinned) {
                        item Holder.btn Search Pin.text = "Cancel Pin"
                        item Holder.btn Search Pin.set On Click Listener { on Unpin(item) }
                    } else {
                        item Holder.btn Search Pin.text = "Pin"
                        item Holder.btn Search Pin.set On Click Listener { on Pin(item) }
                    }

                    item Holder.btn Search Hide.set On Click Listener { on Stash(item) }
                    item Holder.item View.set On Click Listener(null)
                } else {
                    item Holder.item View.alpha = 0.75f
                    item Holder.btn Search Pin.visibility = View.GONE
                    item Holder.btn Search Hide.visibility = View.GONE
                    item Holder.btn Search Restore.visibility = View.VISIBLE

                    val restore Action = View.On Click Listener { on Restore(item) }
                    item Holder.btn Search Restore.set On Click Listener(restore Action)
                    item Holder.item View.set On Click Listener(restore Action)
                }
            }
        }
    }
}
