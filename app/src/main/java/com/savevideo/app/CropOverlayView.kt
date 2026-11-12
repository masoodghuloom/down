package com.savevideo.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Lets the user drag out a rectangle to crop. Draws a dimmed mask with a clear window. */
class CropOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val rect = RectF()
    private var anchorX = 0f
    private var anchorY = 0f

    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val border = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun hasSelection() = rect.width() > 16f && rect.height() > 16f

    fun selection(): RectF = RectF(rect)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorX = event.x
                anchorY = event.y
                rect.set(anchorX, anchorY, anchorX, anchorY)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                rect.set(
                    min(anchorX, event.x), min(anchorY, event.y),
                    max(anchorX, event.x), max(anchorY, event.y)
                )
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        if (hasSelection()) canvas.drawRect(rect, clear)
        canvas.restoreToCount(layer)
        if (hasSelection()) canvas.drawRect(rect, border)
    }
}
