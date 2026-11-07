package dev.bytecode.app.documents

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Keeps a copy of unsaved text in the app's private storage so a crash, a killed process or a
 * recreated activity does not lose work. One file per document, replaced atomically.
 */
class RecoveryStore(context: Context) {

    class Snapshot(val uri: String, val name: String, val text: String, val savedAt: Long)

    private val directory = File(context.filesDir, "recovery").also { it.mkdirs() }

    fun write(uri: String, name: String, text: String) {
        if (text.length > MAX_CHARS) return
        try {
            val json = JSONObject()
                .put("uri", uri)
                .put("name", name)
                .put("text", text)
                .put("savedAt", System.currentTimeMillis())
            val target = fileFor(uri)
            val temporary = File(directory, target.name + ".tmp")
            temporary.writeText(json.toString(), Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        } catch (error: IOException) {
            // Recovery is best effort; failing to write a snapshot must never disturb editing.
        }
    }

    fun read(uri: String): Snapshot? {
        val file = fileFor(uri)
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            Snapshot(json.getString("uri"), json.getString("name"), json.getString("text"), json.getLong("savedAt"))
        } catch (error: JSONException) {
            file.delete()
            null
        } catch (error: IOException) {
            null
        }
    }

    fun delete(uri: String) {
        fileFor(uri).delete()
    }

    private fun fileFor(uri: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { String.format("%02x", it) }
        return File(directory, "$name.json")
    }

    private companion object {
        const val MAX_CHARS = 8_000_000
    }
}
