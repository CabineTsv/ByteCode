package dev.bytecode.app.ui

import android.graphics.Path
import androidx.core.graphics.PathParser

internal sealed class Shape {
    class Circle(val cx: Float, val cy: Float, val radius: Float) : Shape()
    class RoundRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float) : Shape()
    class Data(private val pathData: String) : Shape() {
        val path: Path by lazy(LazyThreadSafetyMode.NONE) { PathParser.createPathFromPathData(pathData) }
    }
}

internal class GlyphLayer(
    val shape: Shape,
    val fill: Int = NO_COLOR,
    val stroke: Int = NO_COLOR,
    val strokeWidth: Float = 0f
)

internal const val NO_COLOR = 0

internal class Glyph(val layers: List<GlyphLayer>)

internal object LanguageGlyphs {

    const val VIEWPORT = 24f

    private val JAVASCRIPT = Glyph(
        listOf(
            GlyphLayer(Shape.RoundRect(2f, 2f, 22f, 22f, 3.5f), fill = 0xFFF7DF1E.toInt()),
            GlyphLayer(Shape.Data("M9.4 10 V15.6 C9.4 17.4 7.6 17.9 6.2 17.1"), stroke = 0xFF1B1D21.toInt(), strokeWidth = 2.1f),
            GlyphLayer(Shape.Data("M16.9 11.4 C16.4 10.3 12.6 9.9 12.6 12 C12.6 14.2 17 13.6 17 16 C17 18.3 13 18.1 12.2 16.8"), stroke = 0xFF1B1D21.toInt(), strokeWidth = 2.1f)
        )
    )

    private val TYPESCRIPT = Glyph(
        listOf(
            GlyphLayer(Shape.RoundRect(2f, 2f, 22f, 22f, 3.5f), fill = 0xFF3178C6.toInt()),
            GlyphLayer(Shape.Data("M5.2 10.2 H11.6 M8.4 10.2 V17.6"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2.1f),
            GlyphLayer(Shape.Data("M16.9 11.4 C16.4 10.3 12.6 9.9 12.6 12 C12.6 14.2 17 13.6 17 16 C17 18.3 13 18.1 12.2 16.8"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2.1f)
        )
    )

    private val PYTHON = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M8 6 C8 3.8 9.6 2.5 12 2.5 C14.6 2.5 16 3.6 16 5.8 V9.4 C16 10.8 15 11.4 13.6 11.4 H8.6 C7.2 11.4 6.2 12.6 6.2 14 V17.2 H5.4 C3.6 17.2 2.8 15.6 2.8 12.6 C2.8 9.6 3.6 8.4 5.6 8.4 H12 V7.2 H8 Z "), fill = 0xFF4B8BBE.toInt()),
            GlyphLayer(Shape.Data("M 16 18 C 16 20.2 14.4 21.5 12 21.5 C 9.4 21.5 8 20.4 8 18.2 V 14.6 C 8 13.2 9 12.6 10.4 12.6 H 15.4 C 16.8 12.6 17.8 11.4 17.8 10 V 6.8 H 18.6 C 20.4 6.8 21.2 8.4 21.2 11.4 C 21.2 14.4 20.4 15.6 18.4 15.6 H 12 V 16.8 H 16 Z"), fill = 0xFFFFD43B.toInt()),
            GlyphLayer(Shape.Circle(10.4f, 5.2f, 0.95f), fill = 0xFFFFFFFF.toInt()),
            GlyphLayer(Shape.Circle(13.6f, 18.8f, 0.95f), fill = 0xFF3B3B3B.toInt())
        )
    )

    private val HTML = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M3.5 2.5 H20.5 L19 19.6 L12 21.6 L5 19.6 Z"), fill = 0xFFE44D26.toInt()),
            GlyphLayer(Shape.Data("M12 4.2 V19.9 L17.7 18.4 L18.9 4.2 Z"), fill = 0xFFF16529.toInt()),
            GlyphLayer(Shape.Data("M16 7.6 H9.2 L8.9 11.4 H15 L14.6 15.8 L12 16.7 L9.4 15.8"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.7f)
        )
    )

    private val CSS = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M3.5 2.5 H20.5 L19 19.6 L12 21.6 L5 19.6 Z"), fill = 0xFF1572B6.toInt()),
            GlyphLayer(Shape.Data("M12 4.2 V19.9 L17.7 18.4 L18.9 4.2 Z"), fill = 0xFF33A9DC.toInt()),
            GlyphLayer(Shape.Data("M9 7.8 H15.4 L12.6 11.6 C14.8 11.6 15.6 12.8 15.6 14 C15.6 15.8 14.2 16.8 12.2 16.8 C10.8 16.8 9.8 16.4 9.2 15.6"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.7f)
        )
    )

    private val C = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M12 2 L20.5 7 V17 L12 22 L3.5 17 V7 Z"), fill = 0xFF00599C.toInt()),
            GlyphLayer(Shape.Data("M14.7 9.5 C14 8.6 13 8.1 11.8 8.1 C9.4 8.1 7.8 9.8 7.8 12 C7.8 14.2 9.4 15.9 11.8 15.9 C13 15.9 14 15.4 14.7 14.5"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2.2f)
        )
    )

    private val CPP = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M12 2 L20.5 7 V17 L12 22 L3.5 17 V7 Z"), fill = 0xFF00599C.toInt()),
            GlyphLayer(Shape.Data("M 11.3 9.5 C 10.6 8.6 9.6 8.1 8.4 8.1 C 6 8.1 4.4 9.8 4.4 12 C 4.4 14.2 6 15.9 8.4 15.9 C 9.6 15.9 10.6 15.4 11.3 14.5"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2f),
            GlyphLayer(Shape.Data("M14 10.6 V14.2 M12.2 12.4 H15.8"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.2f),
            GlyphLayer(Shape.Data("M18 10.6 V14.2 M16.2 12.4 H19.8"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.2f)
        )
    )

    private val CSHARP = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M12 2 L20.5 7 V17 L12 22 L3.5 17 V7 Z"), fill = 0xFF9B4F96.toInt()),
            GlyphLayer(Shape.Data("M 11.5 9.5 C 10.8 8.6 9.8 8.1 8.6 8.1 C 6.2 8.1 4.6 9.8 4.6 12 C 4.6 14.2 6.2 15.9 8.6 15.9 C 9.8 15.9 10.8 15.4 11.5 14.5"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2f),
            GlyphLayer(Shape.Data("M15 9.8 V15 M17.6 9.8 V15 M13.8 11.3 H18.8 M13.8 13.5 H18.8"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.1f)
        )
    )

    private val JSON = Glyph(
        listOf(
            GlyphLayer(Shape.Data("M9.6 3.2 C7.4 3.2 6.8 4.4 6.8 6.2 V9.2 C6.8 10.6 6 11.6 4.2 12 C6 12.4 6.8 13.4 6.8 14.8 V17.8 C6.8 19.6 7.4 20.8 9.6 20.8"), stroke = 0xFFE9A416.toInt(), strokeWidth = 2.1f),
            GlyphLayer(Shape.Data("M14.4 3.2 C16.6 3.2 17.2 4.4 17.2 6.2 V9.2 C17.2 10.6 18 11.6 19.8 12 C18 12.4 17.2 13.4 17.2 14.8 V17.8 C17.2 19.6 16.6 20.8 14.4 20.8"), stroke = 0xFFE9A416.toInt(), strokeWidth = 2.1f)
        )
    )

    private val LUA = Glyph(
        listOf(
            GlyphLayer(Shape.Circle(11.4f, 12.6f, 9f), fill = 0xFF4A5BD0.toInt()),
            GlyphLayer(Shape.Circle(11.4f, 12.6f, 4.4f), fill = 0xFFFFFFFF.toInt()),
            GlyphLayer(Shape.Circle(18.4f, 5.6f, 3.2f), fill = 0xFF4A5BD0.toInt(), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.3f)
        )
    )

    private val LUAU = Glyph(
        listOf(
            GlyphLayer(Shape.Circle(11.4f, 12.6f, 9f), fill = 0xFF00A2FF.toInt()),
            GlyphLayer(Shape.Data("M8.6 8.4 V13 C8.6 15 9.8 16.2 11.6 16.2 C13.4 16.2 14.6 15 14.6 13 V8.4"), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 2.1f),
            GlyphLayer(Shape.Circle(18.4f, 5.6f, 3.2f), fill = 0xFF00A2FF.toInt(), stroke = 0xFFFFFFFF.toInt(), strokeWidth = 1.3f)
        )
    )

    private val byLanguage: Map<String, Glyph> = mapOf(
        "javascript" to JAVASCRIPT,
        "typescript" to TYPESCRIPT,
        "python" to PYTHON,
        "html" to HTML,
        "css" to CSS,
        "c" to C,
        "cpp" to CPP,
        "csharp" to CSHARP,
        "json" to JSON,
        "lua" to LUA,
        "luau" to LUAU,
        "jsx" to JAVASCRIPT,
        "tsx" to TYPESCRIPT
    )

    fun forLanguage(id: String): Glyph? = byLanguage[id]
}
