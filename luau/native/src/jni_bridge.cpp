// JNI glue for dev.bytecode.luau.LuauNative. Contains no Luau logic of its own.
#include <jni.h>

#include <exception>
#include <string>
#include <vector>

#include "luau_runtime.hpp"
#include "text_convert.hpp"

namespace {

std::string toUtf8(JNIEnv* env, jstring text) {
    if (text == nullptr) return std::string();
    const jsize length = env->GetStringLength(text);
    std::vector<jchar> buffer(static_cast<size_t>(length));
    if (length > 0) env->GetStringRegion(text, 0, length, buffer.data());
    return bytecode::utf16ToUtf8(reinterpret_cast<const uint16_t*>(buffer.data()), buffer.size());
}

jstring toJavaString(JNIEnv* env, const std::string& utf8) {
    const std::u16string wide = bytecode::utf8ToUtf16Lossy(utf8.data(), utf8.size());
    return env->NewString(reinterpret_cast<const jchar*>(wide.data()), static_cast<jsize>(wide.size()));
}

std::string sanitizeForLine(const std::string& text) {
    std::string cleaned = text;
    for (char& c : cleaned) {
        if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    }
    return cleaned;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_dev_bytecode_luau_LuauNative_nativeVersion(JNIEnv* env, jclass /*clazz*/) {
    return toJavaString(env, bytecode::luauVersionString());
}

// Returns the ordinal of dev.bytecode.luau.LuauRunStatus.
JNIEXPORT jint JNICALL
Java_dev_bytecode_luau_LuauNative_nativeRun(JNIEnv* env, jclass /*clazz*/, jstring source,
                                            jstring chunkName, jlong timeoutMillis,
                                            jlong memoryLimitBytes, jobject sink) {
    if (sink == nullptr) return static_cast<jint>(bytecode::RunStatus::InternalError);

    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID onOutput = env->GetMethodID(sinkClass, "onOutput", "(ILjava/lang/String;)V");
    if (onOutput == nullptr) {
        env->ExceptionClear();
        return static_cast<jint>(bytecode::RunStatus::InternalError);
    }

    const bytecode::OutputCallback emit = [&](bytecode::OutputChannel channel, const std::string& text) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        jstring javaText = toJavaString(env, text);
        if (javaText == nullptr) {
            env->ExceptionClear();
            return;
        }
        env->CallVoidMethod(sink, onOutput, static_cast<jint>(channel), javaText);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(javaText);
    };

    bytecode::RunOptions options;
    options.timeoutMillis = timeoutMillis;
    options.memoryLimitBytes = memoryLimitBytes;

    const bytecode::RunStatus status =
        bytecode::runScript(toUtf8(env, source), toUtf8(env, chunkName), options, emit);
    return static_cast<jint>(status);
}

JNIEXPORT void JNICALL
Java_dev_bytecode_luau_LuauNative_nativeRequestStop(JNIEnv* /*env*/, jclass /*clazz*/) {
    bytecode::requestStop();
}

// One diagnostic per line: line<TAB>column<TAB>endLine<TAB>endColumn<TAB>message (all zero-based).
JNIEXPORT jstring JNICALL
Java_dev_bytecode_luau_LuauNative_nativeCheckSyntax(JNIEnv* env, jclass /*clazz*/, jstring source,
                                                    jint maxDiagnostics) {
    std::string encoded;
    try {
        const size_t limit = maxDiagnostics > 0 ? static_cast<size_t>(maxDiagnostics) : 1;
        const std::vector<bytecode::Diagnostic> diagnostics =
            bytecode::checkSyntax(toUtf8(env, source), limit);
        for (const bytecode::Diagnostic& d : diagnostics) {
            encoded += std::to_string(d.line) + '\t' + std::to_string(d.column) + '\t' +
                       std::to_string(d.endLine) + '\t' + std::to_string(d.endColumn) + '\t' +
                       sanitizeForLine(d.message) + '\n';
        }
    } catch (const std::exception&) {
        encoded.clear();
    } catch (...) {
        encoded.clear();
    }
    return toJavaString(env, encoded);
}

}  // extern "C"
