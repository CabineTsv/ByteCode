package dev.bytecode.app.workspace

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * Real file operations on a folder the user picked with the Storage Access Framework.
 * All methods block; call them from a background dispatcher.
 */
class WorkspaceFileSystem(context: Context, val treeUri: Uri) {

    private val resolver: ContentResolver = context.contentResolver

    val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    fun rootEntry(): FsEntry {
        val uri = documentUri(rootDocumentId)
        return queryEntry(uri) ?: FsEntry(rootDocumentId, uri, rootDocumentId.substringAfterLast(':'), true, DIR_MIME, -1, 0)
    }

    fun queryEntry(uri: Uri): FsEntry? {
        try {
            resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return entryFrom(cursor)
            }
        } catch (error: Exception) {
            return null
        }
        return null
    }

    private fun entryFrom(cursor: Cursor): FsEntry {
        val id = cursor.getString(0)
        val name = cursor.getString(1) ?: id
        val mime = cursor.getString(2) ?: ""
        val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
        val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
        return FsEntry(id, documentUri(id), name, mime == DIR_MIME, mime, size, modified)
    }

    /** Folders first, then files, each sorted by name without regard to case. */
    fun list(parent: FsEntry): List<FsEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
        val result = ArrayList<FsEntry>()
        try {
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) result.add(entryFrom(cursor))
            }
        } catch (error: Exception) {
            throw IOException("Could not list ${parent.name}: ${error.message}", error)
        }
        result.sortWith(compareBy<FsEntry>({ !it.isDirectory }, { it.name.lowercase() }))
        return result
    }

    fun exists(parent: FsEntry, name: String): Boolean = list(parent).any { it.name == name }

    fun createFile(parent: FsEntry, name: String): FsEntry {
        // application/octet-stream keeps providers from appending an extension such as ".txt".
        val created = DocumentsContract.createDocument(resolver, parent.uri, "application/octet-stream", name)
            ?: throw IOException("Could not create $name")
        var entry = queryEntry(created) ?: throw IOException("Could not read back $name")
        if (entry.name != name) {
            DocumentsContract.renameDocument(resolver, created, name)?.let { renamed ->
                queryEntry(renamed)?.let { entry = it }
            }
        }
        return entry
    }

    fun createDirectory(parent: FsEntry, name: String): FsEntry {
        val created = DocumentsContract.createDocument(resolver, parent.uri, DIR_MIME, name)
            ?: throw IOException("Could not create folder $name")
        return queryEntry(created) ?: throw IOException("Could not read back folder $name")
    }

    fun delete(entry: FsEntry) {
        val deleted = try {
            DocumentsContract.deleteDocument(resolver, entry.uri)
        } catch (error: Exception) {
            throw IOException("Could not delete ${entry.name}: ${error.message}", error)
        }
        if (!deleted) throw IOException("Could not delete ${entry.name}")
    }

    fun rename(entry: FsEntry, newName: String): FsEntry {
        val renamed = try {
            DocumentsContract.renameDocument(resolver, entry.uri, newName)
        } catch (error: Exception) {
            throw IOException("Could not rename ${entry.name}: ${error.message}", error)
        } ?: throw IOException("Could not rename ${entry.name}")
        return queryEntry(renamed) ?: throw IOException("Could not read back $newName")
    }

    fun copy(entry: FsEntry, targetParent: FsEntry): FsEntry {
        guardAgainstCopyIntoItself(entry, targetParent)
        val viaProvider = try {
            DocumentsContract.copyDocument(resolver, entry.uri, targetParent.uri)
        } catch (error: Exception) {
            null // not supported by this provider: copy manually below
        }
        if (viaProvider != null) {
            queryEntry(viaProvider)?.let { return it }
        }
        return copyManually(entry, targetParent, 0)
    }

    fun move(entry: FsEntry, sourceParentDocumentId: String, targetParent: FsEntry): FsEntry {
        guardAgainstCopyIntoItself(entry, targetParent)
        val viaProvider = try {
            DocumentsContract.moveDocument(resolver, entry.uri, documentUri(sourceParentDocumentId), targetParent.uri)
        } catch (error: Exception) {
            null
        }
        if (viaProvider != null) {
            queryEntry(viaProvider)?.let { return it }
        }
        val copied = copyManually(entry, targetParent, 0)
        delete(entry)
        return copied
    }

    /** Copies a file from anywhere (for example a picked document) into [targetParent]. */
    fun importFile(source: Uri, displayName: String, targetParent: FsEntry): FsEntry {
        val created = createFile(targetParent, uniqueName(targetParent, displayName))
        try {
            FileIo.copy(resolver, source, created.uri)
        } catch (error: Exception) {
            runCatching { delete(created) }
            throw IOException("Could not import $displayName: ${error.message}", error)
        }
        return queryEntry(created.uri) ?: created
    }

    fun exportFile(entry: FsEntry, destination: Uri) {
        FileIo.copy(resolver, entry.uri, destination)
    }

    /** Visits every file below [root] (breadth first), skipping the folders named in [ignoredDirectories]. */
    fun walk(
        root: FsEntry,
        ignoredDirectories: Set<String>,
        shouldContinue: () -> Boolean,
        visit: (entry: FsEntry, relativePath: String) -> Unit
    ) {
        val queue = ArrayDeque<Pair<FsEntry, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty() && shouldContinue()) {
            val (directory, prefix) = queue.removeFirst()
            val children = try {
                list(directory)
            } catch (error: IOException) {
                continue
            }
            for (child in children) {
                if (!shouldContinue()) return
                val path = prefix + child.name
                if (child.isDirectory) {
                    if (child.name !in ignoredDirectories) queue.add(child to "$path/")
                } else {
                    visit(child, path)
                }
            }
        }
    }

    fun uniqueName(parent: FsEntry, wanted: String): String {
        val existing = list(parent).map { it.name }.toSet()
        if (wanted !in existing) return wanted
        val base = wanted.substringBeforeLast('.', wanted)
        val extension = if (wanted.contains('.')) "." + wanted.substringAfterLast('.') else ""
        var counter = 1
        while (true) {
            val candidate = if (counter == 1) "$base copy$extension" else "$base copy $counter$extension"
            if (candidate !in existing) return candidate
            counter++
        }
    }

    private fun copyManually(entry: FsEntry, targetParent: FsEntry, depth: Int): FsEntry {
        if (depth > MAX_COPY_DEPTH) throw IOException("Folder nesting is too deep to copy")
        val name = uniqueName(targetParent, entry.name)
        if (entry.isDirectory) {
            val created = createDirectory(targetParent, name)
            for (child in list(entry)) copyManually(child, created, depth + 1)
            return created
        }
        val created = createFile(targetParent, name)
        FileIo.copy(resolver, entry.uri, created.uri)
        return queryEntry(created.uri) ?: created
    }

    private fun guardAgainstCopyIntoItself(entry: FsEntry, targetParent: FsEntry) {
        if (!entry.isDirectory) return
        val target = targetParent.documentId
        if (target == entry.documentId || target.startsWith(entry.documentId + "/")) {
            throw IOException("A folder cannot be copied or moved into itself")
        }
    }

    companion object {
        const val DIR_MIME = DocumentsContract.Document.MIME_TYPE_DIR
        private const val MAX_COPY_DEPTH = 64

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        /** Folders that are never indexed or searched. */
        val IGNORED_DIRECTORIES = setOf(
            ".git", ".svn", ".hg", "node_modules", ".gradle", "build", ".idea", "__pycache__", ".cxx", ".venv", "venv"
        )
    }
}
