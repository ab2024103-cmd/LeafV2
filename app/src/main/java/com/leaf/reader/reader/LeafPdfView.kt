package com.leaf.reader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.leaf.reader.model.HighlightColor
import com.leaf.reader.model.NormRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A highlight painted on top of a rendered PDF page. */
data class PdfHighlightRect(
    val rect: NormRect,
    val color: HighlightColor
)

/**
 * Renders a PDF page bitmap, paints highlights and turns drags into rectangular
 * selections (spec 5.2, 5.9). The bitmap is rendered at the current zoom, so the
 * canvas draws it 1:1 and panning happens in view pixels.
 */
class LeafPdfView(context: Context) : View(context) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var highlightRects: List<PdfHighlightRect> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var selectionRect: NormRect? = null
        set(value) {
            field = value
            invalidate()
        }

    var panX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var panY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var colors: ReaderColors = readerColors(com.leaf.reader.model.ReadingTheme.PAPER, false)
        set(value) {
            field = value
            invalidate()
        }

    var onSelection: ((NormRect) -> Unit)? = null
    var onTap: ((Float, Float) -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null
    var onPanChanged: ((Float, Float) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startPageX = 0f
    private var startPageY = 0f
    private var dragging = false
    private var baseX = 0f
    private var baseY = 0f

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onScaleChanged?.invoke(detector.scaleFactor)
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
                val point = toPage(e.x, e.y) ?: return false
                val hit = highlightRects.indexOfFirst { entry ->
                    val r = entry.rect
                    point.first in r.left..r.right && point.second in r.top..r.bottom
                }
                if (hit >= 0) {
                    onTap?.invoke(-1f, hit.toFloat())
                } else {
                    onTap?.invoke(point.first, point.second)
                }
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors.background.toArgbCompat())
        android.util.Log.i("LeafPdf", "onDraw size=${width}x$height hasBitmap=${bitmap != null}")
        val image = bitmap ?: return
        baseX = (width - image.width) / 2f
        baseY = (height - image.height) / 2f
        canvas.save()
        canvas.translate(baseX + panX, baseY + panY)

        highlightRects.forEach { entry ->
            highlightPaint.color = colors.tint(entry.color).toArgbCompat()
            canvas.drawRect(
                RectF(
                    entry.rect.left * image.width,
                    entry.rect.top * image.height,
                    entry.rect.right * image.width,
                    entry.rect.bottom * image.height
                ),
                highlightPaint
            )
        }

        canvas.drawBitmap(image, 0f, 0f, bitmapPaint)

        selectionRect?.let { rect ->
            selectionPaint.color = colors.selection.toArgbCompat()
            selectionPaint.style = Paint.Style.STROKE
            selectionPaint.strokeWidth = 2f * image.width / 600f
            canvas.drawRect(
                RectF(
                    rect.left * image.width,
                    rect.top * image.height,
                    rect.right * image.width,
                    rect.bottom * image.height
                ),
                selectionPaint
            )
        }
        canvas.restore()
    }

    private fun toPage(x: Float, y: Float): Pair<Float, Float>? {
        val image = bitmap ?: return null
        if (image.width == 0 || image.height == 0) return null
        val pageX = (x - baseX - panX) / image.width
        val pageY = (y - baseY - panY) / image.height
        return pageX to pageY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val point = toPage(event.x, event.y)
                startPageX = point?.first ?: 0f
                startPageY = point?.second ?: 0f
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                if (moved) {
                    if (panX != 0f || panY != 0f) {
                        panX += event.x - downX
                        panY += event.y - downY
                        onPanChanged?.invoke(panX, panY)
                        downX = event.x
                        downY = event.y
                    } else {
                        dragging = true
                        val point = toPage(event.x, event.y)
                        if (point != null) {
                            selectionRect = normRect(startPageX, startPageY, point.first, point.second)
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    selectionRect?.let { onSelection?.invoke(it) }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                selectionRect = null
                invalidate()
            }
        }
        return true
    }

    private fun normRect(x1: Float, y1: Float, x2: Float, y2: Float): NormRect = NormRect(
        left = min(x1, x2).coerceIn(0f, 1f),
        top = min(y1, y2).coerceIn(0f, 1f),
        right = max(x1, x2).coerceIn(0f, 1f),
        bottom = max(y1, y2).coerceIn(0f, 1f)
    )
}
