package com.easit.aiscanner.floatingWindow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class ScreenshotSelectorView @JvmOverloads constructor(
    paramContext: Context?,
    paramAttributeSet: AttributeSet? = null
) :
    View(paramContext, paramAttributeSet) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 160
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    var selectionRect: Rect? = null
        private set
    private var mStartPoint: Point? = null
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        selectionRect?.let { canvas.drawRect(it, selectionPaint) }
    }

    fun startSelection(paramInt1: Int, paramInt2: Int) {
        mStartPoint = Point(paramInt1, paramInt2)
        selectionRect = Rect(paramInt1, paramInt2, paramInt1, paramInt2)
    }

    fun stopSelection() {
        mStartPoint = null
        selectionRect = null
    }

    fun updateSelection(paramInt1: Int, paramInt2: Int) {
        val startPoint = mStartPoint ?: return
        val rect = selectionRect ?: return
        rect.left = startPoint.x.coerceAtMost(paramInt1)
        rect.right = startPoint.x.coerceAtLeast(paramInt1)
        rect.top = startPoint.y.coerceAtMost(paramInt2)
        rect.bottom = startPoint.y.coerceAtLeast(paramInt2)
        invalidate()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}
