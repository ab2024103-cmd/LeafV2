package com.leaf.reader.engine

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * A small, dependency free PDF text extractor.
 *
 * PDFBox is the better tool, but on Android it cannot always read the fonts of a
 * document (the standard 14 fonts make PDFBox load a TrueType fallback, which overflows
 * the stack). When that happens Leaf falls back to this scanner: it walks the content
 * streams of the file, inflates them when needed and collects the text that is shown by
 * the `Tj` / `TJ` operators. The result is good enough to search inside a document and
 * to quote it, even when the exact glyph geometry is not available.
 */
object PdfTextFallback {

    /** Text of every page that carries text, in the order the streams appear. */
    fun pages(bytes: ByteArray): List<String> {
        val pages = mutableListOf<String>()
        var index = 0
        while (index < bytes.size) {
            val start = indexOf(bytes, STREAM, index) ?: break
            var contentStart = start + STREAM.size
            if (contentStart < bytes.size && bytes[contentStart] == CR) contentStart++
            if (contentStart < bytes.size && bytes[contentStart] == LF) contentStart++
            val end = indexOf(bytes, ENDSTREAM, contentStart) ?: break
            index = end + ENDSTREAM.size
            val raw = bytes.copyOfRange(contentStart, end)
            val dictionaryStart = (contentStart - 800).coerceAtLeast(0)
            val dictionary = String(bytes, dictionaryStart, contentStart - dictionaryStart, Charsets.ISO_8859_1)
            val content = if (dictionary.contains("/FlateDecode")) inflate(raw) ?: raw else raw
            val text = textOf(content)
            if (text.isNotBlank()) pages.add(text)
        }
        return pages
    }

    /** Collects the literal strings of a content stream. */
    private fun textOf(content: ByteArray): String {
        val builder = StringBuilder()
        var index = 0
        while (index < content.size) {
            if (content[index] == OPEN_PAREN) {
                val literal = readLiteral(content, index)
                if (literal != null) {
                    builder.append(literal.first)
                    builder.append(' ')
                    index = literal.second
                    continue
                }
            }
            index++
        }
        return builder.toString().trim()
    }

    /** Reads a `( ... )` string and returns it with the index behind the closing bracket. */
    private fun readLiteral(content: ByteArray, start: Int): Pair<String, Int>? {
        val builder = StringBuilder()
        var depth = 1
        var index = start + 1
        while (index < content.size) {
            val byte = content[index].toInt() and 0xFF
            if (byte == ESCAPE) {
                val next = content.getOrNull(index + 1)?.toInt()?.and(0xFF) ?: break
                when (next) {
                    'n'.code -> builder.append('\n')
                    'r'.code -> builder.append('\r')
                    't'.code -> builder.append('\t')
                    'b'.code -> builder.append('\b')
                    'f'.code -> builder.append('')
                    else -> builder.append(next.toChar())
                }
                index += 2
            } else if (byte == OPEN_PAREN) {
                depth++
                builder.append('(')
                index++
            } else if (byte == CLOSE_PAREN) {
                depth--
                if (depth == 0) return builder.toString() to (index + 1)
                builder.append(')')
                index++
            } else {
                builder.append(byte.toChar())
                index++
            }
        }
        return null
    }

    /** Inflates a zlib compressed stream, returning null when it is not readable. */
    private fun inflate(raw: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(raw)
        val output = ByteArrayOutputStream(raw.size * 3)
        val buffer = ByteArray(16 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        inflater.end()
        output.toByteArray()
    }.getOrNull()

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int? {
        if (from < 0 || needle.isEmpty() || haystack.size < needle.size) return null
        outer@ for (start in from..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return start
        }
        return null
    }

    private val STREAM = "stream".toByteArray(Charsets.ISO_8859_1)
    private val ENDSTREAM = "endstream".toByteArray(Charsets.ISO_8859_1)
    private const val CR = 13.toByte()
    private const val LF = 10.toByte()
    private const val OPEN_PAREN = 40.toByte()
    private const val CLOSE_PAREN = 41.toByte()
    private const val ESCAPE = 92.toByte()
}
