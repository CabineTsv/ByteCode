package dev.bytecode.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.bytecode.app.documents.DocumentManager
import dev.bytecode.app.documents.OpenDocument
import dev.bytecode.app.documents.RecoveryStore
import dev.bytecode.app.run.JavaScriptRunner
import dev.bytecode.app.run.LineSeverity
import dev.bytecode.app.run.LuauRunner
import dev.bytecode.app.run.RunManager
import dev.bytecode.app.run.RunRequest
import dev.bytecode.app.run.ShellRunner
import dev.bytecode.app.run.SourceLocation
import dev.bytecode.app.settings.SettingsActivity
import dev.bytecode.app.settings.SettingsRepository
import dev.bytecode.app.ui.ConsoleChannel
import dev.bytecode.app.ui.ConsoleLine
import dev.bytecode.app.ui.ConsolePanel
import dev.bytecode.app.ui.Dialogs
import dev.bytecode.app.ui.ExplorerController
import dev.bytecode.app.ui.ExtraKeysBar
import dev.bytecode.app.ui.PaletteDialog
import dev.bytecode.app.ui.PaletteItem
import dev.bytecode.app.ui.Palette
import dev.bytecode.app.ui.TabStrip
import dev.bytecode.app.ui.dp
import dev.bytecode.app.workspace.FsEntry
import dev.bytecode.app.workspace.IndexedFile
import dev.bytecode.app.workspace.TextSearch
import dev.bytecode.app.workspace.WorkspaceFileSystem
import dev.bytecode.app.workspace.WorkspaceIndexer
import dev.bytecode.app.workspace.WorkspaceStore
import dev.bytecode.editor.CodeEditorView
import dev.bytecode.editor.DiagnosticSeverity
import dev.bytecode.editor.EditorDiagnostic
import dev.bytecode.editor.EditorEvent
import dev.bytecode.editor.LanguageRegistry
import dev.bytecode.luau.LuauRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.resume

/**
 * The single activity. It wires the explorer, the tab strip, the editor, the console and the runners
 * together; the real work happens in the classes it creates.
 */
class MainActivity :
    AppCompatActivity(),
    CodeEditorView.Listener,
    DocumentManager.Listener,
    RunManager.Listener,
    ExplorerController.Listener,
    TabStrip.Listener,
    ConsolePanel.Listener,
    ExtraKeysBar.Listener {

    private data class PendingReveal(val documentKey: String, val line: Int, val column: Int)

    private lateinit var settings: SettingsRepository
    private lateinit var store: WorkspaceStore
    private lateinit var luau: LuauRuntime
    private lateinit var documents: DocumentManager
    private lateinit var runManager: RunManager
    private lateinit var explorer: ExplorerController
    private lateinit var tabs: TabStrip
    private lateinit var console: ConsolePanel

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var explorerDock: FrameLayout
    private lateinit var explorerDivider: View
    private lateinit var explorerDrawer: FrameLayout
    private lateinit var editorView: CodeEditorView
    private lateinit var statusMessage: TextView
    private lateinit var statusPosition: TextView
    private lateinit var statusIndent: TextView
    private lateinit var statusLanguage: TextView

    private var fileSystem: WorkspaceFileSystem? = null
    private var workspaceName: String? = null
    private var workspaceIndex: List<IndexedFile>? = null
    private var wideLayout = false
    private var needsRecreate = false
    private var searchJob: Job? = null
    private var pendingImportTarget: FsEntry? = null
    private var pendingExportEntry: FsEntry? = null
    private var pendingReveal: PendingReveal? = null
    private val problems = LinkedHashMap<String, List<EditorDiagnostic>>()

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onSettingChanged(key) }

    private val openFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) openWorkspace(uri, showExplorer = true)
    }
    private val openFilesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (uri in uris) openExternalFile(uri)
    }
    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val target = pendingImportTarget
        pendingImportTarget = null
        if (target != null) explorer.importFiles(uris, target)
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = pendingExportEntry
        pendingExportEntry = null
        if (uri != null && entry != null) explorer.exportFile(entry, uri)
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsRepository(this)
        theme.applyStyle(settings.accentOverlay(), true)
        Palette.accent = settings.accentColor()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = WorkspaceStore(this)
        luau = LuauRuntime()

        bindViews()
        applySystemBars()
        createComponents()
        setupToolbar()
        setupBackHandling()

        editorView.listener = this
        editorView.fontDirectory = settings.fontDirectory()
        editorView.start(settings.editorSettings())

        console.restore(store.consoleVisible, dp(store.consoleHeightDp))
        applyLayoutMode()
        installExplorerDividerDrag()
        settings.registerListener(settingsListener)
        updateToolbar()
        updateStatus()
        restoreSession()
    }

    override fun onStart() {
        super.onStart()
        if (needsRecreate) {
            needsRecreate = false
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        editorView.onResume()
    }

    override fun onPause() {
        editorView.onPause()
        super.onPause()
    }

    override fun onStop() {
        documents.onAppBackgrounded()
        persistSession()
        super.onStop()
    }

    override fun onDestroy() {
        settings.unregisterListener(settingsListener)
        searchJob?.cancel()
        documents.destroy()
        runManager.destroy()
        editorView.destroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayoutMode()
        console.setHeightPx(console.heightPx)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            val command = when (keyCode) {
                KeyEvent.KEYCODE_S -> if (event.isShiftPressed) AppCommand.SAVE_ALL else AppCommand.SAVE
                KeyEvent.KEYCODE_P -> if (event.isShiftPressed) AppCommand.COMMAND_PALETTE else AppCommand.QUICK_OPEN
                KeyEvent.KEYCODE_B -> AppCommand.TOGGLE_EXPLORER
                KeyEvent.KEYCODE_J -> AppCommand.TOGGLE_CONSOLE
                else -> null
            }
            if (command != null) {
                execute(command)
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_F5) {
            execute(AppCommand.RUN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---- setup --------------------------------------------------------------------------------------------------------

    private fun bindViews() {
        drawer = findViewById(R.id.drawer)
        toolbar = findViewById(R.id.toolbar)
        explorerDock = findViewById(R.id.explorer_dock)
        explorerDivider = findViewById(R.id.explorer_divider)
        explorerDrawer = findViewById(R.id.explorer_drawer)
        editorView = findViewById(R.id.editor_view)
        statusMessage = findViewById(R.id.status_message)
        statusPosition = findViewById(R.id.status_position)
        statusIndent = findViewById(R.id.status_indent)
        statusLanguage = findViewById(R.id.status_language)
    }

    private fun applySystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val light = !settings.isDarkNow()
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    private fun createComponents() {
        val explorerView = layoutInflater.inflate(R.layout.view_explorer, null)
        explorer = ExplorerController(this, lifecycleScope, explorerView, this)
        tabs = TabStrip(findViewById(R.id.tab_strip), this)
        console = ConsolePanel(findViewById(R.id.console_panel), this)
        documents = DocumentManager(this, lifecycleScope, editorView, settings, RecoveryStore(this), this)
        runManager = RunManager(
            lifecycleScope,
            listOf(LuauRunner(luau), JavaScriptRunner(this), ShellRunner(this)),
            console,
            settings,
            this
        )
        findViewById<ExtraKeysBar>(R.id.extra_keys).listener = this
    }

    private fun setupToolbar() {
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setNavigationOnClickListener { execute(AppCommand.TOGGLE_EXPLORER) }
        toolbar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                    documents.documents.any { it.dirty } -> confirmExit()
                    else -> finish()
                }
            }
        })
    }

    private fun confirmExit() {
        Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_exit_title)
                .setMessage(R.string.dialog_exit_message)
                .setPositiveButton(R.string.action_save_all_and_exit) { _, _ ->
                    documents.saveAll()
                    finish()
                }
                .setNegativeButton(R.string.action_exit) { _, _ -> finish() }
                .setNeutralButton(R.string.action_cancel, null)
        )
    }

    // ---- layout: docked explorer on wide screens, drawer on phones ---------------------------------------------------------

    private fun applyLayoutMode() {
        wideLayout = resources.configuration.screenWidthDp >= WIDE_LAYOUT_DP
        val panel = explorer.view
        (panel.parent as? ViewGroup)?.removeView(panel)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (wideLayout) {
            drawer.closeDrawer(GravityCompat.START, false)
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            explorerDock.addView(panel, params)
            explorerDock.layoutParams = explorerDock.layoutParams.apply { width = dp(store.explorerWidthDp) }
            setDockVisible(store.explorerDockVisible)
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            explorerDock.visibility = View.GONE
            explorerDivider.visibility = View.GONE
            explorerDrawer.addView(panel, params)
        }
    }

    private fun setDockVisible(visible: Boolean) {
        store.explorerDockVisible = visible
        val visibility = if (visible) View.VISIBLE else View.GONE
        explorerDock.visibility = visibility
        explorerDivider.visibility = visibility
    }

    private fun toggleExplorer() {
        if (wideLayout) {
            setDockVisible(explorerDock.visibility != View.VISIBLE)
        } else if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            drawer.openDrawer(GravityCompat.START)
        }
    }

    private fun revealExplorer() {
        if (wideLayout) setDockVisible(true) else drawer.openDrawer(GravityCompat.START)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installExplorerDividerDrag() {
        var startX = 0f
        var startWidth = 0
        explorerDivider.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startWidth = explorerDock.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maximum = maxOf((findViewById<View>(R.id.workbench).width * 0.6f).toInt(), dp(180))
                    val width = (startWidth + (event.rawX - startX).toInt()).coerceIn(dp(180), maximum)
                    explorerDock.layoutParams = explorerDock.layoutParams.apply { this.width = width }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    store.explorerWidthDp = (explorerDock.width / resources.displayMetrics.density).toInt()
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ---- toolbar and status bar --------------------------------------------------------------------------------------------

    private fun onMenuItem(id: Int): Boolean {
        val command = when (id) {
            R.id.action_run -> if (runManager.isRunning) AppCommand.STOP else AppCommand.RUN
            R.id.action_save -> AppCommand.SAVE
            R.id.action_find -> AppCommand.FIND
            R.id.action_command_palette -> AppCommand.COMMAND_PALETTE
            R.id.action_quick_open -> AppCommand.QUICK_OPEN
            R.id.action_goto_line -> AppCommand.GOTO_LINE
            R.id.action_search_files -> AppCommand.SEARCH_FILES
            R.id.action_save_all -> AppCommand.SAVE_ALL
            R.id.action_open_folder -> AppCommand.OPEN_FOLDER
            R.id.action_open_file -> AppCommand.OPEN_FILE
            R.id.action_toggle_console -> AppCommand.TOGGLE_CONSOLE
            R.id.action_settings -> AppCommand.SETTINGS
            R.id.action_about -> AppCommand.ABOUT
            else -> return false
        }
        execute(command)
        return true
    }

    private fun updateToolbar() {
        val active = documents.active
        toolbar.title = active?.name ?: getString(R.string.app_name)
        toolbar.subtitle = workspaceName
        val menu = toolbar.menu
        val running = runManager.isRunning
        menu.findItem(R.id.action_run)?.let { item ->
            item.isVisible = running || (active != null && runManager.canRun(active.languageId))
            item.setIcon(if (running) R.drawable.ic_stop else R.drawable.ic_play)
            item.setTitle(if (running) R.string.action_stop else R.string.action_run)
            item.iconTintList = ColorStateList.valueOf(Palette.accent)
        }
        menu.findItem(R.id.action_save)?.isEnabled = active != null && active.dirty && !active.readOnly
        menu.findItem(R.id.action_find)?.isEnabled = active != null
    }

    private fun updateStatus() {
        val active = documents.active
        statusLanguage.text = if (active == null) "" else LanguageRegistry.byId(active.languageId).displayName
        statusIndent.text = when {
            active == null -> ""
            settings.insertSpaces -> getString(R.string.status_spaces, settings.tabSize)
            else -> getString(R.string.status_tabs, settings.tabSize)
        }
        if (active == null) statusPosition.text = ""
    }

    private fun updateCursor(event: EditorEvent.CursorMoved) {
        val base = getString(R.string.status_position, event.line, event.column)
        statusPosition.text = if (event.selectedChars > 0) base + " " + getString(R.string.status_selected, event.selectedChars) else base
    }

    private fun showMessage(text: String) {
        statusMessage.text = text
        console.append(ConsoleChannel.LOG, ConsoleLine(text, LineSeverity.INFO, null))
        Snackbar.make(findViewById<View>(R.id.main_column), text, Snackbar.LENGTH_SHORT).show()
    }

    // ---- commands ---------------------------------------------------------------------------------------------------------------

    private fun isCommandAvailable(command: AppCommand): Boolean {
        val active = documents.active
        return when (command) {
            AppCommand.RUN -> active != null && !runManager.isRunning && runManager.canRun(active.languageId)
            AppCommand.STOP -> runManager.isRunning
            AppCommand.SAVE, AppCommand.FIND, AppCommand.REPLACE, AppCommand.GOTO_LINE, AppCommand.TOGGLE_COMMENT,
            AppCommand.FOLD_ALL, AppCommand.UNFOLD_ALL, AppCommand.UNDO, AppCommand.REDO, AppCommand.CLOSE_TAB -> active != null
            AppCommand.SAVE_ALL, AppCommand.CLOSE_ALL_TABS, AppCommand.NEXT_TAB, AppCommand.PREVIOUS_TAB ->
                documents.documents.isNotEmpty()
            AppCommand.NEW_FILE, AppCommand.NEW_FOLDER, AppCommand.REFRESH_EXPLORER, AppCommand.SEARCH_FILES ->
                fileSystem != null
            else -> true
        }
    }

    private fun execute(command: AppCommand) {
        if (!isCommandAvailable(command)) return
        when (command) {
            AppCommand.SAVE -> documents.active?.let { documents.save(it) }
            AppCommand.SAVE_ALL -> documents.saveAll()
            AppCommand.RUN -> runActiveDocument()
            AppCommand.STOP -> runManager.stop()
            AppCommand.FIND -> editorView.runAction("find")
            AppCommand.REPLACE -> editorView.runAction("replace")
            AppCommand.GOTO_LINE -> editorView.runAction("gotoLine")
            AppCommand.TOGGLE_COMMENT -> editorView.runAction("toggleComment")
            AppCommand.FOLD_ALL -> editorView.runAction("foldAll")
            AppCommand.UNFOLD_ALL -> editorView.runAction("unfoldAll")
            AppCommand.UNDO -> editorView.runAction("undo")
            AppCommand.REDO -> editorView.runAction("redo")
            AppCommand.QUICK_OPEN -> showQuickOpen()
            AppCommand.COMMAND_PALETTE -> showCommandPalette()
            AppCommand.SEARCH_FILES -> promptSearchInFiles()
            AppCommand.RECENT_FILES -> showRecentFiles()
            AppCommand.RECENT_WORKSPACES -> showRecentWorkspaces()
            AppCommand.NEW_FILE -> {
                revealExplorer()
                findViewById<View>(R.id.explorer_new_file).performClick()
            }
            AppCommand.NEW_FOLDER -> {
                revealExplorer()
                findViewById<View>(R.id.explorer_new_folder).performClick()
            }
            AppCommand.OPEN_FOLDER -> openFolderPicker.launch(null)
            AppCommand.OPEN_FILE -> openFilesPicker.launch(arrayOf("*/*"))
            AppCommand.CLOSE_TAB -> documents.active?.let { requestClose(it) }
            AppCommand.CLOSE_ALL_TABS -> closeMany(documents.documents.toList())
            AppCommand.NEXT_TAB -> switchTab(1)
            AppCommand.PREVIOUS_TAB -> switchTab(-1)
            AppCommand.TOGGLE_EXPLORER -> toggleExplorer()
            AppCommand.TOGGLE_CONSOLE -> console.setExpanded(!console.isExpanded)
            AppCommand.CLEAR_CONSOLE -> console.clear(ConsoleChannel.OUTPUT)
            AppCommand.REFRESH_EXPLORER -> explorer.refresh()
            AppCommand.TOGGLE_WORD_WRAP -> settings.putBoolean(SettingsRepository.KEY_WORD_WRAP, !settings.wordWrap)
            AppCommand.INCREASE_FONT -> settings.putInt(SettingsRepository.KEY_FONT_SIZE, minOf(32, settings.fontSize + 1))
            AppCommand.DECREASE_FONT -> settings.putInt(SettingsRepository.KEY_FONT_SIZE, maxOf(8, settings.fontSize - 1))
            AppCommand.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            AppCommand.ABOUT -> showAbout()
        }
    }

    private fun handleEditorCommand(name: String) {
        val command = when (name) {
            "save" -> AppCommand.SAVE
            "saveAll" -> AppCommand.SAVE_ALL
            "quickOpen" -> AppCommand.QUICK_OPEN
            "commandPalette" -> AppCommand.COMMAND_PALETTE
            "searchInFiles" -> AppCommand.SEARCH_FILES
            "toggleExplorer" -> AppCommand.TOGGLE_EXPLORER
            "toggleConsole" -> AppCommand.TOGGLE_CONSOLE
            "run" -> AppCommand.RUN
            "closeTab" -> AppCommand.CLOSE_TAB
            "nextTab" -> AppCommand.NEXT_TAB
            "previousTab" -> AppCommand.PREVIOUS_TAB
            else -> return
        }
        execute(command)
    }

    private fun switchTab(direction: Int) {
        val list = documents.documents
        if (list.size < 2) return
        val current = list.indexOfFirst { it.id == documents.activeId }
        val next = ((if (current < 0) 0 else current) + direction + list.size) % list.size
        documents.activate(list[next].id)
    }

    private fun runActiveDocument() {
        val document = documents.active ?: return
        if (!runManager.canRun(document.languageId)) {
            showMessage(getString(R.string.message_cannot_run, LanguageRegistry.byId(document.languageId).displayName))
            return
        }
        editorView.requestText(document.id) { text, _ ->
            if (text != null) {
                runManager.run(
                    RunRequest(document.key, document.name, document.languageId, text, settings.runTimeoutSeconds)
                )
            }
        }
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (error: Exception) {
            "?"
        }
        val luauText = if (luau.isAvailable) "Luau ${luau.version}" else getString(R.string.about_luau_unavailable)
        Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_message, version, luauText))
                .setPositiveButton(android.R.string.ok, null)
        )
    }

    // ---- palettes ---------------------------------------------------------------------------------------------------------------------

    private fun showCommandPalette() {
        val items = AppCommand.entries
            .filter { isCommandAvailable(it) && it != AppCommand.COMMAND_PALETTE }
            .map { command -> PaletteItem(getString(command.title), command.shortcut) { execute(command) } }
        PaletteDialog(this, getString(R.string.action_command_palette)).show(items)
    }

    private fun recentFileItems(): List<PaletteItem> = store.recentFiles().map { recent ->
        PaletteItem(recent.name, getString(R.string.palette_recent)) {
            val uri = Uri.parse(recent.uri)
            val info = queryDocumentInfo(uri)
            if (info == null) {
                store.removeRecentFile(recent.uri)
                showMessage(getString(R.string.message_open_failed, recent.name, getString(R.string.message_no_access)))
            } else {
                documents.openFile(uri, info.first, info.second, true) { name -> askRestore(name) }
            }
        }
    }

    private fun showRecentFiles() {
        PaletteDialog(this, getString(R.string.action_recent_files)).show(recentFileItems())
    }

    private fun showRecentWorkspaces() {
        val items = store.recentWorkspaces().map { recent ->
            PaletteItem(recent.name, null) {
                val uri = Uri.parse(recent.uri)
                if (store.hasPermission(uri)) openWorkspace(uri, showExplorer = true) else openFolderPicker.launch(uri)
            }
        }
        PaletteDialog(this, getString(R.string.action_recent_workspaces)).show(items)
    }

    private fun showQuickOpen() {
        val fs = fileSystem
        val dialog = PaletteDialog(this, getString(R.string.action_quick_open))
        dialog.show(recentFileItems())
        if (fs == null) return
        lifecycleScope.launch {
            val files = ensureIndex(fs)
            dialog.setItems(files.map { file -> PaletteItem(file.entry.name, file.path) { openEntry(file.entry) } })
        }
    }

    private suspend fun ensureIndex(fs: WorkspaceFileSystem): List<IndexedFile> {
        workspaceIndex?.let { return it }
        val files = withContext(Dispatchers.IO) {
            WorkspaceIndexer.index(fs, fs.rootEntry(), MAX_INDEXED_FILES) { isActive }
        }
        if (fileSystem === fs) workspaceIndex = files
        return files
    }

    // ---- search in files -----------------------------------------------------------------------------------------------------------------

    private fun promptSearchInFiles() {
        if (fileSystem == null) {
            showMessage(getString(R.string.message_open_folder_first))
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_search, null)
        val query: EditText = view.findViewById(R.id.search_query)
        val matchCase: CheckBox = view.findViewById(R.id.search_case)
        val useRegex: CheckBox = view.findViewById(R.id.search_regex)
        Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_search_files)
                .setView(view)
                .setPositiveButton(R.string.action_search) { _, _ ->
                    startSearch(query.text.toString(), matchCase.isChecked, useRegex.isChecked)
                }
                .setNegativeButton(R.string.action_cancel, null)
        )
    }

    private fun startSearch(query: String, caseSensitive: Boolean, useRegex: Boolean) {
        val fs = fileSystem ?: return
        if (query.isEmpty()) return
        searchJob?.cancel()
        console.clear(ConsoleChannel.SEARCH)
        console.select(ConsoleChannel.SEARCH)
        console.setExpanded(true)
        console.append(ConsoleChannel.SEARCH, ConsoleLine(getString(R.string.search_running, query), LineSeverity.INFO, null))

        searchJob = lifecycleScope.launch {
            val buffer = ArrayList<ConsoleLine>()
            val resolver = contentResolver
            val found = try {
                val files = ensureIndex(fs)
                withContext(Dispatchers.IO) {
                    TextSearch.search(resolver, files, query, caseSensitive, useRegex, MAX_SEARCH_MATCHES, { isActive }) { match ->
                        val line = ConsoleLine(
                            "${match.file.path}:${match.line}:${match.column}  ${match.lineText}",
                            LineSeverity.NORMAL,
                            SourceLocation(match.file.entry.uri.toString(), match.line, match.column)
                        )
                        synchronized(buffer) {
                            buffer.add(line)
                            if (buffer.size >= SEARCH_BATCH) {
                                val batch = ArrayList(buffer)
                                buffer.clear()
                                runOnUiThread { batch.forEach { console.append(ConsoleChannel.SEARCH, it) } }
                            }
                        }
                    }
                }
            } catch (error: PatternSyntaxException) {
                console.append(
                    ConsoleChannel.SEARCH,
                    ConsoleLine(getString(R.string.search_bad_pattern, error.description ?: ""), LineSeverity.ERROR, null)
                )
                return@launch
            }
            val rest = synchronized(buffer) { ArrayList(buffer).also { buffer.clear() } }
            rest.forEach { console.append(ConsoleChannel.SEARCH, it) }
            console.append(
                ConsoleChannel.SEARCH,
                ConsoleLine(getString(R.string.search_done, found), LineSeverity.SUCCESS, null)
            )
        }
    }

    // ---- workspace, files and session --------------------------------------------------------------------------------------------------

    private fun openWorkspace(uri: Uri, showExplorer: Boolean) {
        val newFileSystem = try {
            store.takePermission(uri)
            WorkspaceFileSystem(this, uri)
        } catch (error: IllegalArgumentException) {
            showMessage(getString(R.string.message_list_failed, uri.toString(), error.message ?: ""))
            return
        }
        fileSystem = newFileSystem
        workspaceIndex = null
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { newFileSystem.rootEntry().name }.getOrNull() }
                ?: uri.lastPathSegment?.substringAfterLast(':')
                ?: getString(R.string.app_name)
            if (fileSystem !== newFileSystem) return@launch
            workspaceName = name
            store.lastWorkspaceUri = uri.toString()
            store.addRecentWorkspace(uri, name)
            explorer.setWorkspace(newFileSystem, name, store.expandedFolders(uri.toString()))
            updateToolbar()
            if (showExplorer) revealExplorer()
        }
    }

    private fun openEntry(entry: FsEntry) {
        documents.openFile(entry.uri, entry.name, entry.lastModified, true) { name -> askRestore(name) }
        store.addRecentFile(entry.uri, entry.name)
        if (!wideLayout) drawer.closeDrawer(GravityCompat.START)
    }

    private fun openExternalFile(uri: Uri) {
        store.takePermission(uri)
        val info = queryDocumentInfo(uri)
        if (info == null) {
            showMessage(getString(R.string.message_open_failed, uri.lastPathSegment ?: "", getString(R.string.message_no_access)))
            return
        }
        documents.openFile(uri, info.first, info.second, true) { name -> askRestore(name) }
        store.addRecentFile(uri, info.first)
    }

    private fun openAt(uri: Uri, name: String, lastModified: Long, line: Int, column: Int) {
        val existing = documents.findByUri(uri)
        if (existing != null) {
            documents.activate(existing.id)
            editorView.revealPosition(existing.id, line, column, false)
            return
        }
        pendingReveal = PendingReveal(uri.toString(), line, column)
        documents.openFile(uri, name, lastModified, true) { restoreName -> askRestore(restoreName) }
    }

    private fun handlePendingReveal(docId: String) {
        val reveal = pendingReveal ?: return
        val document = documents.find(docId) ?: return
        if (document.key == reveal.documentKey) {
            pendingReveal = null
            editorView.revealPosition(document.id, reveal.line, reveal.column, false)
        }
    }

    private fun queryDocumentInfo(uri: Uri): Pair<String, Long>? {
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (error: Exception) {
            null
        } ?: return null
        val modified = try {
            contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (error: Exception) {
            0L
        }
        return name to modified
    }

    private suspend fun askRestore(name: String): Boolean = suspendCancellableCoroutine { continuation ->
        val dialog = Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_restore_title)
                .setMessage(getString(R.string.dialog_restore_message, name))
                .setCancelable(false)
                .setPositiveButton(R.string.action_restore) { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setNegativeButton(R.string.action_discard) { _, _ -> if (continuation.isActive) continuation.resume(false) }
        )
        continuation.invokeOnCancellation { dialog.dismiss() }
    }

    private fun persistSession() {
        store.saveSession(documents.documents.map { it.key }, documents.active?.key)
    }

    private fun restoreSession() {
        val tabKeys = store.sessionTabs()
        val activeKey = store.sessionActive()
        val last = store.lastWorkspaceUri
        if (last != null) {
            val uri = Uri.parse(last)
            if (store.hasPermission(uri)) openWorkspace(uri, showExplorer = false) else store.lastWorkspaceUri = null
        }
        reopenTabs(tabKeys, activeKey)
    }

    private fun reopenTabs(tabKeys: List<String>, activeKey: String?) {
        if (tabKeys.isEmpty()) return
        lifecycleScope.launch {
            for (key in tabKeys) {
                val uri = Uri.parse(key)
                val info = withContext(Dispatchers.IO) { queryDocumentInfo(uri) } ?: continue
                documents.openFile(uri, info.first, info.second, key == activeKey) { name -> askRestore(name) }?.join()
            }
            if (documents.active == null) {
                documents.documents.lastOrNull()?.let { documents.activate(it.id) }
            }
        }
    }

    private fun requestClose(document: OpenDocument) {
        if (!document.dirty) {
            documents.close(document.id)
            return
        }
        Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_unsaved_title)
                .setMessage(getString(R.string.dialog_unsaved_message, document.name))
                .setPositiveButton(R.string.action_save) { _, _ ->
                    documents.save(document) { saved -> if (saved) documents.close(document.id) }
                }
                .setNegativeButton(R.string.action_dont_save) { _, _ -> documents.close(document.id) }
                .setNeutralButton(R.string.action_cancel, null)
        )
    }

    private fun closeMany(list: List<OpenDocument>) {
        if (list.isEmpty()) return
        val dirtyCount = list.count { it.dirty }
        val closeAll = { list.forEach { documents.close(it.id) } }
        if (dirtyCount == 0) {
            closeAll()
            return
        }
        Dialogs.confirm(
            this,
            getString(R.string.dialog_unsaved_title),
            getString(R.string.dialog_close_many_message, dirtyCount),
            R.string.action_close
        ) { closeAll() }
    }

    // ---- editor events -------------------------------------------------------------------------------------------------------------------------

    override fun onEditorEvent(event: EditorEvent) {
        documents.handleEvent(event)
        when (event) {
            is EditorEvent.StartupTimeout -> showEditorStartupError()
            is EditorEvent.RendererGone -> recoverEditor()
            is EditorEvent.DocumentOpened -> handlePendingReveal(event.docId)
            is EditorEvent.CursorMoved -> if (event.docId == documents.activeId) updateCursor(event)
            is EditorEvent.LintRequest -> handleLintRequest(event)
            is EditorEvent.Command -> handleEditorCommand(event.name)
            is EditorEvent.Log ->
                console.append(ConsoleChannel.LOG, ConsoleLine("[editor] ${event.message}", logSeverity(event.level), null))
            else -> Unit
        }
    }

    private fun showEditorStartupError() {
        Dialogs.show(
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_editor_failed_title)
                .setMessage(R.string.dialog_editor_failed_message)
                .setPositiveButton(android.R.string.ok, null)
        )
    }

    /** The WebView renderer died: start a fresh editor and reopen the tabs (unsaved text comes back from recovery). */
    private fun recoverEditor() {
        showMessage(getString(R.string.message_editor_restarted))
        val keys = documents.documents.map { it.key }
        val activeKey = documents.active?.key
        documents.forgetAll()
        problems.clear()
        refreshProblems()
        editorView.restart()
        reopenTabs(keys, activeKey)
    }

    private fun handleLintRequest(event: EditorEvent.LintRequest) {
        if (event.language != "luau" || !luau.isAvailable) {
            editorView.resolveLint(event.requestId, emptyList())
            return
        }
        lifecycleScope.launch {
            val diagnostics = luau.checkSyntax(event.text).map { item ->
                EditorDiagnostic(
                    item.line + 1, item.column + 1, item.endLine + 1, item.endColumn + 1,
                    DiagnosticSeverity.ERROR, item.message
                )
            }
            editorView.resolveLint(event.requestId, diagnostics)
        }
    }

    private fun refreshProblems() {
        val lines = ArrayList<ConsoleLine>()
        for ((docId, items) in problems) {
            val document = documents.find(docId) ?: continue
            for (item in items) {
                lines.add(
                    ConsoleLine(
                        "${document.name}:${item.line}:${item.column}  ${item.message}",
                        lineSeverity(item.severity),
                        SourceLocation(document.key, item.line, item.column)
                    )
                )
            }
        }
        console.setLines(ConsoleChannel.PROBLEMS, lines)
        console.setProblemCount(lines.size)
    }

    private fun lineSeverity(severity: DiagnosticSeverity): LineSeverity = when (severity) {
        DiagnosticSeverity.ERROR -> LineSeverity.ERROR
        DiagnosticSeverity.WARNING -> LineSeverity.WARNING
        DiagnosticSeverity.INFO -> LineSeverity.INFO
    }

    private fun logSeverity(level: String): LineSeverity = when (level) {
        "error" -> LineSeverity.ERROR
        "warn" -> LineSeverity.WARNING
        else -> LineSeverity.INFO
    }

    // ---- DocumentManager.Listener ---------------------------------------------------------------------------------------------------------------

    override fun onDocumentsChanged() {
        tabs.submit(documents.documents, documents.activeId)
        updateToolbar()
        persistSession()
    }

    override fun onActiveDocumentChanged(document: OpenDocument?) {
        updateToolbar()
        updateStatus()
        if (document != null) store.addRecentFile(document.uri, document.name)
    }

    override fun onDocumentMessage(message: String) {
        showMessage(message)
    }

    override fun onDocumentDiagnostics(document: OpenDocument, items: List<EditorDiagnostic>) {
        if (items.isEmpty()) problems.remove(document.id) else problems[document.id] = items
        refreshProblems()
    }

    override fun onDocumentClosed(document: OpenDocument) {
        problems.remove(document.id)
        refreshProblems()
    }

    // ---- RunManager.Listener ------------------------------------------------------------------------------------------------------------------------

    override fun onRunStateChanged(running: Boolean) {
        updateToolbar()
    }

    // ---- ExplorerController.Listener -----------------------------------------------------------------------------------------------------------------

    override fun onOpenFile(entry: FsEntry) {
        openEntry(entry)
    }

    override fun onOpenFolderRequested() {
        openFolderPicker.launch(null)
    }

    override fun onImportRequested(target: FsEntry) {
        pendingImportTarget = target
        importPicker.launch(arrayOf("*/*"))
    }

    override fun onExportRequested(entry: FsEntry) {
        pendingExportEntry = entry
        exportPicker.launch(entry.name)
    }

    override fun onFileMoved(oldEntry: FsEntry, newEntry: FsEntry) {
        documents.onFileMoved(oldEntry.uri, newEntry.uri, newEntry.name)
    }

    override fun onFilesystemChanged() {
        workspaceIndex = null
        val fs = fileSystem ?: return
        val prefix = fs.treeUri.toString()
        val snapshot = documents.documents.toList()
        lifecycleScope.launch {
            val missing = withContext(Dispatchers.IO) {
                snapshot.filter { it.key.startsWith(prefix) && fs.queryEntry(it.uri) == null }
            }
            if (missing.isNotEmpty()) {
                missing.forEach { documents.close(it.id) }
                showMessage(getString(R.string.message_files_missing, missing.size))
            }
        }
    }

    override fun onExpandedChanged(ids: Set<String>) {
        val workspace = fileSystem?.treeUri?.toString() ?: return
        store.setExpandedFolders(workspace, ids)
    }

    override fun onMessage(text: String) {
        showMessage(text)
    }

    // ---- TabStrip.Listener ------------------------------------------------------------------------------------------------------------------------------

    override fun onTabSelected(id: String) {
        documents.activate(id)
    }

    override fun onTabCloseRequested(id: String) {
        documents.find(id)?.let { requestClose(it) }
    }

    override fun onTabMenuRequested(id: String, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_CLOSE, 0, R.string.action_close_tab)
        popup.menu.add(0, MENU_CLOSE_OTHERS, 1, R.string.action_close_others)
        popup.menu.add(0, MENU_CLOSE_ALL, 2, R.string.action_close_all_tabs)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CLOSE -> documents.find(id)?.let { requestClose(it) }
                MENU_CLOSE_OTHERS -> closeMany(documents.documents.filter { it.id != id })
                MENU_CLOSE_ALL -> closeMany(documents.documents.toList())
            }
            true
        }
        popup.show()
    }

    // ---- ConsolePanel.Listener -------------------------------------------------------------------------------------------------------------------------------

    override fun onLocationClicked(location: SourceLocation) {
        val uri = Uri.parse(location.documentKey)
        val existing = documents.findByUri(uri)
        if (existing != null) {
            documents.activate(existing.id)
            editorView.revealPosition(existing.id, location.line, location.column, false)
            return
        }
        val info = queryDocumentInfo(uri) ?: return
        openAt(uri, info.first, info.second, location.line, location.column)
    }

    override fun onConsoleLayoutChanged(expanded: Boolean, heightPx: Int) {
        store.consoleVisible = expanded
        store.consoleHeightDp = (heightPx / resources.displayMetrics.density).toInt()
    }

    // ---- ExtraKeysBar.Listener ----------------------------------------------------------------------------------------------------------------------------------

    override fun onInsertText(text: String) {
        editorView.insertText(text)
    }

    override fun onEditorAction(name: String) {
        editorView.runAction(name)
    }

    // ---- settings ----------------------------------------------------------------------------------------------------------------------------------------------------

    private fun onSettingChanged(key: String?) {
        when (key) {
            SettingsRepository.KEY_ACCENT,
            SettingsRepository.KEY_ACCENT_CUSTOM,
            SettingsRepository.KEY_THEME -> needsRecreate = true
            else -> {
                editorView.applySettings(settings.editorSettings())
                updateStatus()
            }
        }
    }

    private companion object {
        const val WIDE_LAYOUT_DP = 600
        const val MAX_INDEXED_FILES = 20000
        const val MAX_SEARCH_MATCHES = 2000
        const val SEARCH_BATCH = 25
        const val MENU_CLOSE = 1
        const val MENU_CLOSE_OTHERS = 2
        const val MENU_CLOSE_ALL = 3
    }
}
