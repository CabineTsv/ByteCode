package dev.bytecode.editor

enum class DiagnosticSeverity(val wireName: String) {
    ERROR("error"),
    WARNING("warning"),
    INFO("info");

    companion object {
        fun fromWireName(name: String): DiagnosticSeverity = entries.firstOrNull { it.wireName == name } ?: ERROR
    }
}

/** A problem shown in the editor and in the Problems list. Lines and columns are 1-based. */
data class EditorDiagnostic(
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val severity: DiagnosticSeverity,
    val message: String
)

/** Everything the editor reports back to the app. Delivered on the main thread. */
sealed class EditorEvent {
    /** The editor page is loaded and accepts commands. */
    object Ready : EditorEvent()

    /** The editor page never started (usually: the JavaScript bundle is missing from the APK). */
    object StartupTimeout : EditorEvent()

    /** The WebView renderer process died; the app should call [CodeEditorView.restart] and reopen documents. */
    data class RendererGone(val didCrash: Boolean) : EditorEvent()

    data class DocumentOpened(val docId: String, val dirty: Boolean) : EditorEvent()
    data class DocumentFailed(val docId: String, val message: String) : EditorEvent()
    data class DirtyChanged(val docId: String, val dirty: Boolean) : EditorEvent()

    /** The text changed and the user stopped typing for a moment (used by autosave and recovery). */
    data class Edited(val docId: String) : EditorEvent()

    data class CursorMoved(
        val docId: String,
        val line: Int,
        val column: Int,
        val selectedChars: Int,
        val totalLines: Int
    ) : EditorEvent()

    /** The editor wants [text] checked; answer with [CodeEditorView.resolveLint]. */
    data class LintRequest(val requestId: String, val docId: String, val language: String, val text: String) : EditorEvent()

    data class Diagnostics(val docId: String, val items: List<EditorDiagnostic>) : EditorEvent()

    /** A keyboard shortcut handled by the app, such as `save` or `commandPalette`. */
    data class Command(val name: String) : EditorEvent()

    data class Log(val level: String, val message: String) : EditorEvent()
}
