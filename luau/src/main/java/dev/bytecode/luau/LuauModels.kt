package dev.bytecode.luau

/** Which stream a piece of runtime output belongs to. The order matches the native OutputChannel enum. */
enum class LuauChannel {
    STDOUT,
    WARNING,
    ERROR,
    INFO;

    companion object {
        fun fromCode(code: Int): LuauChannel = entries.getOrElse(code) { INFO }
    }
}

/** Result of a run. The order matches the native RunStatus enum. */
enum class LuauRunStatus {
    OK,
    SYNTAX_ERROR,
    RUNTIME_ERROR,
    STOPPED,
    TIMEOUT,
    OUT_OF_MEMORY,
    INTERNAL_ERROR;

    companion object {
        fun fromCode(code: Int): LuauRunStatus = entries.getOrElse(code) { INTERNAL_ERROR }
    }
}

data class LuauRunOptions(
    /** 0 disables the timeout. */
    val timeoutMillis: Long = 15_000L,
    /** 0 disables the memory limit. */
    val memoryLimitBytes: Long = 256L * 1024L * 1024L
)

data class LuauRunResult(
    val status: LuauRunStatus,
    val elapsedMillis: Long
)

/** A syntax problem reported by the Luau parser. Lines and columns are zero-based. */
data class LuauDiagnostic(
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val message: String
)
