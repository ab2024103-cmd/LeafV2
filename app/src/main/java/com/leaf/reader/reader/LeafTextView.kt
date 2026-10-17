package com.leaf.reader.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.leaf.reader.model.HighlightColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A rendered text range with its colour. */
data class TextHighlightRange(
    val start: Int,
    val end: Int,
    val color: HighlightColor
)

/**
 * Draws a paginated text layout and turns touch gestures into text selections
 * (spec 5.2, 3.14, 3.16).
 */
class LeafTextView(context: Context) : View(context) {

    var textLayout: StaticLayout? = null
    var pageTop: Int = 0
    var highlightRanges: List<TextHighlightRange> = emptyList()
    var selectionRange: IntRange? = null
    var scale: Float = 1f
    var panX: Float = 0f
    var panY: Float = 0f
    var colors: ReaderColors = readerColors(com.leaf.reader.model.ReadingTheme.PAPER, false)

    var onSelectionChanged: ((Int, Int) -> Unit)? = null
    var onTap: ((Int) -> Unit)? = null
    var onHighlightTap: ((Int) -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null
    var onPanChanged: ((Float, Float) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var anchorOffset = 0
    private var dragging = false

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (scale * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (next != scale) {
                    scale = next
                    onScaleChanged?.invoke(next)
                    invalidate()
                }
                return true
            }
        }
    )

    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTap?.invoke()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val layout = textLayout ?: return false
                val offset = offsetFor(layout, e.x, e.y)
                val hit = highlightRanges.indexOfFirst { offset >= it.start && offset < it.end }
                if (hit >= 0) onHighlightTap?.invoke(hit) else onTap?.invoke(offset)
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors.background.toArgbCompat())
        val layout = textLayout ?: return
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale)
        canvas.save()
        canvas.translate(0f, -pageTop.toFloat())

        highlightRanges.forEach { range ->
            highlightPaint.color = colors.tint(range.color).toArgbCompat()
            rectsForRange(layout, range.start, range.end).forEach { rect ->
                canvas.drawRect(rect, highlightPaint)
            }
        }

        selectionRange?.let { selection ->
            selectionPaint.color = colors.selection.toArgbCompat()
            rectsForRange(layout, selection.first, selection.last + 1).forEach { rect ->
                canvas.drawRect(rect, selectionPaint)
            }
        }

        layout.paint.color = colors.text.toArgbCompat()
        layout.draw(canvas)
        canvas.restore()
        canvas.restore()
    }

    private fun offsetFor(layout: StaticLayout, x: Float, y: Float): Int {
        val localX = (x - panX) / scale
        val localY = ((y - panY) / scale + pageTop).coerceIn(0f, (layout.height - 1).coerceAtLeast(0).toFloat())
        val line = layout.getLineForVertical(localY.toInt())
        return layout.getOffsetForHorizontal(line, localX)
    }

    private fun rectsForRange(layout: Layout, start: Int, end: Int): List<RectF> {
        if (end <= start) return emptyList()
        val out = mutableListOf<RectF>()
        val firstLine = layout.getLineForOffset(start)
        val lastLine = layout.getLineForOffset(end - 1)
        for (line in firstLine..lastLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val from = max(start, lineStart)
            val to = min(end, lineEnd)
            if (to <= from) continue
            val x1 = layout.getPrimaryHorizontal(from)
            val x2 = layout.getSecondaryHorizontal(to)
            out.add(
                RectF(
                    min(x1, x2),
                    layout.getLineTop(line).toFloat(),
                    max(x1, x2),
                    layout.getLineBottom(line).toFloat()
                )
            )
        }
        return out
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                anchorOffset = textLayout?.let { offsetFor(it, event.x, event.y) } ?: 0
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                if (moved) {
                    if (scale > 1.02f) {
                        panX += event.x - downX
                        panY += event.y - downY
                        onPanChanged?.invoke(panX, panY)
                        downX = event.x
                        downY = event.y
                        invalidate()
                    } else {
                        dragging = true
                        val layout = textLayout ?: return true
                        val current = offsetFor(layout, event.x, event.y)
                        val from = min(anchorOffset, current)
                        val to = max(anchorOffset, current) + 1
                        selectionRange = from until to
                        onSelectionChanged?.invoke(from, to)
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    onSelectionChanged?.invoke(
                        selectionRange?.first ?: 0,
                        (selectionRange?.last ?: 0) + 1
                    )
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return true
    }

    companion object {
        const val MIN_ZOOM = 0.7f
        const val MAX_ZOOM = 2.5f
    }
}

internal fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt().coerceIn(0, 255),
    (red * 255).toInt().coerceIn(0, 255),
    (green * 255).toInt().coerceIn(0, 255),
    (blue * 255).toInt().coerceIn(0, 255)
)
