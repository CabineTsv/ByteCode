package dev.bytecode.luau

/**
 * Receives text from the native runtime while a script is running.
 * Invoked on the runtime thread; implementations must hand the text over to the UI thread themselves.
 *
 * The native code looks up [onOutput] by name and signature, so do not rename it.
 */
fun interface LuauOutputSink {
    fun onOutput(channel: Int, text: String)
}

/**
 * Thin JNI declaration of the native library (see luau/native/src/jni_bridge.cpp).
 * Use [LuauRuntime] from application code instead of calling this directly.
 */
object LuauNative {

    /** False when the native library could not be loaded (for example on an unsupported ABI). */
    val isLoaded: Boolean = try {
        System.loadLibrary("bytecode_luau")
        true
    } catch (error: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic
    external fun nativeVersion(): String

    @JvmStatic
    external fun nativeRun(
        source: String,
        chunkName: String,
        timeoutMillis: Long,
        memoryLimitBytes: Long,
        sink: LuauOutputSink
    ): Int

    @JvmStatic
    external fun nativeRequestStop()

    @JvmStatic
    external fun nativeCheckSyntax(source: String, maxDiagnostics: Int): String
}
