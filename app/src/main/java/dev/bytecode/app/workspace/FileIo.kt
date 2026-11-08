package dev.bytecode.app.workspace

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class FileTooLargeException(val limitBytes: Long) : IOException("File is larger than $limitBytes bytes")

/** Reading and writing through content URIs (works for tree documents and single files alike). */
object FileIo {

    fun readBytes(resolver: ContentResolver, uri: Uri, maxBytes: Long): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw FileTooLargeException(maxBytes)
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    /** Overwrites the file ("wt" truncates, so a shorter text never leaves old bytes behind). */
    fun writeBytes(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri for writing")
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }

    fun copy(resolver: ContentResolver, from: Uri, to: Uri) {
        val input = resolver.openInputStream(from) ?: throw FileNotFoundException(from.toString())
        input.use { source ->
            val output = resolver.openOutputStream(to, "wt") ?: throw IOException("Cannot open $to for writing")
            output.use { target ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    target.write(buffer, 0, read)
                }
                target.flush()
            }
        }
    }
}

object TextCodec {

    /** Strict UTF-8 decoding; null when the bytes are not valid UTF-8 (so the file is not silently corrupted on save). */
    fun decodeStrict(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        null
    }

    fun decodeLossy(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    /** A NUL byte near the start is a reliable sign of a binary file. */
    fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8000)
        for (index in 0 until limit) {
            if (bytes[index].toInt() == 0) return true
        }
        return false
    }

    const val BOM = '\uFEFF'
}
