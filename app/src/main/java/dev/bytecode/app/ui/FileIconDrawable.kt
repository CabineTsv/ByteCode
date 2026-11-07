package dev.bytecode.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import dev.bytecode.editor.LanguageInfo

/** A small rounded badge with the language's letters, drawn in the language's colour. */
class FileIconDrawable(private val language: LanguageInfo) : Drawable() {

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (language.iconColor and 0x00FFFFFF) or 0x33000000
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = (language.iconColor and 0x00FFFFFF) or 0x66000000
        strokeWidth = 1f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = language.iconColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val area = bounds
        if (area.isEmpty) return
        val radius = area.height() * 0.22f
        rect.set(area.left + 0.5f, area.top + 0.5f, area.right - 0.5f, area.bottom - 0.5f)
        canvas.drawRoundRect(rect, radius, radius, background)
        canvas.drawRoundRect(rect, radius, radius, outline)
        text.textSize = area.height() * if (language.iconLabel.length > 2) 0.34f else 0.42f
        val baseline = area.exactCenterY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(language.iconLabel, area.exactCenterX(), baseline, text)
    }

    override fun setAlpha(alpha: Int) {
        background.alpha = alpha
        outline.alpha = alpha
        text.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        background.colorFilter = colorFilter
        outline.colorFilter = colorFilter
        text.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
