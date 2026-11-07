# Minification is off by default. If you enable it, these members are called by name from native
# code or JavaScript and must be kept.
-keep class dev.bytecode.luau.** { *; }
-keepclassmembers class dev.bytecode.editor.CodeEditorView$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class dev.bytecode.app.run.JavaScriptRunner$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
