package dev.bytecode.editor

/**
 * Describes a language the editor knows. [id] is the key understood by the JavaScript side
 * (editor/web/src/languages.js); [iconLabel] and [iconColor] draw the file icon.
 */
data class LanguageInfo(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val iconLabel: String,
    val iconColor: Int
)

object LanguageRegistry {

    val plainText = LanguageInfo("plaintext", "Plain Text", listOf("txt", "log", "text", "gitignore", "env"), "TX", 0xFF8A919C.toInt())

    val all: List<LanguageInfo> = listOf(
        LanguageInfo("html", "HTML", listOf("html", "htm"), "HT", 0xFFE44D26.toInt()),
        LanguageInfo("css", "CSS", listOf("css"), "CS", 0xFF4A90E2.toInt()),
        LanguageInfo("javascript", "JavaScript", listOf("js", "mjs", "cjs"), "JS", 0xFFE5C100.toInt()),
        LanguageInfo("jsx", "JavaScript (JSX)", listOf("jsx"), "JX", 0xFFE5C100.toInt()),
        LanguageInfo("typescript", "TypeScript", listOf("ts", "mts", "cts"), "TS", 0xFF3178C6.toInt()),
        LanguageInfo("tsx", "TypeScript (TSX)", listOf("tsx"), "TX", 0xFF3178C6.toInt()),
        LanguageInfo("json", "JSON", listOf("json", "jsonc"), "{}", 0xFFE8A33D.toInt()),
        LanguageInfo("python", "Python", listOf("py", "pyw"), "PY", 0xFF4B8BBE.toInt()),
        LanguageInfo("java", "Java", listOf("java"), "JV", 0xFFE76F00.toInt()),
        LanguageInfo("kotlin", "Kotlin", listOf("kt", "kts"), "KT", 0xFFA97BFF.toInt()),
        LanguageInfo("c", "C", listOf("c"), "C", 0xFF6D8BB5.toInt()),
        LanguageInfo("cpp", "C++", listOf("cpp", "cc", "cxx", "h", "hpp", "hh", "hxx"), "C+", 0xFFF34B7D.toInt()),
        LanguageInfo("csharp", "C#", listOf("cs"), "C#", 0xFF9B4F96.toInt()),
        LanguageInfo("php", "PHP", listOf("php"), "PH", 0xFF8892BF.toInt()),
        LanguageInfo("ruby", "Ruby", listOf("rb"), "RB", 0xFFCC342D.toInt()),
        LanguageInfo("go", "Go", listOf("go"), "GO", 0xFF00ADD8.toInt()),
        LanguageInfo("rust", "Rust", listOf("rs"), "RS", 0xFFDEA584.toInt()),
        LanguageInfo("sql", "SQL", listOf("sql"), "SQ", 0xFFE38C00.toInt()),
        LanguageInfo("shell", "Shell", listOf("sh", "bash", "zsh"), "SH", 0xFF89E051.toInt()),
        LanguageInfo("lua", "Lua", listOf("lua"), "LU", 0xFF5B6FD6.toInt()),
        LanguageInfo("luau", "Luau", listOf("luau"), "LU", 0xFF00A2FF.toInt()),
        LanguageInfo("markdown", "Markdown", listOf("md", "markdown"), "MD", 0xFF519ABA.toInt()),
        LanguageInfo("yaml", "YAML", listOf("yml", "yaml"), "YM", 0xFFCB171E.toInt()),
        LanguageInfo("xml", "XML", listOf("xml", "svg", "xsd", "xsl"), "XM", 0xFFE37933.toInt()),
        plainText
    )

    private val byExtension: Map<String, LanguageInfo> = buildMap {
        for (language in all) {
            for (extension in language.extensions) {
                put(extension, language)
            }
        }
    }

    private val byId: Map<String, LanguageInfo> = all.associateBy { it.id }

    /** Chooses the language from the file name's extension (case-insensitive). */
    fun forFileName(fileName: String): LanguageInfo {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return plainText
        return byExtension[extension] ?: plainText
    }

    fun byId(id: String): LanguageInfo = byId[id] ?: plainText
}
