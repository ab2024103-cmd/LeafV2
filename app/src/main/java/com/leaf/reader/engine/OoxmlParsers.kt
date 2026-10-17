package com.leaf.reader.engine

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Shared helpers for reading OOXML packages straight from a zip stream. */
object Ooxml {

    fun forEachEntry(stream: InputStream, wanted: (String) -> Boolean, action: (String, InputStream) -> Unit) {
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && wanted(entry.name)) {
                    action(entry.name, zip)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun readEntry(stream: InputStream, wanted: (String) -> Boolean): Pair<String, ByteArray>? {
        var found: Pair<String, ByteArray>? = null
        forEachEntry(stream, wanted) { name, input ->
            if (found == null) found = name to input.readBytes()
        }
        return found
    }

    fun newParser(input: InputStream): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        return parser
    }

    fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()
}

/** Reads .docx paragraphs. */
object DocxParser {

    fun parse(stream: InputStream): ParsedDocument {
        val entry = Ooxml.readEntry(stream) { it == "word/document.xml" } ?: return ParsedDocument(
            com.leaf.reader.model.FileKind.DOCX, "Document", listOf(TextBlock("Empty document"))
        )
        val blocks = mutableListOf<TextBlock>()
        var title: String? = null
        val parser = Ooxml.newParser(entry.second.inputStream())
        var text = StringBuilder()
        var inParagraph = false
        var inText = false
        var pageBreak = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (Ooxml.localName(parser.name)) {
                        "p" -> {
                            inParagraph = true
                            text = StringBuilder()
                            pageBreak = false
                        }
                        "t" -> if (inParagraph) inText = true
                        "tab" -> if (inParagraph) text.append('\t')
                        "br" -> if (inParagraph && parser.getAttributeValue(null, "type") == "page") {
                            pageBreak = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph && inText) text.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (Ooxml.localName(parser.name)) {
                        "t" -> inText = false
                        "p" -> {
                            inParagraph = false
                            val content = text.toString().trim()
                            if (content.isNotEmpty()) {
                                if (title == null) title = content
                                blocks.add(TextBlock(content, BlockStyle.BODY, pageBreak))
                            } else if (pageBreak) {
                                blocks.add(TextBlock("", BlockStyle.BODY, true))
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        val finalBlocks = if (blocks.isEmpty()) listOf(TextBlock("This document has no readable text.")) else blocks
        return ParsedDocument(
            com.leaf.reader.model.FileKind.DOCX,
            title ?: "Word document",
            finalBlocks
        )
    }
}

/** Reads .xlsx sheets into readable rows. */
object XlsxParser {

    fun parse(stream: InputStream): ParsedDocument {
        val sharedStrings = mutableListOf<String>()
        val sheets = mutableListOf<Pair<String, String>>() // name -> xml

        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    when {
                        name == "xl/sharedStrings.xml" -> sharedStrings.addAll(parseSharedStrings(zip))
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            sheets.add(name to zip.readBytes().toString(Charsets.UTF_8))
                        }
                        name == "xl/workbook.xml" -> Unit
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val blocks = mutableListOf<TextBlock>()
        sheets.forEachIndexed { index, (_, xml) ->
            blocks.add(TextBlock("Sheet ${index + 1}", BlockStyle.HEADING))
            val rows = parseSheet(xml, sharedStrings)
            rows.forEach { row ->
                blocks.add(TextBlock(row, BlockStyle.TABLE_ROW))
            }
            blocks.add(TextBlock("", BlockStyle.BODY, true))
        }
        val finalBlocks = if (blocks.isEmpty()) listOf(TextBlock("This workbook has no readable cells.")) else blocks
        return ParsedDocument(
            com.leaf.reader.model.FileKind.XLSX,
            "Spreadsheet",
            finalBlocks
        )
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val out = mutableListOf<String>()
        val parser = Ooxml.newParser(stream)
        var inItem = false
        val buffer = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (Ooxml.localName(parser.name)) {
                    "si" -> {
                        inItem = true
                        buffer = StringBuilder()
                    }
                    "t" -> if (inItem) inText = true
                }
                XmlPullParser.TEXT -> if (inItem && inText) buffer.append(parser.text ?: "")
                XmlPullParser.END_TAG -> when (Ooxml.localName(parser.name)) {
                    "t" -> inText = false
                    "si" -> {
                        inItem = false
                        out.add(buffer.toString())
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheet(xml: String, shared: List<String>): List<String> {
        val rows = mutableListOf<String>()
        val parser = Ooxml.newParser(xml.byteInputStream())
        var inValue = false
        var inInline = false
        var value = StringBuilder()
        var cellRef = ""
        var cellType = ""
        var currentRow = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (Ooxml.localName(parser.name)) {
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: "n"
                        value = StringBuilder()
                    }
                    "v" -> inValue = true
                    "is" -> inInline = true
                    "t" -> if (inInline) inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text ?: "")
                XmlPullParser.END_TAG -> when (Ooxml.localName(parser.name)) {
                    "v" -> inValue = false
                    "t" -> if (inInline) inValue = false
                    "c" -> {
                        val raw = value.toString().trim()
                        val resolved = if (cellType == "s") {
                            raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        } else raw
                        if (resolved.isNotEmpty()) {
                            currentRow.add(if (cellRef.isNotEmpty()) "$cellRef: $resolved" else resolved)
                        }
                        inInline = false
                    }
                    "row" -> {
                        if (currentRow.isNotEmpty()) rows.add(currentRow.joinToString("   |   "))
                        currentRow = mutableListOf()
                    }
                }
            }
            event = parser.next()
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow.joinToString("   |   "))
        return rows
    }
}

/** Reads .pptx slides. */
object PptxParser {

    fun parse(stream: InputStream): ParsedDocument {
        val slides = mutableListOf<Pair<String, String>>()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    slides.add(name to zip.readBytes().toString(Charsets.UTF_8))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        slides.sortBy { it.first }
        val blocks = mutableListOf<TextBlock>()
        slides.forEachIndexed { index, (_, xml) ->
            blocks.add(TextBlock("Slide ${index + 1}", BlockStyle.HEADING))
            val shapes = parseSlide(xml)
            shapes.forEachIndexed { shapeIndex, text ->
                blocks.add(
                    TextBlock(
                        text,
                        if (shapeIndex == 0) BlockStyle.SLIDE else BlockStyle.BODY
                    )
                )
            }
            blocks.add(TextBlock("", BlockStyle.BODY, true))
        }
        val finalBlocks = if (blocks.isEmpty()) listOf(TextBlock("This deck has no readable text.")) else blocks
        return ParsedDocument(
            com.leaf.reader.model.FileKind.PPTX,
            "Presentation",
            finalBlocks
        )
    }

    private fun parseSlide(xml: String): List<String> {
        val shapes = mutableListOf<StringBuilder>()
        val parser = Ooxml.newParser(xml.byteInputStream())
        var current: StringBuilder? = null
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (Ooxml.localName(parser.name)) {
                    "p" -> current = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current?.append(parser.text ?: "")
                XmlPullParser.END_TAG -> when (Ooxml.localName(parser.name)) {
                    "t" -> inText = false
                    "p" -> {
                        val value = current?.toString()?.trim().orEmpty()
                        if (value.isNotEmpty()) shapes.add(StringBuilder(value))
                        current = null
                    }
                }
            }
            event = parser.next()
        }
        return shapes.map { it.toString() }
    }
}
