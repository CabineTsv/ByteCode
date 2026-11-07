package dev.bytecode.luau

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Kotlin-side manager for the native Luau runtime.
 *
 * - Scripts run one at a time on a dedicated background thread.
 * - Syntax checks run on a second thread so linting stays responsive while a script is running.
 * - If the native library cannot be loaded, [isAvailable] is false and the app hides Luau execution.
 *
 * This is the standalone Luau language runtime. It does not provide the Roblox engine.
 */
class LuauRuntime {

    val isAvailable: Boolean
        get() = LuauNative.isLoaded

    /** The Luau release tag the native library was built from. */
    val version: String by lazy {
        if (isAvailable) {
            try {
                LuauNative.nativeVersion()
            } catch (error: UnsatisfiedLinkError) {
                "unknown"
            }
        } else {
            "unavailable"
        }
    }

    private val runDispatcher = Executors.newSingleThreadExecutor(daemonThreads("bytecode-luau-run"))
        .asCoroutineDispatcher()
    private val checkDispatcher = Executors.newSingleThreadExecutor(daemonThreads("bytecode-luau-check"))
        .asCoroutineDispatcher()

    /**
     * Compiles and runs [source]. [onOutput] is called on the runtime thread for every chunk of output
     * (a chunk may contain several lines and always ends with a newline).
     */
    suspend fun run(
        source: String,
        chunkName: String,
        options: LuauRunOptions = LuauRunOptions(),
        onOutput: (LuauChannel, String) -> Unit
    ): LuauRunResult = withContext(runDispatcher) {
        if (!isAvailable) {
            return@withContext LuauRunResult(LuauRunStatus.INTERNAL_ERROR, 0L)
        }
        val startedAt = System.nanoTime()
        val sink = LuauOutputSink { code, text -> onOutput(LuauChannel.fromCode(code), text) }
        val statusCode = try {
            LuauNative.nativeRun(source, chunkName, options.timeoutMillis, options.memoryLimitBytes, sink)
        } catch (error: UnsatisfiedLinkError) {
            LuauRunStatus.INTERNAL_ERROR.ordinal
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
        LuauRunResult(LuauRunStatus.fromCode(statusCode), elapsedMillis)
    }

    /** Asks the running script to stop. Safe to call from any thread, and when nothing is running. */
    fun stop() {
        if (isAvailable) {
            LuauNative.nativeRequestStop()
        }
    }

    /** Syntax errors found by the official Luau parser. Empty when the library is unavailable. */
    suspend fun checkSyntax(source: String, maxDiagnostics: Int = 50): List<LuauDiagnostic> =
        withContext(checkDispatcher) {
            if (!isAvailable) {
                return@withContext emptyList()
            }
            val encoded = try {
                LuauNative.nativeCheckSyntax(source, maxDiagnostics)
            } catch (error: UnsatisfiedLinkError) {
                ""
            }
            parseDiagnostics(encoded)
        }

    private fun parseDiagnostics(encoded: String): List<LuauDiagnostic> {
        val result = ArrayList<LuauDiagnostic>()
        for (line in encoded.lineSequence()) {
            if (line.isEmpty()) continue
            val parts = line.split('\t', limit = 5)
            if (parts.size < 5) continue
            result.add(
                LuauDiagnostic(
                    line = parts[0].toIntOrNull() ?: 0,
                    column = parts[1].toIntOrNull() ?: 0,
                    endLine = parts[2].toIntOrNull() ?: 0,
                    endColumn = parts[3].toIntOrNull() ?: 0,
                    message = parts[4]
                )
            )
        }
        return result
    }

    private fun daemonThreads(name: String) = ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }
}
