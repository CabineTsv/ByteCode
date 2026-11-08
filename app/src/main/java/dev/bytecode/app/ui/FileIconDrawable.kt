package dev.bytecode.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import dev.bytecode.editor.LanguageInfo
import kotlin.math.min

/** A small rounded badge with the language's letters, drawn in the language's colour. */
class FileIconDrawable(private val language: LanguageInfo) : Drawable() {

    private val glyph: Glyph? = LanguageGlyphs.forLanguage(language.id)
    private var alphaValue = 255

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
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val area = bounds
        if (area.isEmpty) return
        if (glyph != null) drawGlyph(canvas, glyph) else drawBadge(canvas)
    }

    private fun drawGlyph(canvas: Canvas, glyph: Glyph) {
        val area = bounds
        val scale = min(area.width(), area.height()) / LanguageGlyphs.VIEWPORT
        val saved = canvas.save()
        canvas.translate(
            area.left + (area.width() - LanguageGlyphs.VIEWPORT * scale) / 2f,
            area.top + (area.height() - LanguageGlyphs.VIEWPORT * scale) / 2f
        )
        canvas.scale(scale, scale)
        for (layer in glyph.layers) {
            if (layer.fill != NO_COLOR) {
                fillPaint.color = layer.fill
                fillPaint.alpha = Color.alpha(layer.fill) * alphaValue / 255
                drawShape(canvas, layer.shape, fillPaint)
            }
            if (layer.stroke != NO_COLOR) {
                strokePaint.color = layer.stroke
                strokePaint.alpha = Color.alpha(layer.stroke) * alphaValue / 255
                strokePaint.strokeWidth = layer.strokeWidth
                drawShape(canvas, layer.shape, strokePaint)
            }
        }
        canvas.restoreToCount(saved)
    }

    private fun drawShape(canvas: Canvas, shape: Shape, paint: Paint) {
        when (shape) {
            is Shape.Circle -> canvas.drawCircle(shape.cx, shape.cy, shape.radius, paint)
            is Shape.RoundRect -> {
                rect.set(shape.left, shape.top, shape.right, shape.bottom)
                canvas.drawRoundRect(rect, shape.radius, shape.radius, paint)
            }
            is Shape.Data -> canvas.drawPath(shape.path, paint)
        }
    }

    private fun drawBadge(canvas: Canvas) {
        val area = bounds
        val radius = area.height() * 0.22f
        rect.set(area.left + 0.5f, area.top + 0.5f, area.right - 0.5f, area.bottom - 0.5f)
        canvas.drawRoundRect(rect, radius, radius, background)
        canvas.drawRoundRect(rect, radius, radius, outline)
        text.textSize = area.height() * if (language.iconLabel.length > 2) 0.34f else 0.42f
        val baseline = area.exactCenterY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(language.iconLabel, area.exactCenterX(), baseline, text)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        background.alpha = alpha
        outline.alpha = alpha
        text.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        background.colorFilter = colorFilter
        outline.colorFilter = colorFilter
        text.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
