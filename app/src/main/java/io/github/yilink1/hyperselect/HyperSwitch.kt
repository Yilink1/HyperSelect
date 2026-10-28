package io.github.yilink1.hyperselect

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * Custom switch：Solid blue track + white slider，toggle with 200ms smooth swipe effect，closed state uses inactive gray track。
 * Does not rely on fragmented SwitchCompat XML across ROM versions，works consistently across HyperOS versions。
 */
class HyperSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Callback after user tap toggle（Calling setChecked in code does not trigger）。 */
    var onToggled: ((Boolean) -> Unit)? = null

    var checked: Boolean = false
        private set

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val trackRect = RectF()
    private val colorOn = ContextCompat.getColor(context, R.color.hyper_accent)
    private val colorOff = ContextCompat.getColor(context, R.color.hyper_switch_off)
    private val evaluator = ArgbEvaluator()

    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
    }

    fun toggle() {
        setChecked(!checked, animate = true)
        onToggled?.invoke(checked)
    }

    fun setChecked(value: Boolean, animate: Boolean) {
        checked = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            progress = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = 200L
                interpolator = DecelerateInterpolator(1.4f)
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(50), dp(30))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        trackPaint.color = evaluator.evaluate(progress, colorOff, colorOn) as Int
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, h / 2f, h / 2f, trackPaint)

        val inset = dp(3).toFloat()
        val radius = h / 2f - inset
        val cx = (h / 2f) + (w - h) * progress
        canvas.drawCircle(cx, h / 2f, radius, thumbPaint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checked
    }
}
