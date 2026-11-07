// Runs standalone Luau scripts on the official Luau VM.
//
// This is NOT a Roblox environment. Only the language and its standard libraries are available;
// Roblox engine globals (game, Instance, task, ...) are replaced by an explanatory error.
#include "luau_runtime.hpp"

#include <atomic>
#include <chrono>
#include <cstdlib>
#include <exception>
#include <string>
#include <unordered_set>

#include "lua.h"
#include "luacode.h"
#include "lualib.h"

#ifndef BYTECODE_LUAU_TAG
#define BYTECODE_LUAU_TAG "unknown"
#endif

namespace bytecode {
namespace {

using Clock = std::chrono::steady_clock;

// Only one script runs at a time (the Kotlin layer serialises runs); these flags are the only
// state shared with other threads.
std::atomic<bool> gRunning{false};
std::atomic<bool> gStopRequested{false};

// ---- Allocator with an optional memory ceiling ------------------------------------------------

struct AllocatorState {
    size_t used = 0;
    size_t limit = 0;  // 0 = unlimited
};

void* limitedAllocator(void* userdata, void* ptr, size_t oldSize, size_t newSize) {
    auto* state = static_cast<AllocatorState*>(userdata);
    if (newSize == 0) {
        if (ptr != nullptr) {
            state->used -= oldSize;
            std::free(ptr);
        }
        return nullptr;
    }
    // When ptr is null, oldSize encodes an object type and must be ignored.
    const size_t previous = (ptr != nullptr) ? oldSize : 0;
    if (state->limit != 0 && state->used - previous + newSize > state->limit) {
        return nullptr;
    }
    void* result = std::realloc(ptr, newSize);
    if (result != nullptr) {
        state->used = state->used - previous + newSize;
    }
    return result;
}

// ---- Per-run context (reachable from callbacks through lua_callbacks(L)->userdata) -------------

class RunContext {
public:
    RunContext(const OutputCallback& emit, const RunOptions& options)
        : emit_(emit), options_(options), lastFlush_(Clock::now()) {}

    void begin() {
        const Clock::time_point now = Clock::now();
        lastFlush_ = now;
        if (options_.timeoutMillis > 0) {
            hasDeadline_ = true;
            deadline_ = now + std::chrono::milliseconds(options_.timeoutMillis);
        }
    }

    void write(OutputChannel channel, const std::string& text) {
        if (truncated_) return;
        if (outputBytes_ + text.size() > static_cast<size_t>(options_.maxOutputBytes)) {
            flush();
            emit_(OutputChannel::Warning, "[output truncated: the output limit was reached]\n");
            truncated_ = true;
            return;
        }
        outputBytes_ += text.size();
        if (!pending_.empty() && channel != pendingChannel_) flush();
        pendingChannel_ = channel;
        pending_ += text;
        if (pending_.size() >= 4096 || Clock::now() - lastFlush_ >= std::chrono::milliseconds(30)) {
            flush();
        }
    }

    void flush() {
        if (!pending_.empty()) {
            emit_(pendingChannel_, pending_);
            pending_.clear();
        }
        lastFlush_ = Clock::now();
    }

    // Called from the interrupt callback. Returns true when the script must be aborted.
    bool pollAbort() {
        if (gStopRequested.load(std::memory_order_relaxed)) {
            stopped = true;
            return true;
        }
        if ((++pollCounter_ & 0x3FFu) != 0) return false;
        const Clock::time_point now = Clock::now();
        if (hasDeadline_ && now >= deadline_) {
            timedOut = true;
            return true;
        }
        if (!pending_.empty() && now - lastFlush_ >= std::chrono::milliseconds(100)) flush();
        return false;
    }

    bool stopped = false;
    bool timedOut = false;

private:
    const OutputCallback& emit_;
    const RunOptions& options_;
    Clock::time_point lastFlush_;
    Clock::time_point deadline_;
    bool hasDeadline_ = false;
    uint32_t pollCounter_ = 0;
    size_t outputBytes_ = 0;
    bool truncated_ = false;
    std::string pending_;
    OutputChannel pendingChannel_ = OutputChannel::Stdout;
};

RunContext* contextOf(lua_State* L) {
    return static_cast<RunContext*>(lua_callbacks(L)->userdata);
}

// ---- Host functions -----------------------------------------------------------------------------

int printTo(lua_State* L, OutputChannel channel) {
    RunContext* context = contextOf(L);
    std::string line;
    const int count = lua_gettop(L);
    for (int i = 1; i <= count; ++i) {
        size_t length = 0;
        const char* text = luaL_tolstring(L, i, &length);  // honours __tostring
        if (i > 1) line.push_back('\t');
        if (text != nullptr) line.append(text, length);
        lua_pop(L, 1);
    }
    line.push_back('\n');
    if (context != nullptr) context->write(channel, line);
    return 0;
}

int luaPrint(lua_State* L) { return printTo(L, OutputChannel::Stdout); }
int luaWarn(lua_State* L) { return printTo(L, OutputChannel::Warning); }

// Names that exist in Roblox but not in a standalone Luau VM.
const std::unordered_set<std::string>& robloxOnlyGlobals() {
    static const std::unordered_set<std::string> names = {
        "game", "workspace", "script", "plugin", "shared", "settings", "UserSettings", "stats",
        "Instance", "Enum", "Vector3", "Vector2", "Vector3int16", "Vector2int16", "CFrame", "UDim",
        "UDim2", "Color3", "BrickColor", "Ray", "Region3", "Region3int16", "Rect", "TweenInfo",
        "RaycastParams", "OverlapParams", "NumberRange", "NumberSequence", "NumberSequenceKeypoint",
        "ColorSequence", "ColorSequenceKeypoint", "PhysicalProperties", "Random", "DateTime", "Font",
        "Axes", "Faces", "task", "wait", "spawn", "delay", "tick", "time", "elapsedTime",
        "printidentity", "version"};
    return names;
}

// __index handler of the globals table: reading an unknown global yields nil, except for names that
// only exist in Roblox, which raise an explanatory error instead of failing later in a confusing way.
int unknownGlobalTrap(lua_State* L) {
    if (lua_type(L, 2) == LUA_TSTRING) {
        const char* name = lua_tostring(L, 2);
        if (name != nullptr) {
            if (std::string(name) == "require") {
                luaL_error(L,
                           "require() is not available: the standalone Luau runtime has no module loader. "
                           "Put the code in one file.");
            }
            if (robloxOnlyGlobals().count(name) != 0) {
                luaL_error(L,
                           "'%s' is part of the Roblox engine and is not available in ByteCode's standalone "
                           "Luau runtime (it runs the Luau language only, without Instance, DataModel, "
                           "task scheduler or Roblox datatypes).",
                           name);
            }
        }
    }
    lua_pushnil(L);
    return 1;
}

void installHostEnvironment(lua_State* L) {
    lua_pushcfunction(L, luaPrint, "print");
    lua_setglobal(L, "print");
    lua_pushcfunction(L, luaWarn, "warn");
    lua_setglobal(L, "warn");

    // Metatable for the (soon read-only) globals table.
    lua_newtable(L);
    lua_pushcfunction(L, unknownGlobalTrap, "__index");
    lua_setfield(L, -2, "__index");
    lua_setmetatable(L, LUA_GLOBALSINDEX);
}

void interruptCallback(lua_State* L, int gc) {
    if (gc >= 0) return;  // garbage collector safepoint, not a script safepoint
    RunContext* context = contextOf(L);
    if (context == nullptr || !context->pollAbort()) return;
    lua_rawcheckstack(L, 1);  // reserve room for the error message
    luaL_error(L, context->timedOut ? "execution timed out" : "execution stopped");
}

struct StateGuard {
    lua_State* state;
    ~StateGuard() {
        if (state != nullptr) lua_close(state);
    }
};

std::string describeError(lua_State* thread) {
    if (const char* text = lua_tostring(thread, -1)) return text;
    return std::string("(error object is a ") + luaL_typename(thread, -1) + " value)";
}

RunStatus executeScript(const std::string& source,
                        const std::string& chunkName,
                        const RunOptions& options,
                        const OutputCallback& emit) {
    AllocatorState allocatorState;
    if (options.memoryLimitBytes > 0) {
        allocatorState.limit = static_cast<size_t>(options.memoryLimitBytes);
    }
    RunContext context(emit, options);

    lua_State* L = lua_newstate(limitedAllocator, &allocatorState);
    if (L == nullptr) {
        emit(OutputChannel::Error, "Could not create a Luau state (out of memory).\n");
        return RunStatus::InternalError;
    }
    StateGuard guard{L};

    lua_callbacks(L)->userdata = &context;
    lua_callbacks(L)->interrupt = interruptCallback;

    luaL_openlibs(L);
    installHostEnvironment(L);
    luaL_sandbox(L);
    lua_State* thread = lua_newthread(L);  // anchored on L's stack
    luaL_sandboxthread(thread);

    // ---- compile ----
    lua_CompileOptions compileOptions = {};
    compileOptions.optimizationLevel = 1;
    compileOptions.debugLevel = 1;
    size_t bytecodeSize = 0;
    char* bytecode = luau_compile(source.data(), source.size(), &compileOptions, &bytecodeSize);
    if (bytecode == nullptr) {
        emit(OutputChannel::Error, "The Luau compiler ran out of memory.\n");
        return RunStatus::OutOfMemory;
    }
    const int loadResult = luau_load(thread, chunkName.c_str(), bytecode, bytecodeSize, 0);
    std::free(bytecode);
    if (loadResult != 0) {
        emit(OutputChannel::Error, describeError(thread) + "\n");
        return RunStatus::SyntaxError;
    }

    // ---- run ----
    context.begin();
    const int resumeStatus = lua_resume(thread, nullptr, 0);
    context.flush();

    if (resumeStatus == LUA_OK) {
        return RunStatus::Ok;
    }
    if (resumeStatus == LUA_YIELD) {
        emit(OutputChannel::Warning,
             "The script yielded at the top level. The standalone runtime has no scheduler, so it was "
             "stopped here.\n");
        return RunStatus::Ok;
    }
    if (context.timedOut) {
        emit(OutputChannel::Error,
             "Execution timed out after " + std::to_string(options.timeoutMillis) + " ms.\n");
        return RunStatus::Timeout;
    }
    if (context.stopped) {
        emit(OutputChannel::Info, "Execution stopped.\n");
        return RunStatus::Stopped;
    }

    std::string message = describeError(thread);
    if (const char* trace = lua_debugtrace(thread)) {
        message += "\nstack traceback:\n";
        message += trace;
    }
    emit(OutputChannel::Error, message + "\n");
    return resumeStatus == LUA_ERRMEM ? RunStatus::OutOfMemory : RunStatus::RuntimeError;
}

}  // namespace

RunStatus runScript(const std::string& source,
                    const std::string& chunkName,
                    const RunOptions& options,
                    const OutputCallback& emit) {
    gStopRequested.store(false);
    gRunning.store(true);
    RunStatus status = RunStatus::InternalError;
    try {
        status = executeScript(source, chunkName, options, emit);
    } catch (const std::exception& error) {
        emit(OutputChannel::Error, std::string("Internal runtime error: ") + error.what() + "\n");
    } catch (...) {
        emit(OutputChannel::Error, "Internal runtime error.\n");
    }
    gRunning.store(false);
    gStopRequested.store(false);
    return status;
}

void requestStop() {
    if (gRunning.load()) gStopRequested.store(true);
}

std::string luauVersionString() { return BYTECODE_LUAU_TAG; }

}  // namespace bytecode
