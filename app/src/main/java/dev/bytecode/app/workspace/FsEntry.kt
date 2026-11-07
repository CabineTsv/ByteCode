package dev.bytecode.app.workspace

import android.net.Uri

/** A file or folder inside a Storage Access Framework tree. */
data class FsEntry(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String,
    /** -1 when the provider does not report a size. */
    val size: Long,
    val lastModified: Long
)
