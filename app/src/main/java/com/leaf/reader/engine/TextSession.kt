package com.leaf.reader.engine

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.TextCompat
import com.leaf.reader.model.FileKind
import com.leaf.reader.model.ReadingTypeface

/** One page of a text document: a slice of the shared layout. */
data class PageSlice(
    val startOffset: Int,
    val endOffset: Int,
    val topPx: Int,
    val bottomPx: Int
)

/**
 * Paginated text session. A single StaticLayout is built for the whole document and
 * pages are vertical slices of it, which keeps character offsets stable for
 * highlights, bookmarks and search (spec 5.9).
 */
class TextSession(
    val kind: FileKind,
    val parsed: ParsedDocument,
    typeface: ReadingTypeface
) {

    private val paint = TextPaint().apply {
        textSize = DEFAULT_TEXT_SIZE
        isAntiAlias = true
        this.typeface = TextCompat.create(
            android.graphics.Typeface.SERIF,
            if (typeface == ReadingTypeface.SERIF) android.graphics.Typeface.NORMAL else android.graphics.Typeface.SANS_SERIF
        )
    }

    private var layout: StaticLayout? = null
    private var slices: List<PageSlice> = emptyList()
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var textSize = DEFAULT_TEXT_SIZE

    val plainText: String get() = parsed.plainText

    fun configure(width: Int, height: Int, newTextSize: Float) {
        if (width <= 0 || height <= 0) return
        if (width == viewportWidth && height == viewportHeight && newTextSize == textSize && layout != null) return
        viewportWidth = width
        viewportHeight = height
        textSize = newTextSize
        paint.textSize = newTextSize
        rebuild()
    }

    private fun rebuild() {
        val text = parsed.spannableText()
        val built = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, viewportWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.22f)
            .setIncludePad(false)
            .build()
        layout = built
        slices = computeSlices(built, viewportHeight, parsed.pageBreaks)
    }

    val pageCount: Int get() = slices.size.coerceAtLeast(1)

    fun slice(page: Int): PageSlice = slices.getOrNull(page.coerceIn(0, slices.lastIndex))
        ?: PageSlice(0, parsed.plainText.length, 0, viewportHeight)

    fun layout(): StaticLayout? = layout

    fun pageOfOffset(offset: Int): Int {
        slices.forEachIndexed { index, slice ->
            if (offset < slice.endOffset) return index
        }
        return (slices.size - 1).coerceAtLeast(0)
    }

    fun offsetOfPage(page: Int): Int = slice(page).startOffset

    /** Text of a page, used for search snippets and copy. */
    fun pageText(page: Int): String {
        val s = slice(page)
        val text = parsed.plainText
        val start = s.startOffset.coerceIn(0, text.length)
        val end = s.endOffset.coerceIn(start, text.length)
        return text.substring(start, end)
    }

    private fun computeSlices(
        built: StaticLayout,
        pageHeight: Int,
        forcedBreaks: List<Int>
    ): List<PageSlice> {
        val out = mutableListOf<PageSlice>()
        if (built.lineCount == 0) {
            return listOf(PageSlice(0, 0, 0, pageHeight))
        }
        val breaks = forcedBreaks.filter { it > 0 }.sorted()
        var pageStartOffset = 0
        var pageStartLine = 0
        for (line in 0 until built.lineCount) {
            val lineStart = built.getLineStart(line)
            val lineEnd = built.getLineEnd(line)
            val forced = breaks.firstOrNull { it > pageStartOffset && it <= lineEnd }
            if (forced != null) {
                val top = built.getLineTop(pageStartLine)
                val bottom = built.getLineBottom((line - 1).coerceAtLeast(pageStartLine))
                out.add(PageSlice(pageStartOffset, forced, top, bottom))
                pageStartOffset = forced
                pageStartLine = line
            }
            val contentHeight = built.getLineBottom(line) - built.getLineTop(pageStartLine)
            if (contentHeight > pageHeight && line > pageStartLine) {
                val top = built.getLineTop(pageStartLine)
                val bottom = built.getLineBottom(line - 1)
                out.add(PageSlice(pageStartOffset, lineStart, top, bottom))
                pageStartOffset = lineStart
                pageStartLine = line
            }
        }
        val lastTop = built.getLineTop(pageStartLine)
        val lastBottom = built.getLineBottom(built.lineCount - 1)
        out.add(PageSlice(pageStartOffset, built.getLineEnd(built.lineCount - 1), lastTop, lastBottom))
        return out
    }

    companion object {
        const val DEFAULT_TEXT_SIZE = 48f
    }
}
