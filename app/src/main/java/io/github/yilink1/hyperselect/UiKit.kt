package io.github.yilink1.hyperselect

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/** dp -> px（Round）。 */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Use dp() directly in custom Views. */
internal fun View.dp(value: Int): Int = context.dp(value)

/**
 * Unifiedof/thebottomPullMenu： panel +  text ，right 。
 * firstpageandThree pageShared， page  XML。
 */
internal fun Activity.showPopupMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_menu_popup)
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }
    val popup = PopupWindow(content, dp(188), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
        elevation = dp(16).toFloat()
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
    }

    val textColor = ContextCompat.getColor(this, R.color.hyper_text_primary)
    val sidePadding = dp(14)
    for ((label, action) in items) {
        val row = TextView(this)
        row.text = label
        row.textSize = 15f
        row.setTextColor(textColor)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(sidePadding, 0, sidePadding, 0)
        row.setBackgroundResource(R.drawable.bg_item_ripple)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            popup.dismiss()
            action()
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
    }

    popup.showAsDropDown(anchor, 0, dp(4), Gravity.END)
}

internal data class HelpFeatureItem(
    val iconRes: Int,
    val title: String,
    val subtitle: String
)

/**  Instructionspanel（ card ）。 */
internal fun Activity.showFeatureHelpSheet(title: String, items: List<HelpFeatureItem>) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_feature_help, null)
    val dialog = AlertDialog.Builder(this).setView(dialogView).create()

    dialogView.findViewById<TextView>(R.id.tvHelpTitle)?.text = title
    val container = dialogView.findViewById<LinearLayout>(R.id.llHelpItems)
    container?.removeAllViews()

    items.forEach { item ->
        val row = layoutInflater.inflate(R.layout.item_dialog_help_row, container, false)
        row.findViewById<ImageView>(R.id.ivHelpIcon).setImageResource(item.iconRes)
        row.findViewById<TextView>(R.id.tvHelpItemTitle).text = item.title
        row.findViewById<TextView>(R.id.tvHelpItemSub).text = item.subtitle
        container?.addView(row)
    }

    dialogView.findViewById<View>(R.id.btnHelpConfirm).setOnClickListener { dialog.dismiss() }

    dialog.show()
    dialog.window?.let { window ->
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0.45f)
        window.setGravity(Gravity.BOTTOM)
        window.setWindowAnimations(android.R.style.Animation_InputMethod)
        val margin = dp(16)
        window.setLayout(
            resources.displayMetrics.widthPixels - margin * 2,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val lp = window.attributes
        lp.y = margin
        window.attributes = lp
    }
}
