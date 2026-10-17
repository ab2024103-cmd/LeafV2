package com.leaf.reader.engine

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import com.leaf.reader.model.FileKind

enum class BlockStyle { TITLE, HEADING, BODY, QUOTE, LIST, TABLE_ROW, SLIDE }

/** A logical unit of a text document. */
data class TextBlock(
    val text: String,
    val style: BlockStyle = BlockStyle.BODY,
    val pageBreakAfter: Boolean = false
)

/** The parsed content of a text-like document. */
class ParsedDocument(
    val kind: FileKind,
    val title: String,
    val blocks: List<TextBlock>
) {
    private val spannable: SpannableStringBuilder by lazy { buildSpannable() }

    /** Full plain text of the document (character offsets are stable across renders). */
    val plainText: String by lazy { spannable.toString() }

    /** Char offsets where a forced page break happens. */
    val pageBreaks: List<Int> by lazy {
        val breaks = mutableListOf<Int>()
        var offset = 0
        blocks.forEach { block ->
            offset += block.text.length
            if (block.pageBreakAfter) breaks.add(offset)
            offset += 2 // the "\n\n" separator added below
        }
        breaks
    }

    fun buildSpannable(): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        blocks.forEach { block ->
            val start = builder.length
            builder.append(block.text)
            when (block.style) {
                BlockStyle.TITLE -> {
                    builder.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD), start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(1.45f), start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                BlockStyle.HEADING -> {
                    builder.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD), start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(1.2f), start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                BlockStyle.SLIDE -> {
                    builder.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD), start, builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                BlockStyle.QUOTE -> builder.setSpan(
                    StyleSpan(android.graphics.Typeface.ITALIC), start, builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                else -> Unit
            }
            builder.append("\n\n")
        }
        return builder
    }

    fun spannableText(): CharSequence = spannable
}
