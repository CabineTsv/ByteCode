package dev.bytecode.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import dev.bytecode.app.R
import dev.bytecode.editor.EditorSettings
import java.io.File

enum class AutosaveMode { OFF, AFTER_DELAY, ON_FOCUS_LOSS }

/** An accent colour that is also available as a Material theme overlay. */
class AccentPreset(val key: String, val color: Int, val overlay: Int)

object AccentPresets {
    val all: List<AccentPreset> = listOf(
        AccentPreset("azure", 0xFF4EA1FF.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Azure),
        AccentPreset("teal", 0xFF2FBFAD.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Teal),
        AccentPreset("green", 0xFF5CC47C.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Green),
        AccentPreset("amber", 0xFFE5A63A.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Amber),
        AccentPreset("orange", 0xFFF0814A.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Orange),
        AccentPreset("rose", 0xFFE9627F.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Rose),
        AccentPreset("violet", 0xFF9A7CF0.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Violet),
        AccentPreset("slate", 0xFF8D9BB3.toInt(), R.style.ThemeOverlay_ByteCode_Accent_Slate)
    )

    fun byKey(key: String?): AccentPreset = all.firstOrNull { it.key == key } ?: all.first()

    /** The preset that looks closest to [color]; used for stock Material widgets when the accent is custom. */
    fun nearest(color: Int): AccentPreset =
        all.minByOrNull { distance(it.color, color) } ?: all.first()

    private fun distance(a: Int, b: Int): Int {
        val red = Color.red(a) - Color.red(b)
        val green = Color.green(a) - Color.green(b)
        val blue = Color.blue(a) - Color.blue(b)
        return red * red + green * green + blue * blue
    }

    private val hexPattern = Regex("^#[0-9a-fA-F]{6}$")

    fun isValidHex(text: String): Boolean = hexPattern.matches(text.trim())

    fun parseHex(text: String): Int? = if (isValidHex(text)) Color.parseColor(text.trim()) else null

    fun toHex(color: Int): String = String.format("#%06x", color and 0xFFFFFF)
}

/**
 * Typed access to the user's preferences. The Settings screen (res/xml/preferences.xml) writes the same
 * SharedPreferences file, so every key and default here must match that file.
 */
class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val fontSize: Int get() = prefs.getInt(KEY_FONT_SIZE, 14)
    val fontFamily: String get() = prefs.getString(KEY_FONT_FAMILY, "system") ?: "system"
    val tabSize: Int get() = prefs.getString(KEY_TAB_SIZE, "4")?.toIntOrNull() ?: 4
    val insertSpaces: Boolean get() = prefs.getBoolean(KEY_INSERT_SPACES, true)
    val wordWrap: Boolean get() = prefs.getBoolean(KEY_WORD_WRAP, false)
    val lineNumbers: Boolean get() = prefs.getBoolean(KEY_LINE_NUMBERS, true)
    val highlightActiveLine: Boolean get() = prefs.getBoolean(KEY_HIGHLIGHT_LINE, true)
    val autoCloseBrackets: Boolean get() = prefs.getBoolean(KEY_AUTO_BRACKETS, true)
    val autoCloseQuotes: Boolean get() = prefs.getBoolean(KEY_AUTO_QUOTES, true)
    val autoIndent: Boolean get() = prefs.getBoolean(KEY_AUTO_INDENT, true)
    val foldGutter: Boolean get() = prefs.getBoolean(KEY_FOLD_GUTTER, true)
    val syntaxTheme: String get() = prefs.getString(KEY_SYNTAX_THEME, "onecompiler") ?: "onecompiler"

    val themeMode: String get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
    val accentKey: String get() = prefs.getString(KEY_ACCENT, "azure") ?: "azure"

    val autosaveMode: AutosaveMode
        get() = when (prefs.getString(KEY_AUTOSAVE, "off")) {
            "delay" -> AutosaveMode.AFTER_DELAY
            "focus" -> AutosaveMode.ON_FOCUS_LOSS
            else -> AutosaveMode.OFF
        }

    /** 0 means no timeout. */
    val runTimeoutSeconds: Int get() = prefs.getString(KEY_RUN_TIMEOUT, "15")?.toIntOrNull() ?: 15
    val clearOutputBeforeRun: Boolean get() = prefs.getBoolean(KEY_CLEAR_OUTPUT, true)

    fun nightMode(): Int = when (themeMode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    /** True when the UI is currently drawn in the dark theme. */
    fun isDarkNow(): Boolean {
        val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }

    /** The exact accent colour (a custom hex value if one is chosen, otherwise the preset). */
    fun accentColor(): Int {
        if (accentKey == "custom") {
            AccentPresets.parseHex(prefs.getString(KEY_ACCENT_CUSTOM, "") ?: "")?.let { return it }
        }
        return AccentPresets.byKey(accentKey).color
    }

    /** Material theme overlay for stock widgets; the nearest preset when the accent is custom. */
    fun accentOverlay(): Int =
        if (accentKey == "custom") AccentPresets.nearest(accentColor()).overlay else AccentPresets.byKey(accentKey).overlay

    fun fontDirectory(): File = File(context.filesDir, "fonts").also { it.mkdirs() }

    fun customFontFile(): File = File(fontDirectory(), CUSTOM_FONT_NAME)

    fun hasCustomFont(): Boolean = customFontFile().isFile

    fun editorSettings(): EditorSettings {
        val useCustomFont = fontFamily == "custom" && hasCustomFont()
        val family = when {
            useCustomFont -> "\"BC Custom\", monospace"
            fontFamily == "courier" -> "\"Courier New\", Courier, monospace"
            else -> "monospace"
        }
        return EditorSettings(
            fontSize = fontSize,
            fontFamilyCss = family,
            customFontUrl = if (useCustomFont) "/fonts/$CUSTOM_FONT_NAME?v=${customFontFile().lastModified()}" else null,
            tabSize = tabSize,
            insertSpaces = insertSpaces,
            wordWrap = wordWrap,
            lineNumbers = lineNumbers,
            highlightActiveLine = highlightActiveLine,
            autoCloseBrackets = autoCloseBrackets,
            autoCloseQuotes = autoCloseQuotes,
            autoIndent = autoIndent,
            foldGutter = foldGutter,
            darkTheme = isDarkNow(),
            syntaxTheme = syntaxTheme,
            accentColor = AccentPresets.toHex(accentColor())
        )
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        const val KEY_FONT_SIZE = "editor_font_size"
        const val KEY_FONT_FAMILY = "editor_font_family"
        const val KEY_IMPORT_FONT = "editor_import_font"
        const val KEY_TAB_SIZE = "editor_tab_size"
        const val KEY_INSERT_SPACES = "editor_insert_spaces"
        const val KEY_WORD_WRAP = "editor_word_wrap"
        const val KEY_LINE_NUMBERS = "editor_line_numbers"
        const val KEY_HIGHLIGHT_LINE = "editor_highlight_line"
        const val KEY_AUTO_BRACKETS = "editor_auto_brackets"
        const val KEY_AUTO_QUOTES = "editor_auto_quotes"
        const val KEY_AUTO_INDENT = "editor_auto_indent"
        const val KEY_FOLD_GUTTER = "editor_fold_gutter"
        const val KEY_SYNTAX_THEME = "ui_syntax_theme"
        const val KEY_THEME = "ui_theme"
        const val KEY_ACCENT = "ui_accent"
        const val KEY_ACCENT_CUSTOM = "ui_accent_custom"
        const val KEY_AUTOSAVE = "files_autosave"
        const val KEY_RUN_TIMEOUT = "run_timeout"
        const val KEY_CLEAR_OUTPUT = "run_clear_output"
        const val CUSTOM_FONT_NAME = "custom.ttf"
    }
}
