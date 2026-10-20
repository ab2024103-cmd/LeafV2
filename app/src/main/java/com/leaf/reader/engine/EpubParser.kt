package com.leaf.reader.engine

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads .epub books: container -> opf -> spine -> xhtml. */
object EpubParser {

    fun parse(stream: InputStream): ParsedDocument {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val opfPath = findOpfPath(entries) ?: return ParsedDocument(
            com.leaf.reader.model.FileKind.EPUB, "EPUB book", listOf(TextBlock("This book could not be opened."))
        )
        val opf = entries[opfPath] ?: return ParsedDocument(
            com.leaf.reader.model.FileKind.EPUB, "EPUB book", listOf(TextBlock("This book could not be opened."))
        )
        val baseDir = opfPath.substringBeforeLast('/', "")
        val manifest = mutableMapOf<String, String>()   // id -> href
        val spine = mutableListOf<String>()              // idrefs
        var title = "EPUB book"

        val parser = Ooxml.newParser(opf.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (Ooxml.localName(parser.name)) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) spine.add(idref)
                    }
                    "title" -> {
                        val text = parser.nextText()
                        if (text.isNotBlank() && title == "EPUB book") title = text.trim()
                    }
                }
            }
            event = parser.next()
        }

        val blocks = mutableListOf<TextBlock>()
        spine.forEach { idref ->
            val href = manifest[idref] ?: return@forEach
            val path = if (baseDir.isEmpty()) href else "$baseDir/$href"
            val bytes = entries[path] ?: return@forEach
            val text = htmlToText(bytes.toString(Charsets.UTF_8))
            if (text.isNotBlank()) {
                blocks.add(TextBlock(text, BlockStyle.BODY, true))
            }
        }
        val finalBlocks = if (blocks.isEmpty()) listOf(TextBlock("This book has no readable chapters.")) else blocks
        return ParsedDocument(com.leaf.reader.model.FileKind.EPUB, title, finalBlocks)
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = entries["META-INF/container.xml"] ?: return null
        val parser = Ooxml.newParser(container.inputStream())
        var event = parser.eventType
        var path: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && Ooxml.localName(parser.name) == "rootfile") {
                path = parser.getAttributeValue(null, "full-path")
                if (path != null) break
            }
            event = parser.next()
        }
        return path
    }

    /** Minimal but robust HTML -> plain text conversion. */
    fun htmlToText(html: String): String {
        val out = StringBuilder()
        var i = 0
        var inScript = false
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val end = html.indexOf('>', i)
                if (end < 0) break
                val tag = html.substring(i + 1, end).trim()
                val name = Ooxml.localName(tag.substringBefore(' ')).lowercase()
                if (name == "script" || name == "style") inScript = true
                if (name == "br" || name == "p" || name == "div" || name == "li" ||
                    name == "h1" || name == "h2" || name == "h3" || name == "h4" ||
                    name == "h5" || name == "h6" || name == "blockquote" || name == "tr"
                ) {
                    out.append('\n')
                }
                if (name == "script" && tag.endsWith("/")) inScript = false
                i = end + 1
            } else {
                if (!inScript) out.append(c)
                i++
            }
        }
        val text = decodeEntities(out.toString())
        return text.lines().joinToString("\n") { it.trim() }.trim()
    }

    fun decodeEntities(raw: String): String = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
}
