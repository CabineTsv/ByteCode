package dev.bytecode.app.documents

import android.net.Uri

/** A file that is open in a tab. The text itself lives in the editor (JavaScript side). */
class OpenDocument(
    /** Identifier shared with the editor; letters and digits only. */
    val id: String,
    var uri: Uri,
    var name: String,
    var languageId: String,
    /** Large files open as plain text without lint, folding or word wrap. */
    val largeFile: Boolean,
    /** True when the file could not be decoded as UTF-8 (saving would corrupt it) or is otherwise protected. */
    val readOnly: Boolean,
    /** The file started with a UTF-8 byte order mark, which is written back on save. */
    val hasBom: Boolean
) {
    var dirty: Boolean = false
    var loading: Boolean = true

    val key: String
        get() = uri.toString()
}
