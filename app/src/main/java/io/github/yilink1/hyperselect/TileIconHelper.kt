package io.github.yilink1.hyperselect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.abs

object TileIconHelper {

    /**
     * 快捷设置磁贴图标通常是单色遮罩，系统下拉面板会按主题重新着色。
     * 这里对只含灰度像素的图标套用主题前景色，彩色图标保持原样。
     */
    fun processTileIcon(context: Context, original: Drawable): Drawable {
        return runCatching {
            val sampleSize = 96
            val sampleBitmap = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
            val sampleCanvas = Canvas(sampleBitmap)
            original.setBounds(0, 0, sampleSize, sampleSize)
            original.draw(sampleCanvas)

            var isMonochrome = true
            val sampleStep = 3
            var visiblePixelCount = 0

            for (x in 0 until sampleSize step sampleStep) {
                for (y in 0 until sampleSize step sampleStep) {
                    val pixel = sampleBitmap.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha > 40) {
                        visiblePixelCount++
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        // 有明显色彩偏离说明是彩色图标，保留原样
                        if (abs(r - g) > 15 || abs(g - b) > 15) {
                            isMonochrome = false
                            break
                        }
                    }
                }
                if (!isMonochrome) break
            }

            if (visiblePixelCount > 0 && isMonochrome) {
                val textColor = ContextCompat.getColor(context, R.color.hyper_text_primary)
                (original.constantState?.newDrawable()?.mutate() ?: original.mutate()).apply {
                    setTint(textColor)
                    setTintMode(PorterDuff.Mode.SRC_IN)
                }
            } else {
                original
            }
        }.getOrDefault(original)
    }
}
