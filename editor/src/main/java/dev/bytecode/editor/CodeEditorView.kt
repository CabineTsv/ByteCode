package dev.bytecode.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * The code editor: CodeMirror 6 running inside a WebView, loaded from the app's own assets
 * (https://appassets.androidplatform.net/assets/editor/index.html) so it works offline.
 *
 * One WebView and one CodeMirror view serve every open document; switching documents swaps editor
 * states inside JavaScript and never reloads the page.
 *
 * Commands to the editor are queued until the page reports that it is ready. Events from the editor
 * arrive on the main thread through [listener].
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onEditorEvent(event: EditorEvent)
    }

    var listener: Listener? = null

    /** Directory of imported fonts; files in it are served to the page at /fonts/<name>. */
    var fontDirectory: File? = null

    val isEditorReady: Boolean
        get() = ready

    private val mainHandler = Handler(Looper.getMainLooper())
    private val contentStore = DocumentContentStore()
    private val textCallbacks = HashMap<String, (String?) -> Unit>()
    private val pendingScripts = ArrayList<String>()
    private val requestCounter = AtomicInteger(0)
    private var webView: WebView? = null
    private var ready = false
    private var currentSettings = EditorSettings()
    private var watchdog: Runnable? = null

    // ---- lifecycle ---------------------------------------------------------------------------------------

    /** Creates the WebView and loads the editor. Call once; use [restart] after the renderer died. */
    fun start(initialSettings: EditorSettings) {
        currentSettings = initialSettings
        createWebView()
    }

    /** Throws the current WebView away and starts a fresh one. Open documents must be reopened afterwards. */
    fun restart() {
        createWebView()
    }

    fun onResume() {
        webView?.onResume()
    }

    fun onPause() {
        webView?.onPause()
    }

    fun destroy() {
        destroyWebView()
        contentStore.clear()
        textCallbacks.clear()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        destroyWebView()
        ready = false
        pendingScripts.clear()
        textCallbacks.clear()

        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val view = WebView(context)
        view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        view.setBackgroundColor(if (currentSettings.darkTheme) DARK_BACKGROUND else LIGHT_BACKGROUND)
        view.overScrollMode = OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false

        val webSettings = view.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = false
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = false
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.textZoom = 100 // the editor font size is a setting; do not scale it with the system font size
        webSettings.mediaPlaybackRequiresUserGesture = true

        val assetLoader = buildAssetLoader()
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(uri)
            }

            // The page never navigates anywhere else.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val crashed = detail?.didCrash() ?: true
                mainHandler.post { handleRenderProcessGone(view, crashed) }
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "error"
                        ConsoleMessage.MessageLevel.WARNING -> "warn"
                        else -> null
                    }
                    if (level != null) {
                        val source = consoleMessage.sourceId().orEmpty().substringAfterLast('/')
                        dispatchEvent(EditorEvent.Log(level, "${consoleMessage.message()} ($source:${consoleMessage.lineNumber()})"))
                    }
                }
                return true
            }
        }
        view.addJavascriptInterface(Bridge(), BRIDGE_NAME)

        addView(view)
        webView = view
        send(command("applySettings").put("settings", currentSettings.toJson()))
        view.loadUrl(START_URL)
        scheduleWatchdog()
    }

    private fun destroyWebView() {
        cancelWatchdog()
        val view = webView ?: return
        webView = null
        ready = false
        removeView(view)
        view.stopLoading()
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    private fun handleRenderProcessGone(gone: WebView?, didCrash: Boolean) {
        if (gone == null || gone !== webView) return
        destroyWebView()
        dispatchEvent(EditorEvent.RendererGone(didCrash))
    }

    private fun scheduleWatchdog() {
        cancelWatchdog()
        val task = Runnable {
            if (!ready) {
                dispatchEvent(
                    EditorEvent.Log(
                        "error",
                        "The editor page did not start. The CodeMirror bundle is probably missing from the APK."
                    )
                )
                dispatchEvent(EditorEvent.StartupTimeout)
            }
        }
        watchdog = task
        mainHandler.postDelayed(task, STARTUP_TIMEOUT_MILLIS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
    }

    // ---- serving local content to the page ---------------------------------------------------------------

    private fun buildAssetLoader(): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/docs/") { path -> serveDocument(path) }
            .addPathHandler("/fonts/") { path -> serveFont(path) }
            .build()

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun serveDocument(token: String): WebResourceResponse {
        val bytes = contentStore.take(token) ?: return notFound()
        return WebResourceResponse(
            "text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes)
        )
    }

    private fun serveFont(path: String): WebResourceResponse {
        val directory = fontDirectory ?: return notFound()
        val name = path.substringAfterLast('/')
        val file = File(directory, name)
        if (name.isEmpty() || !file.isFile || file.parentFile?.canonicalPath != directory.canonicalPath) {
            return notFound()
        }
        return WebResourceResponse("font/ttf", null, 200, "OK", mapOf("Cache-Control" to "no-store"), file.inputStream())
    }

    // ---- commands to the editor --------------------------------------------------------------------------

    private fun command(name: String): JSONObject = JSONObject().put("cmd", name)

    private fun send(command: JSONObject) {
        val script = "window.ByteCodeHost && window.ByteCodeHost.dispatch(" + JSONObject.quote(command.toString()) + ")"
        if (!ready) {
            pendingScripts.add(script)
            return
        }
        webView?.evaluateJavascript(script, null)
    }

    private fun flushPendingScripts() {
        val view = webView ?: return
        val scripts = ArrayList(pendingScripts)
        pendingScripts.clear()
        for (script in scripts) {
            view.evaluateJavascript(script, null)
        }
    }

    fun applySettings(settings: EditorSettings) {
        currentSettings = settings
        webView?.setBackgroundColor(if (settings.darkTheme) DARK_BACKGROUND else LIGHT_BACKGROUND)
        send(command("applySettings").put("settings", settings.toJson()))
    }

    /**
     * Opens a document. [id] must contain only letters and digits. When [restoreText] is given, the
     * document is opened with [text] as its saved baseline and then replaced by [restoreText], so it
     * starts out modified (used to restore unsaved changes).
     */
    fun openDocument(
        id: String,
        name: String,
        languageId: String,
        text: String,
        largeFile: Boolean,
        readOnly: Boolean,
        activate: Boolean,
        restoreText: String? = null
    ) {
        contentStore.put(id, text)
        val open = command("openDocument")
            .put("id", id)
            .put("name", name)
            .put("language", languageId)
            .put("contentUrl", "/docs/$id")
            .put("largeFile", largeFile)
            .put("readOnly", readOnly)
            .put("activate", activate)
        if (restoreText != null) {
            contentStore.put("$id-restore", restoreText)
            open.put("restoreUrl", "/docs/$id-restore")
        }
        send(open)
    }

    fun activateDocument(id: String) {
        send(command("activateDocument").put("id", id))
    }

    fun closeDocument(id: String) {
        send(command("closeDocument").put("id", id))
    }

    fun renameDocument(id: String, name: String, languageId: String) {
        send(command("renameDocument").put("id", id).put("name", name).put("language", languageId))
    }

    /**
     * Fetches the current text of a document. [callback] receives the text (null if the document is
     * unknown) and the request id, which should be passed to [markSaved] after writing the file.
     */
    fun requestText(docId: String, callback: (text: String?, requestId: String) -> Unit) {
        val requestId = "r" + requestCounter.incrementAndGet()
        textCallbacks[requestId] = { text -> callback(text, requestId) }
        send(command("requestText").put("id", docId).put("requestId", requestId))
    }

    /** Tells the editor that the text it handed out for [requestId] has been written to disk. */
    fun markSaved(docId: String, requestId: String) {
        send(command("markSaved").put("id", docId).put("requestId", requestId))
    }

    fun insertText(text: String) {
        send(command("insertText").put("text", text))
    }

    /** Runs an editor action: undo, redo, find, replace, gotoLine, toggleComment, foldAll, cursorLeft, ... */
    fun runAction(name: String) {
        send(command("action").put("name", name))
    }

    /** Moves the caret to a 1-based line/column and scrolls it into view. */
    fun revealPosition(docId: String?, line: Int, column: Int, focus: Boolean) {
        val reveal = command("revealPosition").put("line", line).put("column", column).put("focus", focus)
        if (docId != null) reveal.put("id", docId)
        send(reveal)
    }

    fun focusEditor() {
        send(command("focus"))
    }

    fun resolveLint(requestId: String, diagnostics: List<EditorDiagnostic>) {
        val items = JSONArray()
        for (diagnostic in diagnostics) {
            items.put(
                JSONObject()
                    .put("line", diagnostic.line)
                    .put("column", diagnostic.column)
                    .put("endLine", diagnostic.endLine)
                    .put("endColumn", diagnostic.endColumn)
                    .put("severity", diagnostic.severity.wireName)
                    .put("message", diagnostic.message)
            )
        }
        send(command("lintResult").put("requestId", requestId).put("items", items))
    }

    // ---- events from the editor ----------------------------------------------------------------------------

    private fun dispatchEvent(event: EditorEvent) {
        listener?.onEditorEvent(event)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            mainHandler.post { handleMessage(json) }
        }
    }

    private fun handleMessage(json: String) {
        val message = try {
            JSONObject(json)
        } catch (error: JSONException) {
            return
        }
        val docId = message.optString("docId")
        when (message.optString("type")) {
            "ready" -> {
                ready = true
                cancelWatchdog()
                flushPendingScripts()
                dispatchEvent(EditorEvent.Ready)
            }
            "documentOpened" -> dispatchEvent(EditorEvent.DocumentOpened(docId, message.optBoolean("dirty")))
            "documentFailed" -> dispatchEvent(EditorEvent.DocumentFailed(docId, message.optString("message")))
            "dirty" -> dispatchEvent(EditorEvent.DirtyChanged(docId, message.optBoolean("dirty")))
            "edited" -> dispatchEvent(EditorEvent.Edited(docId))
            "cursor" -> dispatchEvent(
                EditorEvent.CursorMoved(
                    docId = docId,
                    line = message.optInt("line", 1),
                    column = message.optInt("column", 1),
                    selectedChars = message.optInt("selected", 0),
                    totalLines = message.optInt("lines", 1)
                )
            )
            "text" -> {
                val callback = textCallbacks.remove(message.optString("requestId"))
                val text = if (message.isNull("text")) null else message.optString("text")
                callback?.invoke(text)
            }
            "lint" -> dispatchEvent(
                EditorEvent.LintRequest(
                    requestId = message.optString("requestId"),
                    docId = docId,
                    language = message.optString("language"),
                    text = message.optString("text")
                )
            )
            "diagnostics" -> dispatchEvent(EditorEvent.Diagnostics(docId, parseDiagnostics(message.optJSONArray("items"))))
            "command" -> dispatchEvent(EditorEvent.Command(message.optString("name")))
            "log" -> dispatchEvent(EditorEvent.Log(message.optString("level", "info"), message.optString("message")))
        }
    }

    private fun parseDiagnostics(items: JSONArray?): List<EditorDiagnostic> {
        if (items == null) return emptyList()
        val result = ArrayList<EditorDiagnostic>(items.length())
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val line = item.optInt("line", 1)
            val column = item.optInt("column", 1)
            result.add(
                EditorDiagnostic(
                    line = line,
                    column = column,
                    endLine = line,
                    endColumn = column,
                    severity = DiagnosticSeverity.fromWireName(item.optString("severity")),
                    message = item.optString("message")
                )
            )
        }
        return result
    }

    private companion object {
        const val BRIDGE_NAME = "AndroidHost"
        const val START_URL = "https://appassets.androidplatform.net/assets/editor/index.html"
        const val STARTUP_TIMEOUT_MILLIS = 10_000L
        const val DARK_BACKGROUND = 0xFF1B1D21.toInt()
        const val LIGHT_BACKGROUND = 0xFFFBFBFC.toInt()
    }
}
