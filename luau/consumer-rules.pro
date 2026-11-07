# The native library looks these up by name through JNI.
-keep class dev.bytecode.luau.LuauNative { *; }
-keep class dev.bytecode.luau.LuauOutputSink { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
