package com.leaf.reader.data

import android.content.Context
import android.net.Uri
import com.leaf.reader.model.FileKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a small offline sample library so the app is usable on first launch.
 * Every file is generated locally — nothing is downloaded (spec 10.10).
 */
object DemoLibrary {

    const val FOLDER_WORK = "work"
    const val FOLDER_STUDY = "study"
    const val FOLDER_PERSONAL = "personal"

    fun ensureSeeded(context: Context, repository: DocumentRepository) {
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val samples = listOf(
            Sample(
                "leaf-notes.txt",
                FileKind.TXT,
                """# Leaf reading notes

Leaf keeps every document on your own device. Nothing is uploaded, and no account is required.

## Highlights
Tap and drag across a sentence, then pick a colour to highlight it. Highlights survive theme changes, zoom changes and restarts.

## Organisation
- Folders can be nested, for example Work then Reports.
- Tags are colour coded and can be combined with AND semantics.
- Smart collections update themselves: Added this week, Still reading, Has highlights.

## Search
Search reaches inside files as well as file names. Every match is listed with the page it appears on, and tapping a match opens the document right there.
"""
            ),
            Sample(
                "field-guide.pdf",
                FileKind.PDF,
                null // generated as a real PDF below
            ),
            Sample(
                "quarterly-report.docx",
                FileKind.DOCX,
                null
            ),
            Sample(
                "budget.xlsx",
                FileKind.XLSX,
                null
            ),
            Sample(
                "product-deck.pptx",
                FileKind.PPTX,
                null
            ),
            Sample(
                "little-book.epub",
                FileKind.EPUB,
                null
            )
        )

        samples.forEach { sample ->
            val target = File(dir, sample.name)
            if (!target.exists() || target.length() == 0L) {
                val bytes = when (sample.kind) {
                    FileKind.PDF -> buildPdf(pdfPages())
                    FileKind.DOCX -> buildDocx(docxParagraphs())
                    FileKind.XLSX -> buildXlsx(xlsxSheets())
                    FileKind.PPTX -> buildPptx(pptxSlides())
                    FileKind.EPUB -> buildEpub(epubChapters())
                    FileKind.TXT -> sample.text!!.toByteArray(Charsets.UTF_8)
                }
                FileOutputStream(target).use { it.write(bytes) }
            }
            val uri = Uri.fromFile(target)
            val existing = repository.documentByUri(uri.toString())
            if (existing == null) {
                repository.addLocalDocument(
                    uri = uri,
                    name = sample.name,
                    kind = sample.kind,
                    sizeBytes = target.length(),
                    dateAdded = System.currentTimeMillis() - (sample.ageHours * 3600_000L)
                )
            }
        }
    }

    private data class Sample(
        val name: String,
        val kind: FileKind,
        val text: String?,
        val ageHours: Long = 0
    )

    // ------------------------------------------------------------------ PDF

    private fun pdfPages(): List<List<String>> = listOf(
        listOf(
            "Leaf Field Guide",
            "",
            "A tiny sample document shipped with the app.",
            "",
            "Leaf reads PDF, DOCX, XLSX, PPTX, TXT and EPUB",
            "entirely offline."
        ),
        listOf(
            "Reading a PDF",
            "",
            "Pages render progressively: a thumbnail first, then",
            "the full page, then the text layer.",
            "",
            "Drag across the page to highlight a region.",
            "The selection is stored as normalised geometry."
        ),
        listOf(
            "Searching inside files",
            "",
            "Full text search finds every occurrence and shows",
            "the page it appears on.",
            "",
            "Tap a result to open the document at that page",
            "with the find bar already filled in."
        )
    )

    /** Writes a small but valid PDF file. */
    fun buildPdf(pages: List<List<String>>): ByteArray {
        val objects = mutableListOf<String>()
        val pageCount = pages.size
        val fontObj = 3
        val firstPageObj = 4
        val firstContentObj = firstPageObj + pageCount

        val kids = (0 until pageCount).joinToString(" ") { "${firstPageObj + it} 0 R" }
        objects.add("<< /Type /Catalog /Pages 2 0 R >>")
        objects.add("<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

        pages.forEachIndexed { index, lines ->
            val contentObj = firstContentObj + index
            objects.add(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 $fontObj 0 R >> >> /Contents $contentObj 0 R >>"
            )
        }

        pages.forEach { lines ->
            val stream = buildString {
                append("BT\n/F1 18 Tf\n72 720 Td\n26 TL\n")
                lines.forEach { line ->
                    append("(").append(escapePdf(line)).append(") Tj\nT*\n")
                }
                append("ET")
            }
            objects.add("<< /Length ${stream.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n$stream\nendstream")
        }

        val out = ByteArrayOutputStream()
        fun writeText(value: String) = out.write(value.toByteArray(Charsets.ISO_8859_1))
        writeText("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets.add(out.size())
            writeText("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xrefOffset = out.size()
        writeText("xref\n0 ${objects.size + 1}\n")
        writeText("0000000000 65535 f \n")
        offsets.forEach { writeText(String.format("%010d 00000 n \n", it)) }
        writeText("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun escapePdf(value: String): String = value
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")

    // ----------------------------------------------------------------- DOCX

    private fun docxParagraphs(): List<String> = listOf(
        "Quarterly Report",
        "Leaf keeps this Word document readable without any cloud service.",
        "Paragraphs are extracted from word/document.xml, so search and highlights work on real content.",
        "Highlights inside Word documents are stored as text offsets, which keeps them accurate after a theme change.",
        "The document list shows the folder, the tags and how far you have read.",
        "Long-press a row for the full action sheet: rename, move, tag, favourite, share or delete."
    )

    fun buildDocx(paragraphs: List<String>): ByteArray {
        val body = paragraphs.joinToString("") { paragraph ->
            "<w:p><w:r><w:t>${escapeXml(paragraph)}</w:t></w:r></w:p>"
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$body</w:body></w:document>"
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        return zipOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rels,
            "word/document.xml" to document
        )
    }

    // ----------------------------------------------------------------- XLSX

    private fun xlsxSheets(): List<Pair<String, List<List<String>>>> = listOf(
        "Budget" to listOf(
            listOf("Item", "Q1", "Q2"),
            listOf("Reading", "120", "140"),
            listOf("Covers", "35", "40"),
            listOf("Storage", "0", "0")
        ),
        "Notes" to listOf(
            listOf("Note", "Value"),
            listOf("Documents", "6"),
            listOf("Offline", "Yes"),
            listOf("Account", "None")
        )
    )

    fun buildXlsx(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        val entries = mutableListOf<Pair<String, String>>()
        entries.add(
            "[Content_Types].xml" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    sheets.indices.joinToString("") { i ->
                        "<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    } +
                    "</Types>")
        )
        entries.add(
            "_rels/.rels" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>")
        )
        val sheetTags = sheets.indices.joinToString("") { i ->
            "<sheet name=\"${escapeXml(sheets[i].first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }
        entries.add(
            "xl/workbook.xml" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets>$sheetTags</sheets></workbook>")
        )
        val relTags = sheets.indices.joinToString("") { i ->
            "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>"
        }
        entries.add(
            "xl/_rels/workbook.xml.rels" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$relTags</Relationships>")
        )
        sheets.forEachIndexed { index, (_, rows) ->
            val rowTags = rows.mapIndexed { rowIndex, row ->
                val cells = row.mapIndexed { cellIndex, value ->
                    val ref = "${columnName(cellIndex)}${rowIndex + 1}"
                    "<c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>"
                }.joinToString("")
                "<row r=\"${rowIndex + 1}\">$cells</row>"
            }.joinToString("")
            entries.add(
                "xl/worksheets/sheet${index + 1}.xml" to
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                        "<sheetData>$rowTags</sheetData></worksheet>")
            )
        }
        return zipOf(*entries.toTypedArray())
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    // ----------------------------------------------------------------- PPTX

    private fun pptxSlides(): List<Pair<String, List<String>>> = listOf(
        "Leaf in one slide" to listOf(
            "Leaf is an offline reader",
            "No account, no cloud, no tracking"
        ),
        "Six formats" to listOf(
            "PDF, DOCX, XLSX, PPTX, TXT and EPUB",
            "Every one of them is read on the device"
        ),
        "Annotations" to listOf(
            "Five highlight colours with undo and redo",
            "Bookmarks, tags, folders and smart collections"
        )
    )

    fun buildPptx(slides: List<Pair<String, List<String>>>): ByteArray {
        val entries = mutableListOf<Pair<String, String>>()
        entries.add(
            "[Content_Types].xml" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" +
                    slides.indices.joinToString("") { i ->
                        "<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
                    } +
                    "</Types>")
        )
        entries.add(
            "_rels/.rels" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
                    "</Relationships>")
        )
        val slideIds = slides.indices.joinToString("") { i ->
            "<p:sldId id=\"${256 + i}\" r:id=\"rId${i + 1}\"/>"
        }
        entries.add(
            "ppt/presentation.xml" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<p:sldIdLst>$slideIds</p:sldIdLst></p:presentation>")
        )
        val relTags = slides.indices.joinToString("") { i ->
            "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${i + 1}.xml\"/>"
        }
        entries.add(
            "ppt/_rels/presentation.xml.rels" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$relTags</Relationships>")
        )
        slides.forEachIndexed { index, (title, bullets) ->
            val titleShape = shapeXml(title, bold = true)
            val bulletShapes = bullets.joinToString("") { bullet -> shapeXml(bullet, bold = false) }
            entries.add(
                "ppt/slides/slide${index + 1}.xml" to
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                        "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
                        "<p:cSld><p:spTree><p:sp>$titleShape</p:sp>$bulletShapes</p:spTree></p:cSld></p:sld>")
            )
        }
        return zipOf(*entries.toTypedArray())
    }

    private fun shapeXml(text: String, bold: Boolean): String {
        val runProps = if (bold) " b=\"1\"" else ""
        return "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\"$runProps/>" +
            "<a:t>${escapeXml(text)}</a:t></a:r></a:p></p:txBody>"
    }

    // ----------------------------------------------------------------- EPUB

    private fun epubChapters(): List<Pair<String, List<String>>> = listOf(
        "The first leaf" to listOf(
            "Every reader starts somewhere.",
            "Leaf keeps your library on the device you already own."
        ),
        "Reading slowly" to listOf(
            "Open a document and Leaf remembers the page, the zoom and the theme.",
            "Come back later and continue exactly where you stopped."
        ),
        "Keeping order" to listOf(
            "Folders can nest, tags can combine, and smart collections keep themselves current.",
            "The library updates itself as files change."
        )
    )

    fun buildEpub(chapters: List<Pair<String, List<String>>>): ByteArray {
        val entries = mutableListOf<Pair<String, String>>()
        entries.add("mimetype" to "application/epub+zip")
        entries.add(
            "META-INF/container.xml" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                    "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                    "media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
        )
        val manifest = chapters.indices.joinToString("") { i ->
            "<item id=\"chapter${i + 1}\" href=\"chapter${i + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }
        val spine = chapters.indices.joinToString("") { i -> "<itemref idref=\"chapter${i + 1}\"/>" }
        entries.add(
            "OEBPS/content.opf" to
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\">" +
                    "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                    "<dc:title>Little Book</dc:title><dc:identifier id=\"bookid\">leaf-demo</dc:identifier>" +
                    "<dc:language>en</dc:language></metadata>" +
                    "<manifest>$manifest</manifest><spine>$spine</spine></package>")
        )
        chapters.forEachIndexed { index, (title, paragraphs) ->
            val body = paragraphs.joinToString("") { "<p>${escapeXml(it)}</p>" }
            entries.add(
                "OEBPS/chapter${index + 1}.xhtml" to
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>${escapeXml(title)}</title></head>" +
                        "<body><h1>${escapeXml(title)}</h1>$body</body></html>")
            )
        }
        return zipOf(*entries.toTypedArray())
    }

    // ----------------------------------------------------------------- utils

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
