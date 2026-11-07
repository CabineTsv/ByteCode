package dev.bytecode.app.documents

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.bytecode.app.R
import dev.bytecode.app.settings.AutosaveMode
import dev.bytecode.app.settings.SettingsRepository
import dev.bytecode.app.workspace.FileIo
import dev.bytecode.app.workspace.FileTooLargeException
import dev.bytecode.app.workspace.TextCodec
import dev.bytecode.editor.CodeEditorView
import dev.bytecode.editor.EditorDiagnostic
import dev.bytecode.editor.EditorEvent
import dev.bytecode.editor.LanguageRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Owns the list of open documents: loading files into the editor, saving them, autosave and
 * crash-recovery snapshots. The text of a document lives in the editor; this class only keeps metadata.
 */
class DocumentManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val editor: CodeEditorView,
    private val settings: SettingsRepository,
    private val recovery: RecoveryStore,
    private val listener: Listener
) {

    interface Listener {
        fun onDocumentsChanged()
        fun onActiveDocumentChanged(document: OpenDocument?)
        fun onDocumentMessage(message: String)
        fun onDocumentDiagnostics(document: OpenDocument, items: List<EditorDiagnostic>)
        fun onDocumentClosed(document: OpenDocument)
    }

    private val resolver = context.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bytecode-file-writer").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val openDocuments = ArrayList<OpenDocument>()
    private val loadingUris = HashSet<String>()
    private val recoveryTimers = HashMap<String, Runnable>()
    private val autosaveTimers = HashMap<String, Runnable>()
    private var idCounter = 0

    var activeId: String? = null
        private set

    val documents: List<OpenDocument>
        get() = openDocuments

    val active: OpenDocument?
        get() = openDocuments.firstOrNull { it.id == activeId }

    fun find(id: String): OpenDocument? = openDocuments.firstOrNull { it.id == id }

    fun findByUri(uri: Uri): OpenDocument? = openDocuments.firstOrNull { it.uri == uri }

    // ---- opening ---------------------------------------------------------------------------------------------

    /**
     * Opens [uri] in a new tab (or activates the existing tab). [askRestore] is asked whether unsaved
     * changes from a previous session should be restored. Returns the loading job, or null when the
     * file is already open or being loaded.
     */
    fun openFile(
        uri: Uri,
        displayName: String,
        lastModified: Long,
        activate: Boolean,
        askRestore: suspend (String) -> Boolean
    ): Job? {
        val existing = findByUri(uri)
        if (existing != null) {
            if (activate) activate(existing.id)
            return null
        }
        if (!loadingUris.add(uri.toString())) return null
        return scope.launch {
            try {
                loadAndOpen(uri, displayName, lastModified, activate, askRestore)
            } finally {
                loadingUris.remove(uri.toString())
            }
        }
    }

    private suspend fun loadAndOpen(
        uri: Uri,
        displayName: String,
        lastModified: Long,
        activate: Boolean,
        askRestore: suspend (String) -> Boolean
    ) {
        val bytes = try {
            withContext(Dispatchers.IO) { FileIo.readBytes(resolver, uri, MAX_FILE_BYTES) }
        } catch (error: FileTooLargeException) {
            listener.onDocumentMessage(context.getString(R.string.message_file_too_large, displayName))
            return
        } catch (error: Exception) {
            listener.onDocumentMessage(context.getString(R.string.message_open_failed, displayName, error.message ?: ""))
            return
        }

        if (TextCodec.looksBinary(bytes)) {
            listener.onDocumentMessage(context.getString(R.string.message_binary_file, displayName))
            return
        }

        var text = TextCodec.decodeStrict(bytes) ?: ""
        var readOnly = false
        if (text.isEmpty() && bytes.isNotEmpty()) {
            // Not valid UTF-8: show it, but do not allow saving a lossy conversion over the original.
            text = TextCodec.decodeLossy(bytes)
            readOnly = true
            listener.onDocumentMessage(context.getString(R.string.message_not_utf8, displayName))
        }
        var hasBom = false
        if (text.isNotEmpty() && text[0] == TextCodec.BOM) {
            text = text.substring(1)
            hasBom = true
        }

        val language = LanguageRegistry.forFileName(displayName)
        val largeFile = bytes.size > LARGE_FILE_BYTES

        var restoreText: String? = null
        val snapshot = withContext(Dispatchers.IO) { recovery.read(uri.toString()) }
        if (snapshot != null) {
            if (!readOnly && snapshot.text != text && snapshot.savedAt >= lastModified && askRestore(displayName)) {
                restoreText = snapshot.text
            } else {
                withContext(Dispatchers.IO) { recovery.delete(uri.toString()) }
            }
        }

        val document = OpenDocument(newId(), uri, displayName, language.id, largeFile, readOnly, hasBom)
        openDocuments.add(document)
        editor.openDocument(document.id, displayName, language.id, text, largeFile, readOnly, activate, restoreText)
        if (activate) setActive(document)
        listener.onDocumentsChanged()
    }

    private fun newId(): String {
        idCounter++
        return "d$idCounter"
    }

    // ---- switching and closing -----------------------------------------------------------------------------------

    fun activate(id: String) {
        val document = find(id) ?: return
        val previous = active
        if (previous != null && previous.id != id && settings.autosaveMode == AutosaveMode.ON_FOCUS_LOSS && previous.dirty) {
            save(previous)
        }
        editor.activateDocument(id)
        setActive(document)
    }

    private fun setActive(document: OpenDocument) {
        activeId = document.id
        listener.onActiveDocumentChanged(document)
    }

    /** Closes the tab without asking; callers confirm with the user first when [OpenDocument.dirty] is set. */
    fun close(id: String) {
        val index = openDocuments.indexOfFirst { it.id == id }
        if (index < 0) return
        val document = openDocuments.removeAt(index)
        cancelTimers(id)
        editor.closeDocument(id)
        scope.launch(Dispatchers.IO) { recovery.delete(document.key) }
        listener.onDocumentClosed(document)
        if (activeId == id) {
            val next = openDocuments.getOrNull(minOf(index, openDocuments.size - 1))
            if (next != null) {
                activate(next.id)
            } else {
                activeId = null
                listener.onActiveDocumentChanged(null)
            }
        }
        listener.onDocumentsChanged()
    }

    /** A file was renamed or moved in the explorer: keep its tab pointing at the new location. */
    fun onFileMoved(oldUri: Uri, newUri: Uri, newName: String) {
        val document = findByUri(oldUri) ?: return
        document.uri = newUri
        document.name = newName
        document.languageId = LanguageRegistry.forFileName(newName).id
        editor.renameDocument(document.id, newName, document.languageId)
        listener.onDocumentsChanged()
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    fun save(document: OpenDocument, onComplete: (Boolean) -> Unit = {}) {
        if (document.readOnly) {
            listener.onDocumentMessage(context.getString(R.string.message_read_only, document.name))
            onComplete(false)
            return
        }
        editor.requestText(document.id) { text, requestId ->
            if (text == null) {
                onComplete(false)
                return@requestText
            }
            scope.launch {
                val error = withContext(writeDispatcher) {
                    try {
                        var bytes = text.toByteArray(Charsets.UTF_8)
                        if (document.hasBom) bytes = UTF8_BOM + bytes
                        FileIo.writeBytes(resolver, document.uri, bytes)
                        null
                    } catch (failure: Exception) {
                        failure.message ?: failure.javaClass.simpleName
                    }
                }
                if (error == null) {
                    editor.markSaved(document.id, requestId)
                    withContext(Dispatchers.IO) { recovery.delete(document.key) }
                } else {
                    listener.onDocumentMessage(context.getString(R.string.message_save_failed, document.name, error))
                }
                onComplete(error == null)
            }
        }
    }

    fun saveAll() {
        for (document in openDocuments.toList()) {
            if (document.dirty && !document.readOnly) save(document)
        }
    }

    // ---- events from the editor ----------------------------------------------------------------------------------------

    fun handleEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.DocumentOpened -> find(event.docId)?.let {
                it.loading = false
                it.dirty = event.dirty
                listener.onDocumentsChanged()
            }
            is EditorEvent.DocumentFailed -> find(event.docId)?.let {
                openDocuments.remove(it)
                listener.onDocumentMessage(context.getString(R.string.message_open_failed, it.name, event.message))
                if (activeId == it.id) {
                    activeId = null
                    listener.onActiveDocumentChanged(null)
                }
                listener.onDocumentsChanged()
            }
            is EditorEvent.DirtyChanged -> find(event.docId)?.let {
                it.dirty = event.dirty
                if (!event.dirty) {
                    cancelTimers(it.id)
                    scope.launch(Dispatchers.IO) { recovery.delete(it.key) }
                }
                listener.onDocumentsChanged()
            }
            is EditorEvent.Edited -> find(event.docId)?.let { onEdited(it) }
            is EditorEvent.Diagnostics -> find(event.docId)?.let { listener.onDocumentDiagnostics(it, event.items) }
            else -> Unit
        }
    }

    private fun onEdited(document: OpenDocument) {
        if (document.readOnly || !document.dirty) return
        schedule(recoveryTimers, document.id, RECOVERY_DELAY_MILLIS) { snapshotForRecovery(document) }
        if (settings.autosaveMode == AutosaveMode.AFTER_DELAY) {
            schedule(autosaveTimers, document.id, AUTOSAVE_DELAY_MILLIS) {
                if (document.dirty) save(document)
            }
        }
    }

    private fun snapshotForRecovery(document: OpenDocument) {
        if (!document.dirty || document.readOnly || find(document.id) == null) return
        editor.requestText(document.id) { text, _ ->
            if (text != null && document.dirty) {
                scope.launch(Dispatchers.IO) { recovery.write(document.key, document.name, text) }
            }
        }
    }

    /** Called when the app leaves the foreground: keep a recovery copy and honour the autosave setting. */
    fun onAppBackgrounded() {
        for (document in openDocuments.toList()) {
            if (!document.dirty || document.readOnly) continue
            snapshotForRecovery(document)
            if (settings.autosaveMode != AutosaveMode.OFF) save(document)
        }
    }

    fun destroy() {
        recoveryTimers.values.forEach { mainHandler.removeCallbacks(it) }
        autosaveTimers.values.forEach { mainHandler.removeCallbacks(it) }
        recoveryTimers.clear()
        autosaveTimers.clear()
    }

    /** Forgets every document without touching files (used when the editor view had to be recreated). */
    fun forgetAll() {
        for (document in openDocuments.toList()) cancelTimers(document.id)
        openDocuments.clear()
        loadingUris.clear()
        activeId = null
        listener.onActiveDocumentChanged(null)
        listener.onDocumentsChanged()
    }

    private fun schedule(timers: HashMap<String, Runnable>, id: String, delayMillis: Long, action: () -> Unit) {
        timers.remove(id)?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            timers.remove(id)
            action()
        }
        timers[id] = task
        mainHandler.postDelayed(task, delayMillis)
    }

    private fun cancelTimers(id: String) {
        recoveryTimers.remove(id)?.let { mainHandler.removeCallbacks(it) }
        autosaveTimers.remove(id)?.let { mainHandler.removeCallbacks(it) }
    }

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024L * 1024L
        const val LARGE_FILE_BYTES = 2L * 1024L * 1024L
        const val RECOVERY_DELAY_MILLIS = 1500L
        const val AUTOSAVE_DELAY_MILLIS = 1500L
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
