package dev.bytecode.editor

import org.json.JSONObject

/** Editor options that are forwarded to the CodeMirror instance. */
data class EditorSettings(
    val fontSize: Int = 14,
    /** A CSS font-family value, for example `monospace` or `"BC Custom", monospace`. */
    val fontFamilyCss: String = "monospace",
    /** Page-relative URL of an imported font file, or null. */
    val customFontUrl: String? = null,
    val tabSize: Int = 4,
    val insertSpaces: Boolean = true,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val highlightActiveLine: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val autoCloseQuotes: Boolean = true,
    val autoIndent: Boolean = true,
    val foldGutter: Boolean = true,
    val darkTheme: Boolean = true,
    /** One of: onecompiler, default, monokai, solarized. */
    val syntaxTheme: String = "onecompiler",
    /** `#rrggbb` */
    val accentColor: String = "#4ea1ff"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fontSize", fontSize)
        .put("fontFamily", fontFamilyCss)
        .put("customFontUrl", customFontUrl ?: JSONObject.NULL)
        .put("tabSize", tabSize)
        .put("insertSpaces", insertSpaces)
        .put("wordWrap", wordWrap)
        .put("lineNumbers", lineNumbers)
        .put("highlightActiveLine", highlightActiveLine)
        .put("autoCloseBrackets", autoCloseBrackets)
        .put("autoCloseQuotes", autoCloseQuotes)
        .put("autoIndent", autoIndent)
        .put("foldGutter", foldGutter)
        .put("darkTheme", darkTheme)
        .put("syntaxTheme", syntaxTheme)
        .put("accentColor", accentColor)
}
