package io.github.yilink1.hyperselect

import android.animation.Value Animator
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Layout Inflater
import android.view.View
import android.view.View Group
import android.view.animation.Linear Interpolator
import android.view.animation.Overshoot Interpolator
import android.widget.Button
import android.widget.Frame Layout
import android.widget.Image View
import android.widget.Linear Layout
import android.widget.Text View
import androidx.appcompat.app.App Compat Activity
import androidx.core.view.View Compat
import androidx.core.view.Window Insets Compat
import androidx.recyclerview.widget.Recycler View
import androidx.viewpager2.widget.View Pager2

class Intro Activity : App Compat Activity() {

    private enum class Module Status { CHECKING, ACTIVE, INACTIVE }

    private lateinit var view Pager: View Pager2
    private lateinit var dot Active Pill: View
    private lateinit var btn Action: Button

    private var animator Page1: Value Animator? = null
    private var animator Page2: Value Animator? = null
    private var pulse Animator: Value Animator? = null

    private var status View: View? = null
    private var module Status = Module Status.CHECKING
    private var poll Attempts = 0
    private val status Handler = Handler(Looper.get Main Looper())

    // Polling: notify Change wakes Content Observer in system_server to reread rules, 
    // as long as hook is alive it refreshes receipt; if Module not active/rebooted it never reads.
    private val poll Runnable = object : Runnable {
        override fun run() {
            if (module Status == Module Status.ACTIVE) return
            try {
                content Resolver.notify Change(Config Provider.CONTENT_URI, null)
            } catch (_: Exception) {}
            poll Attempts++
            when {
                Rule Store.is Module Active Since Boot(this@Intro Activity) ->
                    set Module Status(Module Status.ACTIVE)
                poll Attempts >= 4 && module Status == Module Status.CHECKING ->
                    set Module Status(Module Status.INACTIVE)
            }
            if (module Status == Module Status.CHECKING) {
                status Handler.post Delayed(this, 1_000L)
            }
        }
    }

    override fun on Create(saved Instance State: Bundle?) {
        super.on Create(saved Instance State)
        set Content View(R.layout.activity_intro)

        // Directly check if Module is active on boot; if yes show checkmark immediately without fake detection
        if (Rule Store.is Module Active Since Boot(this)) {
            module Status = Module Status.ACTIVE
        }

        // Handle edge-to-edge window insets
        View Compat.set On Apply Window Insets Listener(find View By Id(R.id.intro Root)) { view, insets ->
            val system Bars = insets.get Insets(Window Insets Compat.Type.system Bars())
            view.set Padding(0, system Bars.top, 0, system Bars.bottom)
            insets
        }

        view Pager = find View By Id(R.id.view Pager Intro)
        dot Active Pill = find View By Id(R.id.dot Active Pill)
        btn Action = find View By Id(R.id.btn Intro Action)

        view Pager.adapter = Intro Adapter()

        view Pager.register On Page Change Callback(object : View Pager2.On Page Change Callback() {
            override fun on Page Scrolled(position: Int, position Offset: Float, position Offset Pixels: Int) {
                update Indicator Scroll(position, position Offset)
            }

            override fun on Page Selected(position: Int) {
                update Action Btn Text(position)
                if (position == 2) start Polling() else stop Polling()
            }
        })

        btn Action.set On Click Listener {
            when (view Pager.current Item) {
                0 -> view Pager.set Current Item(1, true)
                1 -> view Pager.set Current Item(2, true)
                else -> {
                    if (module Status == Module Status.ACTIVE) {
                        finish Intro()
                    } else {
                        // Manual re-detect
                        poll Attempts = 0
                        set Module Status(Module Status.CHECKING)
                        start Polling()
                    }
                }
            }
        }

        update Indicator Scroll(0, 0f)
        update Action Btn Text(0)
    }

    // Dynamic elastic indicator: stationary 6.5dp dot, stretched to flat bar on swipe, avoids scale X causing bulging/ellipse
    private fun update Indicator Scroll(position: Int, position Offset: Float) {
        val density = resources.display Metrics.density
        val base Size = (6.5f * density).to Int()
        val dot Gap = 8f * density
        val step = 6.5f * density + dot Gap

        val head Offset = (position Offset * 2f).coerce At Most(1f)
        val tail Offset = ((position Offset - 0.5f) * 2f).coerce At Least(0f)

        val left = (position + tail Offset) * step
        val right = (position * step + base Size) + (head Offset * step)
        val target Width = (right - left).to Int().coerce At Least(base Size)

        val lp = dot Active Pill.layout Params
        if (lp.width != target Width) {
            lp.width = target Width
            dot Active Pill.layout Params = lp
        }
        dot Active Pill.translation X = left
    }

    private fun update Action Btn Text(position: Int) {
        btn Action.text = when {
            position < 2 -> "Continue"
            module Status == Module Status.ACTIVE -> "Start clean experience"
            else -> "Re-detect"
        }
    }

    // ---------- Module self-check ----------

    private fun start Polling() {
        stop Polling()
        if (Rule Store.is Module Active Since Boot(this)) {
            set Module Status(Module Status.ACTIVE)
            return
        }
        poll Attempts = 0
        set Module Status(Module Status.CHECKING)
        try {
            content Resolver.notify Change(Config Provider.CONTENT_URI, null)
        } catch (_: Exception) {}
        status Handler.post(poll Runnable)
    }

    private fun stop Polling() {
        status Handler.remove Callbacks(poll Runnable)
    }

    private fun set Module Status(status: Module Status) {
        if (module Status == status) return
        module Status = status
        apply Status Ui()
        update Action Btn Text(view Pager.current Item)
        if (status == Module Status.ACTIVE) {
            stop Polling()
            status View?.find View By Id<Text View>(R.id.tv Status Icon)?.let { icon ->
                icon.scale X = 0.4f
                icon.scale Y = 0.4f
                icon.animate().scale X(1f).scale Y(1f)
                    .set Duration(450)
                    .set Interpolator(Overshoot Interpolator(2.5f))
                    .start()
            }
        }
    }

    private fun apply Status Ui() {
        val v = status View ?: return
        val icon = v.find View By Id<Text View>(R.id.tv Status Icon)
        val title = v.find View By Id<Text View>(R.id.tv Status Title)
        val desc = v.find View By Id<Text View>(R.id.tv Status Desc)
        when (module Status) {
            Module Status.CHECKING -> {
                icon.text = "…"
                icon.set Background Resource(R.drawable.bg_status_circle_checking)
                title.text = "Checking module status"
                desc.text = "Handshaking with system framework, please wait..."
                start Pulse(icon)
            }
            Module Status.INACTIVE -> {
                stop Pulse()
                icon.alpha = 1f
                icon.text = "!"
                icon.set Background Resource(R.drawable.bg_status_circle_fail)
                title.text = "Module not active"
                desc.text = "Follow these steps:\n\n1. Open LSPosed Manager → Modules → Enable Hyper Select\n2. Reboot your phone (the module hooks the system framework, so a reboot is required)\n3. After rebooting, return to this page to auto-detect"
            }
            Module Status.ACTIVE -> {
                stop Pulse()
                icon.alpha = 1f
                icon.text = "✓"
                icon.set Background Resource(R.drawable.bg_status_circle_ok)
                title.text = "Module is active"
                desc.text = "Ready, all Settings take effect immediately"
            }
        }
    }

    private fun start Pulse(target: View) {
        if (pulse Animator?.is Running == true) return
        pulse Animator = Value Animator.of Float(0.55f, 1f).apply {
            duration = 700L
            repeat Mode = Value Animator.REVERSE
            repeat Count = Value Animator.INFINITE
            add Update Listener { target.alpha = it.animated Value as Float }
        }
        pulse Animator?.start()
    }

    private fun stop Pulse() {
        pulse Animator?.cancel()
    }

    private fun finish Intro() {
        val sp = get Shared Preferences("hyper_select_prefs", MODE_PRIVATE)
        sp.edit().put Boolean("intro_shown", true).apply()
        finish()
    }

    override fun on Destroy() {
        super.on Destroy()
        animator Page1?.cancel()
        animator Page2?.cancel()
        pulse Animator?.cancel()
        stop Polling()
    }

    // View Pager2 Adapter for the 3 Walkthrough Pages
    private inner class Intro Adapter : Recycler View.Adapter<Recycler View.View Holder>() {

        override fun get Item Count(): Int = 3

        override fun get Item View Type(position: Int): Int = position

        override fun on Create View Holder(parent: View Group, view Type: Int): Recycler View.View Holder {
            val layout Id = when (view Type) {
                0 -> R.layout.item_intro_page1
                1 -> R.layout.item_intro_page2
                else -> R.layout.item_intro_page3
            }
            val view = Layout Inflater.from(parent.context).inflate(layout Id, parent, false)
            return object : Recycler View.View Holder(view) {}
        }

        override fun on Bind View Holder(holder: Recycler View.View Holder, position: Int) {
            when (position) {
                0 -> setup Page1Animation(holder.item View)
                1 -> setup Page2Animation(holder.item View)
                else -> {
                    status View = holder.item View
                    apply Status Ui()
                }
            }
        }
    }

    // ---------- Animation utils ----------

    private fun ease Out(p: Float): Float {
        val q = 1f - p
        return 1f - q * q * q
    }

    // STEP 1: AI Writing icon collapse -> Paste slides out like drawer (2.8s loop)
    private fun setup Page1Animation(view: View) {
        val iv Ai Icon = view.find View By Id<Image View>(R.id.iv Page1Ai Icon) ?: return
        val drawer = view.find View By Id<Frame Layout>(R.id.drawer Page1Paste) ?: return
        val tv Paste = view.find View By Id<Text View>(R.id.tv Page1Paste) ?: return

        val is Night Mode = (resources.configuration.ui Mode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (is Night Mode) {
            iv Ai Icon.set Image Resource(R.drawable.ic_xiaomi_ai_extracted_hd)
        } else {
            iv Ai Icon.set Image Resource(R.drawable.ic_xiaomi_ai_light_retina)
        }

        val density = resources.display Metrics.density
        val ai Icon Target Width = (24f * density).to Int()
        val ai Icon Target Margin = (10f * density).to Int()

        animator Page1?.cancel()
        view.post {
            tv Paste.measure(
                View.Measure Spec.make Measure Spec(0, View.Measure Spec.UNSPECIFIED),
                View.Measure Spec.make Measure Spec(0, View.Measure Spec.UNSPECIFIED)
            )
            val full Paste Width = tv Paste.measured Width
            val clip H = (48f * density).to Int()

            // Lock tv Paste to full physical width to prevent Android text engine from dropping chars
            tv Paste.layout Params = Frame Layout.Layout Params(full Paste Width, View Group.Layout Params.MATCH_PARENT)
            tv Paste.translation X = 0f

            animator Page1 = Value Animator.of Float(0f, 1f).apply {
                duration = 2_800L
                repeat Count = Value Animator.INFINITE
                interpolator = Linear Interpolator()
                add Update Listener { anim ->
                    val f = anim.animated Fraction
                    when {
                        // 1. Initial state: AI icon shown, drawer fully closed (0% - 20%)
                        f <= 0.20f -> {
                            iv Ai Icon.scale X = 1f
                            iv Ai Icon.scale Y = 1f
                            iv Ai Icon.rotation = 0f
                            iv Ai Icon.alpha = 1f
                            val lp Icon = iv Ai Icon.layout Params as Linear Layout.Layout Params
                            lp Icon.width = ai Icon Target Width
                            lp Icon.margin End = ai Icon Target Margin
                            iv Ai Icon.layout Params = lp Icon

                            val lp Drawer = drawer.layout Params as Linear Layout.Layout Params
                            lp Drawer.width = 0
                            drawer.layout Params = lp Drawer
                            drawer.clip Bounds = Rect(0, 0, 0, clip H)
                        }
                        // 2. AI icon subtle bounce/rotate (20% - 30%)
                        f <= 0.30f -> {
                            val p = (f - 0.20f) / 0.10f
                            iv Ai Icon.scale X = 1f + 0.18f * p
                            iv Ai Icon.scale Y = 1f + 0.18f * p
                            iv Ai Icon.rotation = 45f * p
                            iv Ai Icon.alpha = 1f

                            val lp Drawer = drawer.layout Params as Linear Layout.Layout Params
                            lp Drawer.width = 0
                            drawer.layout Params = lp Drawer
                            drawer.clip Bounds = Rect(0, 0, 0, clip H)
                        }
                        // 3. AI icon shrinks/fades, drawer opens to reveal text (smooth stroke animation)(30% - 50%)
                        f <= 0.50f -> {
                            val p = (f - 0.30f) / 0.20f
                            val smooth P = 1f - (1f - p) * (1f - p) * (1f - p)

                            iv Ai Icon.scale X = (1.18f * (1f - smooth P)).coerce At Least(0f)
                            iv Ai Icon.scale Y = (1.18f * (1f - smooth P)).coerce At Least(0f)
                            iv Ai Icon.rotation = 45f + 135f * smooth P
                            iv Ai Icon.alpha = (1f - smooth P * 1.2f).coerce In(0f, 1f)

                            val lp Icon = iv Ai Icon.layout Params as Linear Layout.Layout Params
                            lp Icon.width = (ai Icon Target Width * (1f - smooth P)).to Int()
                            lp Icon.margin End = (ai Icon Target Margin * (1f - smooth P)).to Int()
                            iv Ai Icon.layout Params = lp Icon

                            val current Width = (full Paste Width * smooth P).to Int()
                            val lp Drawer = drawer.layout Params as Linear Layout.Layout Params
                            lp Drawer.width = current Width
                            drawer.layout Params = lp Drawer
                            drawer.clip Bounds = Rect(0, 0, current Width, clip H)
                        }
                        // 4. Clean Status Keep visible (50% - 82%)
                        f <= 0.82f -> {
                            val lp Icon = iv Ai Icon.layout Params as Linear Layout.Layout Params
                            lp Icon.width = 0
                            lp Icon.margin End = 0
                            iv Ai Icon.layout Params = lp Icon
                            iv Ai Icon.alpha = 0f

                            val lp Drawer = drawer.layout Params as Linear Layout.Layout Params
                            lp Drawer.width = full Paste Width
                            drawer.layout Params = lp Drawer
                            drawer.clip Bounds = null
                        }
                        // 5. Smooth reset: close drawer, prepare for next cycle (82% - 100%)
                        else -> {
                            val p = (f - 0.82f) / 0.18f
                            val current Width = (full Paste Width * (1f - p)).to Int()
                            val lp Drawer = drawer.layout Params as Linear Layout.Layout Params
                            lp Drawer.width = current Width
                            drawer.layout Params = lp Drawer
                            drawer.clip Bounds = Rect(0, 0, current Width, clip H)

                            val lp Icon = iv Ai Icon.layout Params as Linear Layout.Layout Params
                            lp Icon.width = (ai Icon Target Width * p).to Int()
                            lp Icon.margin End = (ai Icon Target Margin * p).to Int()
                            iv Ai Icon.layout Params = lp Icon
                            iv Ai Icon.scale X = p
                            iv Ai Icon.scale Y = p
                            iv Ai Icon.alpha = p
                            iv Ai Icon.rotation = 180f + 180f * p
                        }
                    }
                }
            }
            animator Page1?.start()
        }
    }

    // STEP 2: Highlight pinned items at top, others shift down (no text overlap) -> Shareleft Hide（Cycle 3.8s）
    private fun setup Page2Animation(view: View) {
        val card = view.find View By Id<View>(R.id.card Page2Menu)
        val paste = view.find View By Id<Text View>(R.id.tv Page2Paste)
        val share = view.find View By Id<Text View>(R.id.tv Page2Share)
        val translate = view.find View By Id<Text View>(R.id.tv Page2Translate)
        val baidu = view.find View By Id<Text View>(R.id.tv Page2Baidu)
        val deep Seek = view.find View By Id<Text View>(R.id.tv Page2Deep Seek)

        val density = resources.display Metrics.density
        var highlighted = false

        animator Page2?.cancel()
        view.post {
            val row H = paste.height
            if (row H <= 0) return@post
            animator Page2 = Value Animator.of Float(0f, 1f).apply {
                duration = 3_800L
                repeat Count = Value Animator.INFINITE
                interpolator = Linear Interpolator()
                add Update Listener { anim ->
                    val f = anim.animated Fraction
                    when {
                        // 1. Menu  (0-8%)
                        f <= 0.08f -> {
                            val p = ease Out(f / 0.08f)
                            card.alpha = p
                            card.scale X = 0.85f + 0.15f * p
                            card.scale Y = 0.85f + 0.15f * p
                            reset Page2Rows(paste, share, translate, baidu, deep Seek)
                            if (highlighted) {
                                deep Seek.background = null
                                highlighted = false
                            }
                        }
                        // 2.  order Stay (8-26%)
                        f <= 0.26f -> {
                            card.alpha = 1f
                            card.scale X = 1f
                            card.scale Y = 1f
                            reset Page2Rows(paste, share, translate, baidu, deep Seek)
                        }
                        // 3. Deep Seek  top Top， when  4 row bottom  1  ， nonetext ！(26-48%)
                        f <= 0.48f -> {
                            if (!highlighted) {
                                deep Seek.set Background Resource(R.drawable.bg_menu_item_highlight)
                                deep Seek.background?.mutate()?.alpha = 255
                                highlighted = true
                            }
                            val p = ease Out((f - 0.26f) / 0.22f)
                            deep Seek.translation Y = -4f * row H * p
                            deep Seek.translation Z = 8f * density * p
                            deep Seek.scale X = 1f + 0.03f * p
                            deep Seek.scale Y = 1f + 0.03f * p

                            paste.translation Y = row H * p
                            share.translation Y = row H * p
                            translate.translation Y = row H * p
                            baidu.translation Y = row H * p
                        }
                        // 4.  ：Deep Seek  ，  (48-56%)
                        f <= 0.56f -> {
                            val p = ease Out((f - 0.48f) / 0.08f)
                            deep Seek.translation Y = -4f * row H
                            deep Seek.translation Z = 8f * density * (1f - p)
                            deep Seek.scale X = 1f + 0.03f * (1f - p)
                            deep Seek.scale Y = 1f + 0.03f * (1f - p)
                            if (highlighted) {
                                deep Seek.background?.mutate()?.alpha = ((1f - p) * 255).to Int().coerce In(0, 255)
                            }
                            paste.translation Y = row H.to Float()
                            share.translation Y = row H.to Float()
                            translate.translation Y = row H.to Float()
                            baidu.translation Y = row H.to Float()
                        }
                        // 5. Shareleft Hide，bottom rowtop  (56-72%)
                        f <= 0.72f -> {
                            if (highlighted) {
                                deep Seek.background = null
                                highlighted = false
                            }
                            val p = ease Out((f - 0.56f) / 0.16f)
                            deep Seek.translation Y = -4f * row H
                            deep Seek.translation Z = 0f
                            paste.translation Y = row H.to Float()
                            share.translation Y = row H.to Float()
                            share.translation X = -100f * density * p
                            share.alpha = 1f - p
                            translate.translation Y = row H * (1f - p)
                            baidu.translation Y = row H * (1f - p)
                        }
                        // 6. most Stay (72-90%)
                        f <= 0.90f -> {
                            if (highlighted) {
                                deep Seek.background = null
                                highlighted = false
                            }
                            card.alpha = 1f
                            deep Seek.translation Y = -4f * row H
                            paste.translation Y = row H.to Float()
                            share.translation Y = row H.to Float()
                            share.translation X = -100f * density
                            share.alpha = 0f
                            translate.translation Y = 0f
                            baidu.translation Y = 0f
                        }
                        // 7.   (90-97%)
                        f <= 0.97f -> {
                            if (highlighted) {
                                deep Seek.background = null
                                highlighted = false
                            }
                            card.alpha = 1f - (f - 0.90f) / 0.07f
                        }
                        // 8.  can  (97-100%)
                        else -> {
                            card.alpha = 0f
                            reset Page2Rows(paste, share, translate, baidu, deep Seek)
                            if (highlighted) {
                                deep Seek.background = null
                                highlighted = false
                            }
                        }
                    }
                }
            }
            animator Page2?.start()
        }
    }

    private fun reset Page2Rows(
        paste: Text View,
        share: Text View,
        translate: Text View,
        baidu: Text View,
        deep Seek: Text View
    ) {
        paste.translation Y = 0f
        share.translation Y = 0f
        share.translation X = 0f
        share.alpha = 1f
        translate.translation Y = 0f
        baidu.translation Y = 0f
        deep Seek.translation Y = 0f
        deep Seek.translation Z = 0f
        deep Seek.scale X = 1f
        deep Seek.scale Y = 1f
    }
}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }
}
