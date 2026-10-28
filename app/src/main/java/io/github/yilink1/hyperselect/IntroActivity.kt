package io.github.yilink1.hyperselect

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class IntroActivity : AppCompatActivity() {

    private enum class ModuleStatus { CHECKING, ACTIVE, INACTIVE }

    private lateinit var viewPager: ViewPager2
    private lateinit var dotActivePill: View
    private lateinit var btnAction: Button

    private var animatorPage1: ValueAnimator? = null
    private var animatorPage2: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private var statusView: View? = null
    private var moduleStatus = ModuleStatus.CHECKING
    private var pollAttempts = 0
    private val statusHandler = Handler(Looper.getMainLooper())

    // Polling: notifyChange wakes ContentObserver in system_server to reread rules, 
    // as long as hook is alive it refreshes receipt; if Module not active/rebooted it never reads.
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (moduleStatus == ModuleStatus.ACTIVE) return
            try {
                contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
            } catch (_: Exception) {}
            pollAttempts++
            when {
                RuleStore.isModuleActiveSinceBoot(this@IntroActivity) ->
                    setModuleStatus(ModuleStatus.ACTIVE)
                pollAttempts >= 4 && moduleStatus == ModuleStatus.CHECKING ->
                    setModuleStatus(ModuleStatus.INACTIVE)
            }
            if (moduleStatus == ModuleStatus.CHECKING) {
                statusHandler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        // Directly check if Module is active on boot; if yes show checkmark immediately without fake detection
        if (RuleStore.isModuleActiveSinceBoot(this)) {
            moduleStatus = ModuleStatus.ACTIVE
        }

        // Handle edge-to-edge window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.introRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        viewPager = findViewById(R.id.viewPagerIntro)
        dotActivePill = findViewById(R.id.dotActivePill)
        btnAction = findViewById(R.id.btnIntroAction)

        viewPager.adapter = IntroAdapter()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                updateIndicatorScroll(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                updateActionBtnText(position)
                if (position == 2) startPolling() else stopPolling()
            }
        })

        btnAction.setOnClickListener {
            when (viewPager.currentItem) {
                0 -> viewPager.setCurrentItem(1, true)
                1 -> viewPager.setCurrentItem(2, true)
                else -> {
                    if (moduleStatus == ModuleStatus.ACTIVE) {
                        finishIntro()
                    } else {
                        // Manual re-detect
                        pollAttempts = 0
                        setModuleStatus(ModuleStatus.CHECKING)
                        startPolling()
                    }
                }
            }
        }

        updateIndicatorScroll(0, 0f)
        updateActionBtnText(0)
    }

    // Dynamic elastic indicator: stationary 6.5dp dot, stretched to flat bar on swipe, avoids scaleX causing bulging/ellipse
    private fun updateIndicatorScroll(position: Int, positionOffset: Float) {
        val density = resources.displayMetrics.density
        val baseSize = (6.5f * density).toInt()
        val dotGap = 8f * density
        val step = 6.5f * density + dotGap

        val headOffset = (positionOffset * 2f).coerceAtMost(1f)
        val tailOffset = ((positionOffset - 0.5f) * 2f).coerceAtLeast(0f)

        val left = (position + tailOffset) * step
        val right = (position * step + baseSize) + (headOffset * step)
        val targetWidth = (right - left).toInt().coerceAtLeast(baseSize)

        val lp = dotActivePill.layoutParams
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            dotActivePill.layoutParams = lp
        }
        dotActivePill.translationX = left
    }

    private fun updateActionBtnText(position: Int) {
        btnAction.text = when {
            position < 2 -> "Continue"
            moduleStatus == ModuleStatus.ACTIVE -> "Start clean experience"
            else -> "Re-detect"
        }
    }

    // ---------- Module self-check ----------

    private fun startPolling() {
        stopPolling()
        if (RuleStore.isModuleActiveSinceBoot(this)) {
            setModuleStatus(ModuleStatus.ACTIVE)
            return
        }
        pollAttempts = 0
        setModuleStatus(ModuleStatus.CHECKING)
        try {
            contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
        } catch (_: Exception) {}
        statusHandler.post(pollRunnable)
    }

    private fun stopPolling() {
        statusHandler.removeCallbacks(pollRunnable)
    }

    private fun setModuleStatus(status: ModuleStatus) {
        if (moduleStatus == status) return
        moduleStatus = status
        applyStatusUi()
        updateActionBtnText(viewPager.currentItem)
        if (status == ModuleStatus.ACTIVE) {
            stopPolling()
            statusView?.findViewById<TextView>(R.id.tvStatusIcon)?.let { icon ->
                icon.scaleX = 0.4f
                icon.scaleY = 0.4f
                icon.animate().scaleX(1f).scaleY(1f)
                    .setDuration(450)
                    .setInterpolator(OvershootInterpolator(2.5f))
                    .start()
            }
        }
    }

    private fun applyStatusUi() {
        val v = statusView ?: return
        val icon = v.findViewById<TextView>(R.id.tvStatusIcon)
        val title = v.findViewById<TextView>(R.id.tvStatusTitle)
        val desc = v.findViewById<TextView>(R.id.tvStatusDesc)
        when (moduleStatus) {
            ModuleStatus.CHECKING -> {
                icon.text = "…"
                icon.setBackgroundResource(R.drawable.bg_status_circle_checking)
                title.text = "Checking module status"
                desc.text = "Handshaking with system framework, please wait..."
                startPulse(icon)
            }
            ModuleStatus.INACTIVE -> {
                stopPulse()
                icon.alpha = 1f
                icon.text = "!"
                icon.setBackgroundResource(R.drawable.bg_status_circle_fail)
                title.text = "Module not active"
                desc.text = "Follow these steps:\n\n1. Open LSPosed Manager → Modules → Enable HyperSelect\n2. Reboot your phone (the module hooks the system framework, so a reboot is required)\n3. After rebooting, return to this page to auto-detect"
            }
            ModuleStatus.ACTIVE -> {
                stopPulse()
                icon.alpha = 1f
                icon.text = "✓"
                icon.setBackgroundResource(R.drawable.bg_status_circle_ok)
                title.text = "Module is active"
                desc.text = "Ready, all Settings take effect immediately"
            }
        }
    }

    private fun startPulse(target: View) {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(0.55f, 1f).apply {
            duration = 700L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { target.alpha = it.animatedValue as Float }
        }
        pulseAnimator?.start()
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
    }

    private fun finishIntro() {
        val sp = getSharedPreferences("hyper_select_prefs", MODE_PRIVATE)
        sp.edit().putBoolean("intro_shown", true).apply()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        animatorPage1?.cancel()
        animatorPage2?.cancel()
        pulseAnimator?.cancel()
        stopPolling()
    }

    // ViewPager2 Adapter for the 3 Walkthrough Pages
    private inner class IntroAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = 3

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutId = when (viewType) {
                0 -> R.layout.item_intro_page1
                1 -> R.layout.item_intro_page2
                else -> R.layout.item_intro_page3
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (position) {
                0 -> setupPage1Animation(holder.itemView)
                1 -> setupPage2Animation(holder.itemView)
                else -> {
                    statusView = holder.itemView
                    applyStatusUi()
                }
            }
        }
    }

    // ---------- Animation utils ----------

    private fun easeOut(p: Float): Float {
        val q = 1f - p
        return 1f - q * q * q
    }

    // STEP 1: AI Writing icon collapse -> Paste slides out like drawer (2.8s loop)
    private fun setupPage1Animation(view: View) {
        val ivAiIcon = view.findViewById<ImageView>(R.id.ivPage1AiIcon) ?: return
        val drawer = view.findViewById<FrameLayout>(R.id.drawerPage1Paste) ?: return
        val tvPaste = view.findViewById<TextView>(R.id.tvPage1Paste) ?: return

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            ivAiIcon.setImageResource(R.drawable.ic_xiaomi_ai_extracted_hd)
        } else {
            ivAiIcon.setImageResource(R.drawable.ic_xiaomi_ai_light_retina)
        }

        val density = resources.displayMetrics.density
        val aiIconTargetWidth = (24f * density).toInt()
        val aiIconTargetMargin = (10f * density).toInt()

        animatorPage1?.cancel()
        view.post {
            tvPaste.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val fullPasteWidth = tvPaste.measuredWidth
            val clipH = (48f * density).toInt()

            // Lock tvPaste to full physical width to prevent Android text engine from dropping chars
            tvPaste.layoutParams = FrameLayout.LayoutParams(fullPasteWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            tvPaste.translationX = 0f

            animatorPage1 = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2_800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    when {
                        // 1. Initial state: AI icon shown, drawer fully closed (0% - 20%)
                        f <= 0.20f -> {
                            ivAiIcon.scaleX = 1f
                            ivAiIcon.scaleY = 1f
                            ivAiIcon.rotation = 0f
                            ivAiIcon.alpha = 1f
                            val lpIcon = ivAiIcon.layoutParams as LinearLayout.LayoutParams
                            lpIcon.width = aiIconTargetWidth
                            lpIcon.marginEnd = aiIconTargetMargin
                            ivAiIcon.layoutParams = lpIcon

                            val lpDrawer = drawer.layoutParams as LinearLayout.LayoutParams
                            lpDrawer.width = 0
                            drawer.layoutParams = lpDrawer
                            drawer.clipBounds = Rect(0, 0, 0, clipH)
                        }
                        // 2. AI icon subtle bounce/rotate (20% - 30%)
                        f <= 0.30f -> {
                            val p = (f - 0.20f) / 0.10f
                            ivAiIcon.scaleX = 1f + 0.18f * p
                            ivAiIcon.scaleY = 1f + 0.18f * p
                            ivAiIcon.rotation = 45f * p
                            ivAiIcon.alpha = 1f

                            val lpDrawer = drawer.layoutParams as LinearLayout.LayoutParams
                            lpDrawer.width = 0
                            drawer.layoutParams = lpDrawer
                            drawer.clipBounds = Rect(0, 0, 0, clipH)
                        }
                        // 3. AI icon shrinks/fades, drawer opens to reveal text (smooth stroke animation)(30% - 50%)
                        f <= 0.50f -> {
                            val p = (f - 0.30f) / 0.20f
                            val smoothP = 1f - (1f - p) * (1f - p) * (1f - p)

                            ivAiIcon.scaleX = (1.18f * (1f - smoothP)).coerceAtLeast(0f)
                            ivAiIcon.scaleY = (1.18f * (1f - smoothP)).coerceAtLeast(0f)
                            ivAiIcon.rotation = 45f + 135f * smoothP
                            ivAiIcon.alpha = (1f - smoothP * 1.2f).coerceIn(0f, 1f)

                            val lpIcon = ivAiIcon.layoutParams as LinearLayout.LayoutParams
                            lpIcon.width = (aiIconTargetWidth * (1f - smoothP)).toInt()
                            lpIcon.marginEnd = (aiIconTargetMargin * (1f - smoothP)).toInt()
                            ivAiIcon.layoutParams = lpIcon

                            val currentWidth = (fullPasteWidth * smoothP).toInt()
                            val lpDrawer = drawer.layoutParams as LinearLayout.LayoutParams
                            lpDrawer.width = currentWidth
                            drawer.layoutParams = lpDrawer
                            drawer.clipBounds = Rect(0, 0, currentWidth, clipH)
                        }
                        // 4. CleanStatusKeep visible (50% - 82%)
                        f <= 0.82f -> {
                            val lpIcon = ivAiIcon.layoutParams as LinearLayout.LayoutParams
                            lpIcon.width = 0
                            lpIcon.marginEnd = 0
                            ivAiIcon.layoutParams = lpIcon
                            ivAiIcon.alpha = 0f

                            val lpDrawer = drawer.layoutParams as LinearLayout.LayoutParams
                            lpDrawer.width = fullPasteWidth
                            drawer.layoutParams = lpDrawer
                            drawer.clipBounds = null
                        }
                        // 5. Smooth reset: close drawer, prepare for next cycle (82% - 100%)
                        else -> {
                            val p = (f - 0.82f) / 0.18f
                            val currentWidth = (fullPasteWidth * (1f - p)).toInt()
                            val lpDrawer = drawer.layoutParams as LinearLayout.LayoutParams
                            lpDrawer.width = currentWidth
                            drawer.layoutParams = lpDrawer
                            drawer.clipBounds = Rect(0, 0, currentWidth, clipH)

                            val lpIcon = ivAiIcon.layoutParams as LinearLayout.LayoutParams
                            lpIcon.width = (aiIconTargetWidth * p).toInt()
                            lpIcon.marginEnd = (aiIconTargetMargin * p).toInt()
                            ivAiIcon.layoutParams = lpIcon
                            ivAiIcon.scaleX = p
                            ivAiIcon.scaleY = p
                            ivAiIcon.alpha = p
                            ivAiIcon.rotation = 180f + 180f * p
                        }
                    }
                }
            }
            animatorPage1?.start()
        }
    }

    // STEP 2: Highlight pinned items at top, others shift down (no text overlap) -> Shareleft Hide（Cycle 3.8s）
    private fun setupPage2Animation(view: View) {
        val card = view.findViewById<View>(R.id.cardPage2Menu)
        val paste = view.findViewById<TextView>(R.id.tvPage2Paste)
        val share = view.findViewById<TextView>(R.id.tvPage2Share)
        val translate = view.findViewById<TextView>(R.id.tvPage2Translate)
        val baidu = view.findViewById<TextView>(R.id.tvPage2Baidu)
        val deepSeek = view.findViewById<TextView>(R.id.tvPage2DeepSeek)

        val density = resources.displayMetrics.density
        var highlighted = false

        animatorPage2?.cancel()
        view.post {
            val rowH = paste.height
            if (rowH <= 0) return@post
            animatorPage2 = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3_800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    when {
                        // 1. Menu  (0-8%)
                        f <= 0.08f -> {
                            val p = easeOut(f / 0.08f)
                            card.alpha = p
                            card.scaleX = 0.85f + 0.15f * p
                            card.scaleY = 0.85f + 0.15f * p
                            resetPage2Rows(paste, share, translate, baidu, deepSeek)
                            if (highlighted) {
                                deepSeek.background = null
                                highlighted = false
                            }
                        }
                        // 2.  orderStay (8-26%)
                        f <= 0.26f -> {
                            card.alpha = 1f
                            card.scaleX = 1f
                            card.scaleY = 1f
                            resetPage2Rows(paste, share, translate, baidu, deepSeek)
                        }
                        // 3. DeepSeek  top Top， when  4 row bottom  1  ， nonetext ！(26-48%)
                        f <= 0.48f -> {
                            if (!highlighted) {
                                deepSeek.setBackgroundResource(R.drawable.bg_menu_item_highlight)
                                deepSeek.background?.mutate()?.alpha = 255
                                highlighted = true
                            }
                            val p = easeOut((f - 0.26f) / 0.22f)
                            deepSeek.translationY = -4f * rowH * p
                            deepSeek.translationZ = 8f * density * p
                            deepSeek.scaleX = 1f + 0.03f * p
                            deepSeek.scaleY = 1f + 0.03f * p

                            paste.translationY = rowH * p
                            share.translationY = rowH * p
                            translate.translationY = rowH * p
                            baidu.translationY = rowH * p
                        }
                        // 4.  ：DeepSeek  ，  (48-56%)
                        f <= 0.56f -> {
                            val p = easeOut((f - 0.48f) / 0.08f)
                            deepSeek.translationY = -4f * rowH
                            deepSeek.translationZ = 8f * density * (1f - p)
                            deepSeek.scaleX = 1f + 0.03f * (1f - p)
                            deepSeek.scaleY = 1f + 0.03f * (1f - p)
                            if (highlighted) {
                                deepSeek.background?.mutate()?.alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
                            }
                            paste.translationY = rowH.toFloat()
                            share.translationY = rowH.toFloat()
                            translate.translationY = rowH.toFloat()
                            baidu.translationY = rowH.toFloat()
                        }
                        // 5. Shareleft Hide，bottom rowtop  (56-72%)
                        f <= 0.72f -> {
                            if (highlighted) {
                                deepSeek.background = null
                                highlighted = false
                            }
                            val p = easeOut((f - 0.56f) / 0.16f)
                            deepSeek.translationY = -4f * rowH
                            deepSeek.translationZ = 0f
                            paste.translationY = rowH.toFloat()
                            share.translationY = rowH.toFloat()
                            share.translationX = -100f * density * p
                            share.alpha = 1f - p
                            translate.translationY = rowH * (1f - p)
                            baidu.translationY = rowH * (1f - p)
                        }
                        // 6. most Stay (72-90%)
                        f <= 0.90f -> {
                            if (highlighted) {
                                deepSeek.background = null
                                highlighted = false
                            }
                            card.alpha = 1f
                            deepSeek.translationY = -4f * rowH
                            paste.translationY = rowH.toFloat()
                            share.translationY = rowH.toFloat()
                            share.translationX = -100f * density
                            share.alpha = 0f
                            translate.translationY = 0f
                            baidu.translationY = 0f
                        }
                        // 7.   (90-97%)
                        f <= 0.97f -> {
                            if (highlighted) {
                                deepSeek.background = null
                                highlighted = false
                            }
                            card.alpha = 1f - (f - 0.90f) / 0.07f
                        }
                        // 8.  can  (97-100%)
                        else -> {
                            card.alpha = 0f
                            resetPage2Rows(paste, share, translate, baidu, deepSeek)
                            if (highlighted) {
                                deepSeek.background = null
                                highlighted = false
                            }
                        }
                    }
                }
            }
            animatorPage2?.start()
        }
    }

    private fun resetPage2Rows(
        paste: TextView,
        share: TextView,
        translate: TextView,
        baidu: TextView,
        deepSeek: TextView
    ) {
        paste.translationY = 0f
        share.translationY = 0f
        share.translationX = 0f
        share.alpha = 1f
        translate.translationY = 0f
        baidu.translationY = 0f
        deepSeek.translationY = 0f
        deepSeek.translationZ = 0f
        deepSeek.scaleX = 1f
        deepSeek.scaleY = 1f
    }
}