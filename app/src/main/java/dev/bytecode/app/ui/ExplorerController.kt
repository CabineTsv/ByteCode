package dev.bytecode.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.bytecode.app.R
import dev.bytecode.app.workspace.FsEntry
import dev.bytecode.app.workspace.WorkspaceFileSystem
import dev.bytecode.editor.LanguageRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The file explorer: a lazily loaded tree of the workspace folder with real file operations
 * (create, rename, delete, copy/cut/paste, import, export) on top of the Storage Access Framework.
 */
class ExplorerController(
    private val context: Context,
    private val scope: CoroutineScope,
    val view: View,
    private val listener: Listener
) {

    interface Listener {
        fun onOpenFile(entry: FsEntry)
        fun onOpenFolderRequested()
        fun onImportRequested(target: FsEntry)
        fun onExportRequested(entry: FsEntry)
        fun onFileMoved(oldEntry: FsEntry, newEntry: FsEntry)
        fun onFilesystemChanged()
        fun onExpandedChanged(ids: Set<String>)
        fun onMessage(text: String)
    }

    private class ClipboardItem(val entry: FsEntry, val parentId: String, val cut: Boolean)

    private data class Row(
        val entry: FsEntry,
        val depth: Int,
        val parentId: String,
        val expanded: Boolean,
        val selected: Boolean
    )

    private val list: RecyclerView = view.findViewById(R.id.explorer_list)
    private val emptyView: View = view.findViewById(R.id.explorer_empty)
    private val workspaceName: TextView = view.findViewById(R.id.explorer_workspace_name)
    private val progress: LinearProgressIndicator = view.findViewById(R.id.explorer_progress)
    private val adapter = RowAdapter()

    private var fileSystem: WorkspaceFileSystem? = null
    private var root: FsEntry? = null
    private val entries = HashMap<String, FsEntry>()
    private val children = HashMap<String, List<FsEntry>>()
    private val expanded = LinkedHashSet<String>()
    private var selectedId: String? = null
    private var clipboard: ClipboardItem? = null
    private var pendingLoads = 0

    val hasWorkspace: Boolean
        get() = fileSystem != null

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null

        view.findViewById<MaterialButton>(R.id.explorer_open_button).setOnClickListener { listener.onOpenFolderRequested() }
        view.findViewById<ImageButton>(R.id.explorer_new_file).setOnClickListener { promptNewEntry(false) }
        view.findViewById<ImageButton>(R.id.explorer_new_folder).setOnClickListener { promptNewEntry(true) }
        view.findViewById<ImageButton>(R.id.explorer_refresh).setOnClickListener { refresh() }
        view.findViewById<ImageButton>(R.id.explorer_collapse).setOnClickListener { collapseAll() }
        workspaceName.setOnClickListener { root?.let { showMenu(it, null, workspaceName) } }
        updateEmptyState()
    }

    // ---- workspace ------------------------------------------------------------------------------------------

    fun setWorkspace(newFileSystem: WorkspaceFileSystem, displayName: String?, expandedIds: Set<String>) {
        fileSystem = newFileSystem
        root = null
        entries.clear()
        children.clear()
        expanded.clear()
        expanded.addAll(expandedIds)
        selectedId = null
        clipboard = null
        submit()
        scope.launch {
            val rootEntry = withContext(Dispatchers.IO) { runCatching { newFileSystem.rootEntry() }.getOrNull() }
            if (fileSystem !== newFileSystem) return@launch
            if (rootEntry == null) {
                listener.onMessage(context.getString(R.string.message_list_failed, displayName ?: "", ""))
                return@launch
            }
            root = rootEntry
            entries[rootEntry.documentId] = rootEntry
            workspaceName.text = displayName ?: rootEntry.name
            workspaceName.visibility = View.VISIBLE
            loadLevels(rootEntry)
        }
    }

    fun clearWorkspace() {
        fileSystem = null
        root = null
        entries.clear()
        children.clear()
        expanded.clear()
        selectedId = null
        workspaceName.visibility = View.GONE
        submit()
    }

    fun refresh() {
        val rootEntry = root ?: return
        scope.launch { loadLevels(rootEntry) }
    }

    fun collapseAll() {
        expanded.clear()
        submit()
        listener.onExpandedChanged(emptySet())
    }

    /** Loads [start] and every expanded folder below it, one level at a time. */
    private suspend fun loadLevels(start: FsEntry) {
        val queue = ArrayDeque<FsEntry>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            loadChildren(directory)
            for (child in children[directory.documentId].orEmpty()) {
                if (child.isDirectory && child.documentId in expanded) queue.add(child)
            }
        }
    }

    private suspend fun loadChildren(directory: FsEntry) {
        val currentFileSystem = fileSystem ?: return
        setLoading(1)
        val result = withContext(Dispatchers.IO) { runCatching { currentFileSystem.list(directory) } }
        setLoading(-1)
        if (fileSystem !== currentFileSystem) return
        val loaded = result.getOrNull()
        if (loaded == null) {
            listener.onMessage(context.getString(R.string.message_list_failed, directory.name, result.exceptionOrNull()?.message ?: ""))
            if (!children.containsKey(directory.documentId)) children[directory.documentId] = emptyList()
        } else {
            children[directory.documentId] = loaded
            for (child in loaded) entries[child.documentId] = child
        }
        submit()
    }

    private fun setLoading(delta: Int) {
        pendingLoads = maxOf(0, pendingLoads + delta)
        progress.visibility = if (pendingLoads > 0) View.VISIBLE else View.GONE
    }

    // ---- rows -----------------------------------------------------------------------------------------------------

    private fun buildRows(): List<Row> {
        val rootEntry = root ?: return emptyList()
        val rows = ArrayList<Row>()
        fun visit(parent: FsEntry, depth: Int) {
            val loaded = children[parent.documentId] ?: return
            for (child in loaded) {
                val isExpanded = child.isDirectory && child.documentId in expanded
                rows.add(Row(child, depth, parent.documentId, isExpanded, child.documentId == selectedId))
                if (isExpanded) visit(child, depth + 1)
            }
        }
        visit(rootEntry, 0)
        return rows
    }

    private fun submit() {
        adapter.submitList(buildRows())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyView.visibility = if (fileSystem == null) View.VISIBLE else View.GONE
    }

    private fun onRowClicked(row: Row) {
        selectedId = row.entry.documentId
        if (!row.entry.isDirectory) {
            submit()
            listener.onOpenFile(row.entry)
            return
        }
        if (row.expanded) {
            expanded.remove(row.entry.documentId)
        } else {
            expanded.add(row.entry.documentId)
            if (!children.containsKey(row.entry.documentId)) {
                scope.launch { loadChildren(row.entry) }
            }
        }
        submit()
        listener.onExpandedChanged(expanded.toSet())
    }

    // ---- context menu ------------------------------------------------------------------------------------------------

    private fun showMenu(entry: FsEntry, parentId: String?, anchor: View) {
        val popup = PopupMenu(context, anchor)
        val actions = HashMap<Int, () -> Unit>()
        fun add(@StringRes title: Int, action: () -> Unit) {
            val id = actions.size + 1
            popup.menu.add(0, id, id, title)
            actions[id] = action
        }

        if (entry.isDirectory) {
            add(R.string.action_new_file) { promptNewEntry(false, entry) }
            add(R.string.action_new_folder) { promptNewEntry(true, entry) }
            add(R.string.menu_import) { listener.onImportRequested(entry) }
            if (clipboard != null) add(R.string.menu_paste) { paste(entry) }
        } else {
            add(R.string.menu_open) { listener.onOpenFile(entry) }
        }
        if (parentId != null) {
            add(R.string.action_rename) { promptRename(entry, parentId) }
            add(R.string.menu_copy) { copyToClipboard(entry, parentId, false) }
            add(R.string.menu_cut) { copyToClipboard(entry, parentId, true) }
            add(R.string.action_delete) { confirmDelete(entry, parentId) }
        }
        if (!entry.isDirectory) add(R.string.menu_export) { listener.onExportRequested(entry) }
        if (entry.isDirectory) add(R.string.action_refresh) { scope.launch { loadLevels(entry) } }

        popup.setOnMenuItemClickListener { item ->
            actions[item.itemId]?.invoke()
            true
        }
        popup.show()
    }

    // ---- operations ----------------------------------------------------------------------------------------------------

    /** The folder new items go into: the selected folder, the folder of the selected file, or the root. */
    private fun currentTarget(): FsEntry? {
        val selected = selectedId?.let { entries[it] }
        if (selected != null) {
            if (selected.isDirectory) return selected
            val parent = parentOf(selected.documentId)
            if (parent != null) return parent
        }
        return root
    }

    private fun parentOf(documentId: String): FsEntry? {
        for ((parentId, loaded) in children) {
            if (loaded.any { it.documentId == documentId }) return entries[parentId]
        }
        return null
    }

    private fun validName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            listener.onMessage(context.getString(R.string.message_invalid_name))
            return null
        }
        return name
    }

    private fun promptNewEntry(directory: Boolean, target: FsEntry? = null) {
        val parent = target ?: currentTarget()
        if (parent == null) {
            listener.onMessage(context.getString(R.string.message_open_folder_first))
            return
        }
        Dialogs.promptText(
            context,
            if (directory) R.string.dialog_new_folder else R.string.dialog_new_file,
            if (directory) R.string.hint_folder_name else R.string.hint_file_name,
            "",
            R.string.action_create
        ) { raw ->
            val name = validName(raw) ?: return@promptText
            createEntry(parent, name, directory)
        }
    }

    private fun createEntry(parent: FsEntry, name: String, directory: Boolean) {
        val currentFileSystem = fileSystem ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (currentFileSystem.exists(parent, name)) throw IOException(context.getString(R.string.message_name_exists, name))
                    if (directory) currentFileSystem.createDirectory(parent, name) else currentFileSystem.createFile(parent, name)
                }
            }
            val created = result.getOrNull()
            if (created == null) {
                listener.onMessage(context.getString(R.string.message_create_failed, name, result.exceptionOrNull()?.message ?: ""))
                return@launch
            }
            expanded.add(parent.documentId)
            selectedId = created.documentId
            loadChildren(parent)
            listener.onExpandedChanged(expanded.toSet())
            listener.onFilesystemChanged()
            if (!directory) listener.onOpenFile(created)
        }
    }

    private fun promptRename(entry: FsEntry, parentId: String) {
        Dialogs.promptText(context, R.string.dialog_rename, R.string.hint_name, entry.name, R.string.action_rename) { raw ->
            val name = validName(raw) ?: return@promptText
            if (name == entry.name) return@promptText
            val currentFileSystem = fileSystem ?: return@promptText
            val parent = entries[parentId]
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (parent != null && currentFileSystem.exists(parent, name)) {
                            throw IOException(context.getString(R.string.message_name_exists, name))
                        }
                        currentFileSystem.rename(entry, name)
                    }
                }
                val renamed = result.getOrNull()
                if (renamed == null) {
                    listener.onMessage(context.getString(R.string.message_rename_failed, entry.name, result.exceptionOrNull()?.message ?: ""))
                    return@launch
                }
                forget(entry)
                selectedId = renamed.documentId
                if (parent != null) loadChildren(parent)
                if (!entry.isDirectory) listener.onFileMoved(entry, renamed)
                listener.onExpandedChanged(expanded.toSet())
                listener.onFilesystemChanged()
            }
        }
    }

    private fun confirmDelete(entry: FsEntry, parentId: String) {
        Dialogs.confirm(
            context,
            context.getString(R.string.dialog_delete_title),
            context.getString(R.string.dialog_delete_message, entry.name),
            R.string.action_delete
        ) { delete(entry, parentId) }
    }

    private fun delete(entry: FsEntry, parentId: String) {
        val currentFileSystem = fileSystem ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { currentFileSystem.delete(entry) } }
            if (result.isFailure) {
                listener.onMessage(context.getString(R.string.message_delete_failed, entry.name, result.exceptionOrNull()?.message ?: ""))
                return@launch
            }
            forget(entry)
            if (selectedId == entry.documentId) selectedId = null
            entries[parentId]?.let { loadChildren(it) }
            listener.onExpandedChanged(expanded.toSet())
            listener.onFilesystemChanged()
        }
    }

    private fun forget(entry: FsEntry) {
        children.remove(entry.documentId)
        expanded.remove(entry.documentId)
        entries.remove(entry.documentId)
    }

    private fun copyToClipboard(entry: FsEntry, parentId: String, cut: Boolean) {
        clipboard = ClipboardItem(entry, parentId, cut)
        listener.onMessage(context.getString(if (cut) R.string.message_cut else R.string.message_copied, entry.name))
    }

    private fun paste(target: FsEntry) {
        val item = clipboard ?: return
        val currentFileSystem = fileSystem ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (item.cut) currentFileSystem.move(item.entry, item.parentId, target)
                    else currentFileSystem.copy(item.entry, target)
                }
            }
            val pasted = result.getOrNull()
            if (pasted == null) {
                listener.onMessage(context.getString(R.string.message_paste_failed, item.entry.name, result.exceptionOrNull()?.message ?: ""))
                return@launch
            }
            if (item.cut) {
                clipboard = null
                forget(item.entry)
                if (!item.entry.isDirectory) listener.onFileMoved(item.entry, pasted)
                entries[item.parentId]?.let { loadChildren(it) }
            }
            expanded.add(target.documentId)
            loadChildren(target)
            listener.onExpandedChanged(expanded.toSet())
            listener.onFilesystemChanged()
        }
    }

    fun importFiles(uris: List<Uri>, target: FsEntry) {
        val currentFileSystem = fileSystem ?: return
        if (uris.isEmpty()) return
        scope.launch {
            val failures = ArrayList<String>()
            var imported = 0
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = displayNameOf(uri)
                    try {
                        currentFileSystem.importFile(uri, name, target)
                        imported++
                    } catch (error: Exception) {
                        failures.add("$name: ${error.message}")
                    }
                }
            }
            expanded.add(target.documentId)
            loadChildren(target)
            listener.onExpandedChanged(expanded.toSet())
            listener.onFilesystemChanged()
            if (failures.isEmpty()) {
                listener.onMessage(context.getString(R.string.message_import_done, imported))
            } else {
                listener.onMessage(context.getString(R.string.message_import_failed, failures.first()))
            }
        }
    }

    fun exportFile(entry: FsEntry, destination: Uri) {
        val currentFileSystem = fileSystem ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { currentFileSystem.exportFile(entry, destination) } }
            listener.onMessage(
                if (result.isSuccess) context.getString(R.string.message_export_done, entry.name)
                else context.getString(R.string.message_export_failed, entry.name, result.exceptionOrNull()?.message ?: "")
            )
        }
    }

    private fun displayNameOf(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { return it }
                }
            }
        } catch (error: Exception) {
            // fall back to the last path segment below
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file"
    }

    // ---- adapter ----------------------------------------------------------------------------------------------------------

    private inner class RowAdapter : ListAdapter<Row, RowHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_explorer_row, parent, false)
            return RowHolder(itemView)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chevron: ImageView = view.findViewById(R.id.row_chevron)
        private val icon: ImageView = view.findViewById(R.id.row_icon)
        private val name: TextView = view.findViewById(R.id.row_name)

        fun bind(row: Row) {
            val entry = row.entry
            itemView.setPaddingRelative(context.dp(6 + row.depth * 14), 0, itemView.paddingEnd, 0)
            name.text = entry.name
            if (entry.isDirectory) {
                chevron.visibility = View.VISIBLE
                chevron.rotation = if (row.expanded) 90f else 0f
                icon.setImageResource(if (row.expanded) R.drawable.ic_folder_open else R.drawable.ic_folder)
            } else {
                chevron.visibility = View.INVISIBLE
                icon.setImageDrawable(FileIconDrawable(LanguageRegistry.forFileName(entry.name)))
            }
            if (row.selected) {
                itemView.setBackgroundColor(Palette.color(context, R.color.bc_selection))
            } else {
                itemView.setBackgroundResource(R.drawable.bg_item_ripple)
            }
            itemView.setOnClickListener { onRowClicked(row) }
            itemView.setOnLongClickListener {
                showMenu(entry, row.parentId, itemView)
                true
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean = oldItem.entry.documentId == newItem.entry.documentId
            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean = oldItem == newItem
        }
    }
}
