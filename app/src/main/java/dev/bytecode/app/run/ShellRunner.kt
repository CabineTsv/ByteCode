package dev.bytecode.app.run

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs a shell script with the device's own /system/bin/sh. Only the commands that ship with Android
 * (toybox) are available, and the script runs with this app's permissions inside its cache folder.
 */
class ShellRunner(private val context: Context) : CodeRunner {

    override val title: String = "Shell (device sh)"
    override val languageIds: Set<String> = setOf("shell")

    private val shell = File("/system/bin/sh")

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopRequested = false

    override fun isAvailable(): Boolean = shell.canExecute()

    override suspend fun run(request: RunRequest, output: RunOutput): RunOutcome = withContext(Dispatchers.IO) {
        stopRequested = false
        val directory = File(context.cacheDir, "run").also { it.mkdirs() }
        val script = File(directory, "script.sh")
        script.writeText(request.text.replace("\r\n", "\n"), Charsets.UTF_8)

        val started = try {
            ProcessBuilder(shell.path, script.path).directory(directory).start()
        } catch (error: IOException) {
            return@withContext RunOutcome(false, "Could not start the shell: ${error.message}")
        }
        process = started

        val stdout = thread(name = "bytecode-shell-out") { pump(started.inputStream, LineSeverity.NORMAL, output) }
        val stderr = thread(name = "bytecode-shell-err") { pump(started.errorStream, LineSeverity.ERROR, output) }

        val finishedInTime = if (request.timeoutSeconds > 0) {
            started.waitFor(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } else {
            started.waitFor()
            true
        }
        if (!finishedInTime) started.destroyForcibly()
        stdout.join(1000)
        stderr.join(1000)
        process = null
        script.delete()

        when {
            stopRequested -> RunOutcome(false, "Stopped")
            !finishedInTime -> RunOutcome(false, "Timed out after ${request.timeoutSeconds} s")
            else -> {
                val code = started.exitValue()
                RunOutcome(code == 0, "Exit code $code")
            }
        }
    }

    override fun stop() {
        stopRequested = true
        process?.destroyForcibly()
    }

    private fun pump(stream: InputStream, severity: LineSeverity, output: RunOutput) {
        try {
            stream.bufferedReader(Charsets.UTF_8).forEachLine { line -> output.print(severity, line) }
        } catch (error: IOException) {
            // The process was destroyed while reading; nothing more to report.
        }
    }
}
