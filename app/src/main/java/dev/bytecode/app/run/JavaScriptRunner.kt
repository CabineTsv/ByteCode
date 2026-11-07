package dev.bytecode.app.run

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Runs JavaScript in a hidden, sandboxed WebView (the device's Chromium). console.* is captured and
 * errors are reported with their line numbers. There is no network access (the app has no INTERNET
 * permission) and no DOM output is shown. The run ends when no timers or intervals are pending, when
 * the timeout expires, or when the user presses Stop.
 */
class JavaScriptRunner(private val context: Context) : CodeRunner {

    override val title: String = "JavaScript (WebView)"
    override val languageIds: Set<String> = setOf("javascript")

    @Volatile
    private var pending: CompletableDeferred<RunOutcome>? = null

    override fun isAvailable(): Boolean = true

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun run(request: RunRequest, output: RunOutput): RunOutcome = withContext(Dispatchers.Main) {
        val finished = CompletableDeferred<RunOutcome>()
        pending = finished

        val webView = WebView(context)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                finished.complete(RunOutcome(false, "The JavaScript engine crashed"))
                return true
            }
        }
        webView.addJavascriptInterface(Bridge(request, output, finished), BRIDGE_NAME)
        webView.onResume()
        webView.loadDataWithBaseURL("https://bytecode.invalid/", buildPage(request.text), "text/html", "UTF-8", null)

        val timeoutMillis = if (request.timeoutSeconds > 0) request.timeoutSeconds * 1000L else Long.MAX_VALUE
        val outcome = withTimeoutOrNull(timeoutMillis) { finished.await() }
            ?: RunOutcome(false, "Timed out after ${request.timeoutSeconds} s")

        pending = null
        webView.stopLoading()
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
        outcome
    }

    override fun stop() {
        pending?.complete(RunOutcome(false, "Stopped"))
    }

    private class Bridge(
        private val request: RunRequest,
        private val output: RunOutput,
        private val finished: CompletableDeferred<RunOutcome>
    ) {
        @JavascriptInterface
        fun log(level: String, text: String) {
            val severity = when (level) {
                "error" -> LineSeverity.ERROR
                "warn" -> LineSeverity.WARNING
                "info", "debug" -> LineSeverity.INFO
                else -> LineSeverity.NORMAL
            }
            for (line in text.split('\n')) output.print(severity, line)
        }

        @JavascriptInterface
        fun error(message: String, line: Int, column: Int, stack: String) {
            val location = if (line > 0) SourceLocation(request.documentKey, line, maxOf(column, 1)) else null
            output.print(LineSeverity.ERROR, message, location)
        }

        @JavascriptInterface
        fun done() {
            finished.complete(RunOutcome(true, "Finished"))
        }
    }

    private fun buildPage(code: String): String {
        // JSON.quote gives a valid JavaScript string literal; "<" is escaped so "</script>" cannot end the tag.
        val literal = JSONObject.quote(code).replace("<", "\\u003c")
        return "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><script>" +
            HARNESS.replace("CODE_LITERAL", literal) +
            "</script></body></html>"
    }

    private companion object {
        const val BRIDGE_NAME = "Runner"

        // Written without the dollar sign so it can live in a Kotlin raw string.
        val HARNESS = """
            (function () {
              var quiet = 0;
              var timers = {};
              var intervals = {};
              var nativeSetTimeout = window.setTimeout.bind(window);
              var nativeClearTimeout = window.clearTimeout.bind(window);
              var nativeSetInterval = window.setInterval.bind(window);
              var nativeClearInterval = window.clearInterval.bind(window);

              function format(args) {
                return Array.prototype.map.call(args, function (value) {
                  if (typeof value === 'string') return value;
                  try { return typeof value === 'object' ? JSON.stringify(value) : String(value); }
                  catch (e) { return String(value); }
                }).join(' ');
              }
              ['log', 'info', 'warn', 'error', 'debug'].forEach(function (level) {
                console[level] = function () { Runner.log(level, format(arguments)); };
              });
              window.alert = function (message) { Runner.log('warn', 'alert: ' + message); };
              window.confirm = function () { return false; };
              window.prompt = function () { return null; };

              window.addEventListener('error', function (event) {
                Runner.error(String(event.message), event.lineno | 0, event.colno | 0,
                  event.error && event.error.stack ? String(event.error.stack) : '');
              });
              window.addEventListener('unhandledrejection', function (event) {
                var reason = event.reason;
                Runner.error('Unhandled promise rejection: ' + (reason && reason.stack ? reason.stack : String(reason)), 0, 0, '');
              });

              window.setTimeout = function (fn, ms) {
                var args = Array.prototype.slice.call(arguments, 2);
                var id = nativeSetTimeout(function () { delete timers[id]; fn.apply(null, args); }, ms);
                timers[id] = true;
                return id;
              };
              window.clearTimeout = function (id) { delete timers[id]; nativeClearTimeout(id); };
              window.setInterval = function (fn, ms) {
                var args = Array.prototype.slice.call(arguments, 2);
                var id = nativeSetInterval(function () { fn.apply(null, args); }, ms);
                intervals[id] = true;
                return id;
              };
              window.clearInterval = function (id) { delete intervals[id]; nativeClearInterval(id); };

              function check() {
                var pendingCount = Object.keys(timers).length + Object.keys(intervals).length;
                quiet = pendingCount === 0 ? quiet + 1 : 0;
                if (quiet >= 3) { Runner.done(); } else { nativeSetTimeout(check, 50); }
              }

              var code = CODE_LITERAL;
              try {
                var script = document.createElement('script');
                script.textContent = code;
                document.body.appendChild(script);
              } catch (e) {
                Runner.error(String(e), 0, 0, '');
              }
              nativeSetTimeout(check, 50);
            })();
        """.trimIndent()
    }
}
