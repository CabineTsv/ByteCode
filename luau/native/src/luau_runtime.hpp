// Public interface of the native Luau layer. Nothing in this header depends on JNI or on Luau
// headers, so it can be reused (and unit-tested) outside Android.
#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace bytecode {

// Must stay in sync with dev.bytecode.luau.LuauChannel.
enum class OutputChannel : int { Stdout = 0, Warning = 1, Error = 2, Info = 3 };

// Must stay in sync with dev.bytecode.luau.LuauRunStatus.
enum class RunStatus : int {
    Ok = 0,
    SyntaxError = 1,
    RuntimeError = 2,
    Stopped = 3,
    Timeout = 4,
    OutOfMemory = 5,
    InternalError = 6
};

struct RunOptions {
    int64_t timeoutMillis = 0;                    // 0 = no timeout
    int64_t memoryLimitBytes = 0;                 // 0 = no limit
    int64_t maxOutputBytes = 4 * 1024 * 1024;     // output beyond this is dropped
};

struct Diagnostic {
    int line = 0;       // zero-based
    int column = 0;     // zero-based
    int endLine = 0;
    int endColumn = 0;
    std::string message;
};

using OutputCallback = std::function<void(OutputChannel, const std::string&)>;

// Compiles and runs one script on a fresh, sandboxed Luau state. Blocks until the script ends.
// Text passed to `emit` is UTF-8 and may contain several lines.
RunStatus runScript(const std::string& source,
                    const std::string& chunkName,
                    const RunOptions& options,
                    const OutputCallback& emit);

// Asks the script that is currently running (if any) to stop at its next safepoint.
void requestStop();

// Parses the source with the official Luau parser and returns its syntax errors.
std::vector<Diagnostic> checkSyntax(const std::string& source, size_t maxDiagnostics);

// The Luau release tag this library was built from.
std::string luauVersionString();

}  // namespace bytecode
