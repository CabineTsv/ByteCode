package dev.bytecode.app.run

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.bytecode.app.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class LineSeverity { NORMAL, INFO, WARNING, ERROR, SUCCESS }

/** A place in a source file that a console line refers to. [line] and [column] are 1-based. */
data class SourceLocation(val documentKey: String, val line: Int, val column: Int)

/** Where a runner writes its output. Safe to call from any thread. */
interface RunOutput {
    fun print(severity: LineSeverity, text: String, location: SourceLocation? = null)
}

/** The console as seen by [RunManager]. */
interface RunConsole : RunOutput {
    /** Shows the Output channel and optionally clears it before a new run. */
    fun beginRun(header: String, clear: Boolean)
}

class RunRequest(
    /** Identifies the document (its URI) so console lines can link back to it. */
    val documentKey: String,
    val documentName: String,
    val languageId: String,
    val text: String,
    /** 0 means no timeout. */
    val timeoutSeconds: Int
)

class RunOutcome(val success: Boolean, val summary: String? = null)

/** Executes source code of some languages. Only runners that report [isAvailable] are offered to the user. */
interface CodeRunner {
    val title: String
    val languageIds: Set<String>
    fun isAvailable(): Boolean
    suspend fun run(request: RunRequest, output: RunOutput): RunOutcome

    /** Asks a running execution to stop. Safe to call from any thread. */
    fun stop()
}

/** Chooses a runner by language, runs one program at a time and reports progress to the console. */
class RunManager(
    private val scope: CoroutineScope,
    private val runners: List<CodeRunner>,
    private val console: RunConsole,
    private val settings: SettingsRepository,
    private val listener: Listener
) {
    interface Listener {
        fun onRunStateChanged(running: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var activeRunner: CodeRunner? = null

    var isRunning: Boolean = false
        private set

    /** Output may arrive from any thread; it is always delivered to the console on the main thread. */
    private val mainThreadOutput = object : RunOutput {
        override fun print(severity: LineSeverity, text: String, location: SourceLocation?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                console.print(severity, text, location)
            } else {
                mainHandler.post { console.print(severity, text, location) }
            }
        }
    }

    fun runnerFor(languageId: String): CodeRunner? =
        runners.firstOrNull { languageId in it.languageIds && it.isAvailable() }

    fun canRun(languageId: String): Boolean = runnerFor(languageId) != null

    fun run(request: RunRequest) {
        if (isRunning) return
        val runner = runnerFor(request.languageId) ?: return
        isRunning = true
        activeRunner = runner
        listener.onRunStateChanged(true)
        console.beginRun("▶ ${request.documentName} — ${runner.title}", settings.clearOutputBeforeRun)

        job = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val outcome = try {
                runner.run(request, mainThreadOutput)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RunOutcome(false, error.message ?: error.javaClass.simpleName)
            }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val summary = outcome.summary ?: if (outcome.success) "Finished" else "Failed"
            // Messages from the runner thread are posted to the main thread; post this one behind them.
            mainHandler.post {
                console.print(
                    if (outcome.success) LineSeverity.SUCCESS else LineSeverity.ERROR,
                    "■ $summary (${elapsed} ms)"
                )
                isRunning = false
                activeRunner = null
                listener.onRunStateChanged(false)
            }
        }
    }

    fun stop() {
        activeRunner?.stop()
    }

    fun destroy() {
        activeRunner?.stop()
        job?.cancel()
    }
}
