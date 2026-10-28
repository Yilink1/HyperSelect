package io.github.yilink1.hyperselect

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Color Drawable
import android.view.Gravity
import android.view.View
import android.view.View Group
import android.widget.Image View
import android.widget.Linear Layout
import android.widget.Popup Window
import android.widget.Text View
import androidx.appcompat.app.Alert Dialog
import androidx.core.content.Context Compat

/** dp -> px（Round）。 */
internal fun Context.dp(value: Int): Int = (value * resources.display Metrics.density).to Int()

/** Use dp() directly in custom Views. */
internal fun View.dp(value: Int): Int = context.dp(value)

/**
 * Unifiedof thebottom Pull Menu： panel +  text ，right 。
 * firstpageand Three page Shared， page  XML。
 */
internal fun Activity.show Popup Menu(anchor: View, items: List<Pair<String, () -> Unit>>) {
    val content = Linear Layout(this).apply {
        orientation = Linear Layout.VERTICAL
        set Background Resource(R.drawable.bg_menu_popup)
        set Padding(dp(6), dp(6), dp(6), dp(6))
    }
    val popup = Popup Window(content, dp(188), View Group.Layout Params.WRAP_CONTENT, true).apply {
        elevation = dp(16).to Float()
        set Background Drawable(Color Drawable(Color.TRANSPARENT))
        is Outside Touchable = true
    }

    val text Color = Context Compat.get Color(this, R.color.hyper_text_primary)
    val side Padding = dp(14)
    for ((label, action) in items) {
        val row = Text View(this)
        row.text = label
        row.text Size = 15f
        row.set Text Color(text Color)
        row.gravity = Gravity.CENTER_VERTICAL
        row.set Padding(side Padding, 0, side Padding, 0)
        row.set Background Resource(R.drawable.bg_item_ripple)
        row.is Clickable = true
        row.is Focusable = true
        row.set On Click Listener {
            popup.dismiss()
            action()
        }
        content.add View(row, Linear Layout.Layout Params(View Group.Layout Params.MATCH_PARENT, dp(46)))
    }

    popup.show As Drop Down(anchor, 0, dp(4), Gravity.END)
}

internal data class Help Feature Item(
    val icon Res: Int,
    val title: String,
    val subtitle: String
)

/**  Instructionspanel（ card ）。 */
internal fun Activity.show Feature Help Sheet(title: String, items: List<Help Feature Item>) {
    val dialog View = layout Inflater.inflate(R.layout.dialog_feature_help, null)
    val dialog = Alert Dialog.Builder(this).set View(dialog View).create()

    dialog View.find View By Id<Text View>(R.id.tv Help Title)?.text = title
    val container = dialog View.find View By Id<Linear Layout>(R.id.ll Help Items)
    container?.remove All Views()

    items.for Each { item ->
        val row = layout Inflater.inflate(R.layout.item_dialog_help_row, container, false)
        row.find View By Id<Image View>(R.id.iv Help Icon).set Image Resource(item.icon Res)
        row.find View By Id<Text View>(R.id.tv Help Item Title).text = item.title
        row.find View By Id<Text View>(R.id.tv Help Item Sub).text = item.subtitle
        container?.add View(row)
    }

    dialog View.find View By Id<View>(R.id.btn Help Confirm).set On Click Listener { dialog.dismiss() }

    dialog.show()
    dialog.window?.let { window ->
        window.set Background Drawable(Color Drawable(Color.TRANSPARENT))
        window.set Dim Amount(0.45f)
        window.set Gravity(Gravity.BOTTOM)
        window.set Window Animations(android.R.style.Animation_Input Method)
        val margin = dp(16)
        window.set Layout(
            resources.display Metrics.width Pixels - margin * 2,
            View Group.Layout Params.WRAP_CONTENT
        )
        val lp = window.attributes
        lp.y = margin
        window.attributes = lp
    }
}
