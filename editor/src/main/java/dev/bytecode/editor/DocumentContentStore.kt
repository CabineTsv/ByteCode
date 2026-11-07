package dev.bytecode.editor

import java.util.concurrent.ConcurrentHashMap

/**
 * Holds document text until the editor page fetches it from `https://appassets.androidplatform.net/docs/<token>`.
 * Passing text through a URL instead of a JavaScript string literal keeps large files fast and safe.
 * Every entry is served once and then removed.
 */
internal class DocumentContentStore {
    private val contents = ConcurrentHashMap<String, ByteArray>()

    fun put(token: String, text: String) {
        contents[token] = text.toByteArray(Charsets.UTF_8)
    }

    fun take(token: String): ByteArray? = contents.remove(token)

    fun clear() {
        contents.clear()
    }
}
