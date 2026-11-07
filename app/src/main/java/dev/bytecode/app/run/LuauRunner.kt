package dev.bytecode.app.run

import dev.bytecode.luau.LuauChannel
import dev.bytecode.luau.LuauRunOptions
import dev.bytecode.luau.LuauRunStatus
import dev.bytecode.luau.LuauRuntime

/**
 * Runs Luau with the official Luau VM (native library). This is the standalone language runtime:
 * it has no Roblox engine, so Roblox globals raise an explanatory error.
 */
class LuauRunner(private val runtime: LuauRuntime) : CodeRunner {

    override val title: String = "Luau"
    override val languageIds: Set<String> = setOf("luau", "lua")

    override fun isAvailable(): Boolean = runtime.isAvailable

    override suspend fun run(request: RunRequest, output: RunOutput): RunOutcome {
        if (request.languageId == "lua") {
            output.print(
                LineSeverity.INFO,
                "Running a .lua file on the Luau VM: Lua 5.x libraries such as io and os.execute are not available."
            )
        }
        val options = LuauRunOptions(
            timeoutMillis = request.timeoutSeconds * 1000L,
            memoryLimitBytes = MEMORY_LIMIT_BYTES
        )
        val result = runtime.run(request.text, "@" + request.documentName, options) { channel, text ->
            forward(channel, text, request, output)
        }
        return when (result.status) {
            LuauRunStatus.OK -> RunOutcome(true, "Finished")
            LuauRunStatus.SYNTAX_ERROR -> RunOutcome(false, "Syntax error")
            LuauRunStatus.RUNTIME_ERROR -> RunOutcome(false, "Runtime error")
            LuauRunStatus.STOPPED -> RunOutcome(false, "Stopped")
            LuauRunStatus.TIMEOUT -> RunOutcome(false, "Timed out")
            LuauRunStatus.OUT_OF_MEMORY -> RunOutcome(false, "Out of memory")
            LuauRunStatus.INTERNAL_ERROR -> RunOutcome(false, "Internal error")
        }
    }

    override fun stop() {
        runtime.stop()
    }

    private fun forward(channel: LuauChannel, text: String, request: RunRequest, output: RunOutput) {
        val severity = when (channel) {
            LuauChannel.STDOUT -> LineSeverity.NORMAL
            LuauChannel.WARNING -> LineSeverity.WARNING
            LuauChannel.ERROR -> LineSeverity.ERROR
            LuauChannel.INFO -> LineSeverity.INFO
        }
        val lines = text.split('\n').let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }
        for (line in lines) {
            val location = if (channel == LuauChannel.ERROR) locationOf(line, request.documentKey) else null
            output.print(severity, line, location)
        }
    }

    // "main.luau:12: message" and traceback lines such as "main.luau:12 function foo".
    private fun locationOf(line: String, documentKey: String): SourceLocation? {
        val match = LOCATION.find(line) ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        return SourceLocation(documentKey, number, 1)
    }

    private companion object {
        const val MEMORY_LIMIT_BYTES = 256L * 1024L * 1024L
        val LOCATION = Regex("^\\s*[^:\\s][^:]*:(\\d+)(?:[: ]|$)")
    }
}
