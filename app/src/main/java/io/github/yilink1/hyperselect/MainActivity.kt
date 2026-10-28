package io.github.yilink1.hyperselect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Text View
import androidx.appcompat.app.App Compat Activity
import androidx.core.view.View Compat
import androidx.core.view.Window Insets Compat

class Main Activity : App Compat Activity() {

    private lateinit var dot Status: View
    private lateinit var tv Status Title: Text View
    private lateinit var tv Text Action Badge: Text View
    private lateinit var tv Share Action Badge: Text View
    private lateinit var tv Open With Action Badge: Text View

    override fun on Create(saved Instance State: Bundle?) {
        super.on Create(saved Instance State)
        set Content View(R.layout.activity_main)

        View Compat.set On Apply Window Insets Listener(find View By Id(android.R.id.content)) { view, insets ->
            val system Bars = insets.get Insets(Window Insets Compat.Type.system Bars())
            view.set Padding(0, system Bars.top, 0, system Bars.bottom)
            insets
        }

        dot Status = find View By Id(R.id.dot Status)
        tv Status Title = find View By Id(R.id.tv Status Title)
        tv Text Action Badge = find View By Id(R.id.tv Text Action Badge)
        tv Share Action Badge = find View By Id(R.id.tv Share Action Badge)
        tv Open With Action Badge = find View By Id(R.id.tv Open With Action Badge)

        find View By Id<View>(R.id.card Text Action).set On Click Listener {
            start Activity(Intent(this, Text Action Activity::class.java))
        }
        find View By Id<View>(R.id.card Share Action).set On Click Listener {
            start Activity(Intent(this, Share Action Activity::class.java))
        }
        find View By Id<View>(R.id.card Open With Action).set On Click Listener {
            start Activity(Intent(this, Open With Action Activity::class.java))
        }
        find View By Id<View>(R.id.card Module Status).set On Click Listener {
            start Activity(Intent(this, Intro Activity::class.java))
        }
        find View By Id<View>(R.id.btn Menu).set On Click Listener { anchor ->
            show Popup Menu(
                anchor,
                list Of(
                    Pair("Feature Demo", { start Activity(Intent(this, Intro Activity::class.java)) }),
                    Pair("About Hyper Select", { start Activity(Intent(this, About Activity::class.java)) })
                )
            )
        }

        // Show tutorial on first install
        val sp = get Shared Preferences("hyper_select_prefs", MODE_PRIVATE)
        if (!sp.get Boolean("intro_shown", false)) {
            start Activity(Intent(this, Intro Activity::class.java))
        }
    }

    override fun on Resume() {
        super.on Resume()
        update Status And Counts()
    }

    private fun update Status And Counts() {
        if (Rule Store.is Module Active Since Boot(this)) {
            dot Status.set Background Resource(R.drawable.bg_status_circle_ok)
            tv Status Title.text = "Module is active"
        } else {
            dot Status.set Background Resource(R.drawable.bg_status_circle_fail)
            tv Status Title.text = "Module not active · Enable in LSPosed and reboot"
        }

        val config = Rule Store.load(this)
        tv Text Action Badge.text = hidden Label(config.blacklist.size)
        tv Share Action Badge.text = hidden Label(config.share Blacklist.size)
        tv Open With Action Badge.text = hidden Label(config.open With Blacklist.size)
    }

    private fun hidden Label(count: Int): String = if (count > 0) "Hidden $count" else "—"
}
