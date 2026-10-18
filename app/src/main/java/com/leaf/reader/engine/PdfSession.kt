package com.leaf.reader.engine

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.leaf.reader.model.NormRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PdfGlyph(
    val ch: Char,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val offset: Int
)

data class PdfPageText(
    val index: Int,
    val text: String,
    val textStart: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    val glyphs: List<PdfGlyph>,
    val lineHeight: Float = 12f
)

data class PdfMatch(
    val page: Int,
    val start: Int,
    val end: Int,
    val rects: List<NormRect>
)

/** Text index of a PDF: per page text plus glyph geometry (spec 5.9). */
class PdfTextIndex(val pages: List<PdfPageText>) {

    fun pageOf(index: Int): PdfPageText? = pages.getOrNull(index)

    fun findMatches(query: String): List<PdfMatch> {
        if (query.isBlank()) return emptyList()
        val needle = query.lowercase()
        val out = mutableListOf<PdfMatch>()
        pages.forEach { page ->
            val haystack = page.text.lowercase()
            var index = haystack.indexOf(needle)
            while (index >= 0) {
                out.add(
                    PdfMatch(
                        page = page.index,
                        start = page.textStart + index,
                        end = page.textStart + index + query.length,
                        rects = rectsForRange(page, index, index + query.length)
                    )
                )
                index = haystack.indexOf(needle, index + needle.length)
            }
        }
        return out
    }

    fun rectsForRange(page: PdfPageText, start: Int, end: Int): List<NormRect> {
        if (page.pageWidth <= 0f || page.pageHeight <= 0f) return emptyList()
        val rects = mutableListOf<NormRect>()
        page.glyphs.forEach { glyph ->
            val glyphStart = glyph.offset
            val glyphEnd = glyph.offset + 1
            if (glyphEnd <= start || glyphStart >= end) return@forEach
            val top = glyph.y - page.lineHeight * 0.80f
            val bottom = glyph.y + page.lineHeight * 0.20f
            rects.add(
                NormRect(
                    left = (glyph.x / page.pageWidth).coerceIn(0f, 1f),
                    top = (top / page.pageHeight).coerceIn(0f, 1f),
                    right = ((glyph.x + glyph.w) / page.pageWidth).coerceIn(0f, 1f),
                    bottom = (bottom / page.pageHeight).coerceIn(0f, 1f)
                )
            )
        }
        return mergeRects(rects)
    }

    /** Text of every glyph that intersects the given normalised rectangle. */
    fun textInRect(page: PdfPageText, rect: NormRect): String {
        val builder = StringBuilder()
        var lastY = Float.NaN
        page.glyphs.forEach { glyph ->
            val left = glyph.x / page.pageWidth
            val right = (glyph.x + glyph.w) / page.pageWidth
            val top = (glyph.y - page.lineHeight * 0.80f) / page.pageHeight
            val bottom = (glyph.y + page.lineHeight * 0.20f) / page.pageHeight
            if (left < rect.right && right > rect.left && top < rect.bottom && bottom > rect.top) {
                if (!lastY.isNaN() && abs(glyph.y - lastY) > 1.5f) builder.append(' ')
                builder.append(glyph.ch)
                lastY = glyph.y
            }
        }
        return builder.toString().trim()
    }

    fun offsetOfRect(page: PdfPageText, rect: NormRect): IntRange? {
        val chars = page.glyphs.filter { glyph ->
            val left = glyph.x / page.pageWidth
            val right = (glyph.x + glyph.w) / page.pageWidth
            val top = (glyph.y - page.lineHeight * 0.80f) / page.pageHeight
            val bottom = (glyph.y + page.lineHeight * 0.20f) / page.pageHeight
            left < rect.right && right > rect.left && top < rect.bottom && bottom > rect.top
        }
        if (chars.isEmpty()) return null
        val start = chars.minOf { it.offset }
        val end = chars.maxOf { it.offset } + 1
        return start until end
    }

    private fun mergeRects(rects: List<NormRect>): List<NormRect> {
        if (rects.isEmpty()) return emptyList()
        val sorted = rects.sortedWith(compareBy({ it.top }, { it.left }))
        val merged = mutableListOf<NormRect>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val sameLine = abs(next.top - current.top) < 0.006f
            if (sameLine && next.left <= current.right + 0.012f) {
                current = NormRect(
                    min(current.left, next.left),
                    min(current.top, next.top),
                    max(current.right, next.right),
                    max(current.bottom, next.bottom)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}

/** Reads and renders a PDF document. */
class PdfSession(private val context: Context, private val uri: Uri) {

    companion object {
        /** Upper bound for the rendered page width — keeps the memory footprint small. */
        const val MAX_RENDER_WIDTH = 1600
        private const val TAG = "LeafPdf"

        /**
         * PDFBox loads the metrics of the standard PDF fonts, which needs far more stack
         * than Android gives a thread by default (a StackOverflowError otherwise).
         */
        private val pdfBoxDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(null, runnable, "leaf-pdfbox", 8L * 1024 * 1024)
            }.asCoroutineDispatcher()
    }

    private val resolver: ContentResolver = context.contentResolver

    private val descriptor: ParcelFileDescriptor? =
        com.leaf.reader.core.FileUtils.openFileDescriptor(context, uri, "r")

    private val renderer: PdfRenderer? = descriptor?.let {
        runCatching { PdfRenderer(it) }.getOrNull()
    }

    private var index: PdfTextIndex? = null

    val pageCount: Int get() = renderer?.pageCount ?: 0

    fun isValid(): Boolean = renderer != null

    /**
     * Renders a page to a bitmap of the requested width (aspect ratio preserved).
     *
     * The platform renderer insists on ARGB_8888, so the page size is capped instead: the
     * width is limited to [MAX_RENDER_WIDTH] and further reduced when the device has little
     * memory left. A full page in ARGB_8888 is around ten megabytes, which is more than a
     * low memory device (or an emulator) can spare while the reader is drawing.
     */
    fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? {
        val pdf = renderer ?: return null
        if (pageIndex !in 0 until pdf.pageCount) return null
        return runCatching {
            pdf.openPage(pageIndex).use { page ->
                val aspect = if (page.width > 0) page.height.toFloat() / page.width else 1.414f
                val width = renderWidth(targetWidth, aspect)
                drawPage(page, width, aspect)
            }
        }.onFailure { error ->
            android.util.Log.e(TAG, "pdf render failed for page $pageIndex", error)
        }.getOrNull()
    }

    /** Draws one page into a bitmap; halves the size when the device runs out of memory. */
    private fun drawPage(page: PdfRenderer.Page, width: Int, aspect: Float): Bitmap {
        val height = max(1, (width * aspect).toInt())
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            // Half the size still shows the page and keeps the device alive.
            Bitmap.createBitmap(
                max(1, width / 2),
                max(1, height / 2),
                Bitmap.Config.ARGB_8888
            )
        }
        bitmap.eraseColor(Color.WHITE)
        val scale = if (page.width > 0) width.toFloat() / page.width else 1f
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /** Width that still fits into the memory the device can spare for one page. */
    private fun renderWidth(targetWidth: Int, aspect: Float): Int {
        val wanted = targetWidth.coerceIn(64, MAX_RENDER_WIDTH)
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        // One page may use a third of the free memory, four bytes per pixel.
        val budget = (available / 3).coerceAtLeast(1_500_000L)
        val maxPixels = (budget / 4).coerceAtLeast(120_000L)
        val maxWidth = sqrt(maxPixels / aspect.coerceAtLeast(0.1f)).toInt()
        return wanted.coerceAtMost(maxWidth.coerceAtLeast(64))
    }

    /** Builds (once) the text + geometry index used for search and highlighting. */
    suspend fun textIndex(): PdfTextIndex {
        index?.let { return it }
        return withContext(pdfBoxDispatcher) {
            val built = buildIndex()
            index = built
            built
        }
    }

    /** Synchronous access to the index (used on the UI thread for rect selection). */
    fun textIndexBlocking(): PdfTextIndex {
        index?.let { return it }
        val built = buildIndex()
        index = built
        return built
    }

    /** Already built index, if the background extraction finished. */
    fun cachedTextIndex(): PdfTextIndex? = index

    private fun buildIndex(): PdfTextIndex {
        val stream: InputStream = com.leaf.reader.core.FileUtils.openInputStream(context, uri)
            ?: return PdfTextIndex(emptyList())
        return runCatching {
            val document = PDDocument.load(stream)
            try {
                val pages = mutableListOf<PdfPageText>()
                var globalOffset = 0
                for (i in 0 until document.numberOfPages) {
                    val stripper = LeafStripper()
                    stripper.setSortByPosition(true)
                    stripper.setStartPage(i + 1)
                    stripper.setEndPage(i + 1)
                    runCatching { stripper.getText(document) }.onFailure { error ->
                        android.util.Log.e(TAG, "pdf text extraction failed on page $i", error)
                    }
                    val builder = stripper.builder
                    val pdPage = document.getPage(i)
                    val crop = runCatching { pdPage.cropBox }.getOrNull()
                    val rotation = runCatching { pdPage.rotation }.getOrDefault(0)
                    val baseWidth = crop?.width ?: 612f
                    val baseHeight = crop?.height ?: 792f
                    val rotated = rotation == 90 || rotation == 270
                    val pageWidth = if (rotated) baseHeight else baseWidth
                    val pageHeight = if (rotated) baseWidth else baseHeight
                    val text = builder?.text ?: ""
                    pages.add(
                        PdfPageText(
                            index = i,
                            text = text,
                            textStart = globalOffset,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            glyphs = builder?.glyphs ?: emptyList(),
                            lineHeight = builder?.lineHeight() ?: 12f
                        )
                    )
                    globalOffset += text.length + 2
                }
                PdfTextIndex(pages)
            } finally {
                runCatching { document.close() }
                runCatching { stream.close() }
            }
        }.getOrElse { error ->
            android.util.Log.e(TAG, "pdf text index failed", error)
            PdfTextIndex(emptyList())
        }
    }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
    }

    private class PageBuilder {
        val sb = StringBuilder()
        val glyphs = mutableListOf<PdfGlyph>()
        val baselines = mutableListOf<Float>()
        var lastY: Float? = null

        val text: String get() = sb.toString()

        fun add(ch: Char, x: Float, y: Float, w: Float, h: Float) {
            if (ch == '\n' || ch == '\r') return
            val offset = sb.length
            sb.append(ch)
            glyphs.add(PdfGlyph(ch, x, y, w, h, offset))
            baselines.add(y)
            lastY = y
        }

        /** Median distance between baselines, used to size highlight rectangles. */
        fun lineHeight(): Float {
            val distinct = baselines.distinct().sorted()
            if (distinct.size < 2) return 12f
            val deltas = distinct.zipWithNext { a, b -> abs(b - a) }.filter { it > 0.5f }.sorted()
            if (deltas.isEmpty()) return 12f
            return deltas[deltas.size / 2]
        }

        fun newLine() {
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
        }
    }

    private class LeafStripper : PDFTextStripper() {
        var builder: PageBuilder? = null
            private set

        override fun processTextPosition(text: TextPosition) {
            val target = builder ?: PageBuilder().also { builder = it }
            val unicode = runCatching { text.getUnicode() }.getOrNull() ?: return
            if (unicode.isEmpty()) return
            val y = text.getYDirAdj()
            val previous = target.lastY
            if (previous != null && abs(y - previous) > 1.5f) target.newLine()
            unicode.forEach { ch ->
                target.add(
                    ch,
                    text.getXDirAdj(),
                    y,
                    text.getWidthDirAdj(),
                    0f
                )
            }
        }
    }
}
