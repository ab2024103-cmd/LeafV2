package com.leaf.reader.engine

import android.content.Context
import android.net.Uri
import com.leaf.reader.model.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset

/** Plain text extracted from a document, plus logical page offsets. */
data class ExtractedText(
    val text: String,
    val pageOffsets: List<Int>
)

/** Extracts plain text from every supported format (spec 10.8). */
object TextExtractor {

    suspend fun extract(context: Context, uri: Uri, kind: FileKind): ExtractedText =
        withContext(Dispatchers.IO) {
            when (kind) {
                FileKind.PDF -> {
                    val session = PdfSession(context, uri)
                    try {
                        val index = session.textIndex()
                        val text = index.pages.joinToString("\n\n") { it.text }
                        val offsets = index.pages.map { it.textStart }.toMutableList()
                        offsets += text.length
                        ExtractedText(text, offsets)
                    } finally {
                        session.close()
                    }
                }
                FileKind.TXT -> {
                    val text = readText(context, uri)
                    ExtractedText(text, estimateOffsets(text))
                }
                FileKind.DOCX -> {
                    val parsed = openStream(context, uri) { DocxParser.parse(it) }
                    val text = parsed.plainText
                    ExtractedText(text, estimateOffsets(text))
                }
                FileKind.EPUB -> {
                    val parsed = openStream(context, uri) { EpubParser.parse(it) }
                    val text = parsed.plainText
                    ExtractedText(text, estimateOffsets(text))
                }
                FileKind.XLSX -> {
                    val parsed = openStream(context, uri) { XlsxParser.parse(it) }
                    val text = parsed.plainText
                    ExtractedText(text, logicalPageOffsets(parsed))
                }
                FileKind.PPTX -> {
                    val parsed = openStream(context, uri) { PptxParser.parse(it) }
                    val text = parsed.plainText
                    ExtractedText(text, logicalPageOffsets(parsed))
                }
            }
        }

    /** Parses a text-like document into blocks (used by the reader). */
    suspend fun parseDocument(context: Context, uri: Uri, kind: FileKind): ParsedDocument =
        withContext(Dispatchers.IO) {
            runCatching { parseOrThrow(context, uri, kind) }.getOrElse { error ->
                ParsedDocument(
                    kind,
                    kind.label,
                    listOf(TextBlock("This document could not be opened: ${error.message ?: "unknown error"}"))
                )
            }
        }

    private suspend fun parseOrThrow(context: Context, uri: Uri, kind: FileKind): ParsedDocument =
        when (kind) {
            FileKind.TXT -> {
                val text = readText(context, uri)
                val blocks = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                    .map { TextBlock(it, BlockStyle.BODY) }
                ParsedDocument(
                    FileKind.TXT,
                    "Text document",
                    if (blocks.isEmpty()) listOf(TextBlock("Empty document")) else blocks
                )
            }
            FileKind.DOCX -> openStream(context, uri) { DocxParser.parse(it) }
            FileKind.EPUB -> openStream(context, uri) { EpubParser.parse(it) }
            FileKind.XLSX -> openStream(context, uri) { XlsxParser.parse(it) }
            FileKind.PPTX -> openStream(context, uri) { PptxParser.parse(it) }
            FileKind.PDF -> ParsedDocument(FileKind.PDF, "PDF", listOf(TextBlock("")))
        }

    private suspend fun <T> openStream(
        context: Context,
        uri: Uri,
        block: (InputStream) -> T
    ): T = withContext(Dispatchers.IO) {
        val stream = com.leaf.reader.core.FileUtils.openInputStream(context, uri)
            ?: throw IllegalStateException("Cannot open $uri")
        stream.use { block(it) }
    }

    fun readText(context: Context, uri: Uri): String {
        val stream = com.leaf.reader.core.FileUtils.openInputStream(context, uri) ?: return ""
        return stream.use { readAll(it) }
    }

    private fun readAll(stream: InputStream): String {
        val bytes = stream.readBytes()
        val utf8 = runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
        val replacement = utf8.count { it == '\uFFFD' }
        if (replacement == 0) return utf8
        return runCatching { String(bytes, Charset.forName("ISO-8859-1")) }.getOrDefault(utf8)
    }

    /** Rough page estimate used before a document has been opened (chars per page). */
    private fun estimateOffsets(text: String): List<Int> {
        val perPage = 1800
        val offsets = mutableListOf(0)
        var index = perPage
        while (index < text.length) {
            offsets.add(index)
            index += perPage
        }
        offsets.add(text.length)
        return offsets
    }

    /** Exact page offsets for paged Office formats (one page per slide / sheet). */
    private fun logicalPageOffsets(parsed: ParsedDocument): List<Int> {
        val offsets = mutableListOf(0)
        var offset = 0
        parsed.blocks.forEach { block ->
            offset += block.text.length
            if (block.pageBreakAfter) {
                offset += 2
                if (offset < parsed.plainText.length) offsets.add(offset)
            } else {
                offset += 2
            }
        }
        val textLength = parsed.plainText.length
        if (offsets.lastOrNull() != textLength) offsets.add(textLength)
        return offsets
    }
}
