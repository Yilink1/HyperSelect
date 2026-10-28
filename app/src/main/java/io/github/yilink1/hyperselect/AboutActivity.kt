package io.github.yilink1.hyperselect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Color Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Menu Item
import android.view.View
import android.view.View Group
import android.widget.Text View
import androidx.appcompat.app.Alert Dialog
import androidx.appcompat.app.App Compat Activity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.View Compat
import androidx.core.view.Window Insets Compat

class About Activity : App Compat Activity() {

    override fun on Create(saved Instance State: Bundle?) {
        super.on Create(saved Instance State)
        set Content View(R.layout.activity_about)

        View Compat.set On Apply Window Insets Listener(find View By Id(android.R.id.content)) { view, insets ->
            val system Bars = insets.get Insets(Window Insets Compat.Type.system Bars())
            view.set Padding(0, system Bars.top, 0, system Bars.bottom)
            insets
        }

        val toolbar: Toolbar = find View By Id(R.id.toolbar About)
        set Support Action Bar(toolbar)
        support Action Bar?.apply {
            set Display Home As Up Enabled(true)
            title = "About"
        }

        val tv Version: Text View = find View By Id(R.id.tv About Version)
        try {
            val p Info = package Manager.get Package Info(package Name, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                p Info.long Version Code
            } else {
                p Info.version Code.to Long()
            }
            tv Version.text = "v${p Info.version Name} ($code)"
        } catch (_: Exception) {
            tv Version.text = "v1.0.0"
        }

        find View By Id<View>(R.id.btn About Help).set On Click Listener {
            show Help Dialog()
        }

        find View By Id<View>(R.id.btn About Github).set On Click Listener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yilink1/Hyper Select"))
            try {
                start Activity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun on Options Item Selected(item: Menu Item): Boolean {
        if (item.item Id == android.R.id.home) {
            finish()
            return true
        }
        return super.on Options Item Selected(item)
    }

    private fun show Help Dialog() {
        val dialog View = layout Inflater.inflate(R.layout.dialog_help, null)
        val dialog = Alert Dialog.Builder(this)
            .set View(dialog View)
            .create()

        dialog View.find View By Id<View>(R.id.btn Help Confirm).set On Click Listener {
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.let { window ->
            window.set Background Drawable(Color Drawable(Color.TRANSPARENT))
            window.set Dim Amount(0.45f)
            window.set Gravity(Gravity.BOTTOM)
            window.set Window Animations(android.R.style.Animation_Input Method)
            val margin H = (16f * resources.display Metrics.density).to Int()
            val margin V = (16f * resources.display Metrics.density).to Int()
            val display Width = resources.display Metrics.width Pixels
            window.set Layout(display Width - margin H * 2, View Group.Layout Params.WRAP_CONTENT)
            val lp = window.attributes
            lp.y = margin V
            window.attributes = lp
        }
    }
}
