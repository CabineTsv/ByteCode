package dev.bytecode.app.workspace

import android.content.ContentResolver

/** A file found while indexing a workspace. [path] is relative to the workspace root. */
data class IndexedFile(val entry: FsEntry, val path: String)

data class SearchMatch(val file: IndexedFile, val line: Int, val column: Int, val lineText: String)

object WorkspaceIndexer {

    /** Lists up to [limit] files. Blocks; run on a background thread. */
    fun index(
        fs: WorkspaceFileSystem,
        root: FsEntry,
        limit: Int,
        shouldContinue: () -> Boolean
    ): List<IndexedFile> {
        val files = ArrayList<IndexedFile>()
        fs.walk(root, WorkspaceFileSystem.IGNORED_DIRECTORIES, { shouldContinue() && files.size < limit }) { entry, path ->
            files.add(IndexedFile(entry, path))
        }
        return files
    }
}

object TextSearch {

    private const val MAX_FILE_BYTES = 1L * 1024L * 1024L
    private const val MAX_LINE_PREVIEW = 200

    /**
     * Searches [files] and reports each match through [onMatch]. Binary and large files are skipped.
     * Throws [java.util.regex.PatternSyntaxException] for an invalid regular expression.
     * Blocks; run on a background thread.
     */
    fun search(
        resolver: ContentResolver,
        files: List<IndexedFile>,
        query: String,
        caseSensitive: Boolean,
        useRegex: Boolean,
        maxMatches: Int,
        shouldContinue: () -> Boolean,
        onMatch: (SearchMatch) -> Unit
    ): Int {
        if (query.isEmpty()) return 0
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val pattern = Regex(if (useRegex) query else Regex.escape(query), options)

        var found = 0
        for (file in files) {
            if (!shouldContinue() || found >= maxMatches) break
            if (file.entry.size > MAX_FILE_BYTES) continue
            val bytes = try {
                FileIo.readBytes(resolver, file.entry.uri, MAX_FILE_BYTES)
            } catch (error: Exception) {
                continue
            }
            if (TextCodec.looksBinary(bytes)) continue
            val text = TextCodec.decodeLossy(bytes)

            var lineNumber = 0
            for (line in text.lineSequence()) {
                lineNumber++
                if (found >= maxMatches) break
                for (match in pattern.findAll(line)) {
                    if (match.value.isEmpty()) continue
                    found++
                    onMatch(SearchMatch(file, lineNumber, match.range.first + 1, preview(line)))
                    if (found >= maxMatches) break
                }
            }
        }
        return found
    }

    private fun preview(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.length <= MAX_LINE_PREVIEW) trimmed else trimmed.substring(0, MAX_LINE_PREVIEW) + "…"
    }
}
