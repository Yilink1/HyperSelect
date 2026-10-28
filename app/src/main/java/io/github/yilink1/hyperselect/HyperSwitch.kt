package io.github.yilink1.hyperselect

import android.animation.Argb Evaluator
import android.animation.Value Animator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect F
import android.util.Attribute Set
import android.view.View
import android.view.accessibility.Accessibility Node Info
import android.view.animation.Decelerate Interpolator
import androidx.core.content.Context Compat

/**
 * Custom switch：Solid blue track + white slider，toggle with 200ms smooth swipe effect，closed state uses inactive gray track。
 * Does not rely on fragmented Switch Compat XML across ROM versions，works consistently across Hyper OS versions。
 */
class Hyper Switch @Jvm Overloads constructor(
    context: Context,
    attrs: Attribute Set? = null
) : View(context, attrs) {

    /** Callback after user tap toggle（Calling set Checked in code does not trigger）。 */
    var on Toggled: ((Boolean) -> Unit)? = null

    var checked: Boolean = false
        private set

    private val track Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val track Rect = Rect F()
    private val color On = Context Compat.get Color(context, R.color.hyper_accent)
    private val color Off = Context Compat.get Color(context, R.color.hyper_switch_off)
    private val evaluator = Argb Evaluator()

    private var progress = 0f
    private var animator: Value Animator? = null

    init {
        is Clickable = true
        is Focusable = true
        set On Click Listener { toggle() }
    }

    fun toggle() {
        set Checked(!checked, animate = true)
        on Toggled?.invoke(checked)
    }

    fun set Checked(value: Boolean, animate: Boolean) {
        checked = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        if (!animate || !is Attached To Window) {
            progress = target
            invalidate()
        } else {
            animator = Value Animator.of Float(progress, target).apply {
                duration = 200L
                interpolator = Decelerate Interpolator(1.4f)
                add Update Listener {
                    progress = it.animated Value as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun set Enabled(enabled: Boolean) {
        super.set Enabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }

    override fun on Detached From Window() {
        animator?.cancel()
        super.on Detached From Window()
    }

    override fun on Measure(width Measure Spec: Int, height Measure Spec: Int) {
        set Measured Dimension(dp(50), dp(30))
    }

    override fun on Draw(canvas: Canvas) {
        val w = width.to Float()
        val h = height.to Float()

        track Paint.color = evaluator.evaluate(progress, color Off, color On) as Int
        track Rect.set(0f, 0f, w, h)
        canvas.draw Round Rect(track Rect, h / 2f, h / 2f, track Paint)

        val inset = dp(3).to Float()
        val radius = h / 2f - inset
        val cx = (h / 2f) + (w - h) * progress
        canvas.draw Circle(cx, h / 2f, radius, thumb Paint)
    }

    override fun on Initialize Accessibility Node Info(info: Accessibility Node Info) {
        super.on Initialize Accessibility Node Info(info)
        info.class Name = "android.widget.Switch"
        info.is Checkable = true
        info.is Checked = checked
    }
}
