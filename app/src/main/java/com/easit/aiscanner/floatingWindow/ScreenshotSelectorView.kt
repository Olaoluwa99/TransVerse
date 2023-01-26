package com.easit.aiscanner.floatingWindow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScreenshotSelectorView @JvmOverloads constructor(
    paramContext: Context?,
    paramAttributeSet: AttributeSet? = null
) :
    View(paramContext, paramAttributeSet) {
    private val mPaintBackground = Paint(-16777216)
    private val mPaintSelection: Paint
    var selectionRect: Rect? = null
        private set
    private var mStartPoint: Point? = null
    override fun draw(paramCanvas: Canvas) {
        super.draw(paramCanvas)
        val rect1 = selectionRect
        if (rect1 == null) {
            paramCanvas.drawRect(0.0f, 0.0f, 0.0f, 0.0f, mPaintBackground)
            return
        }
        paramCanvas.drawRect(
            rect1.left.toFloat(),
            selectionRect!!.top.toFloat(),
            selectionRect!!.right.toFloat(),
            selectionRect!!.bottom.toFloat(),
            mPaintBackground
        )
        val rect2 = selectionRect
        if (rect2 != null) paramCanvas.drawRect(rect2, mPaintSelection)
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
        val rect = selectionRect
        if (rect != null) {
            rect.left = mStartPoint!!.x.coerceAtMost(paramInt1)
            selectionRect!!.right = mStartPoint!!.x.coerceAtLeast(paramInt1)
            selectionRect!!.top = mStartPoint!!.y.coerceAtMost(paramInt2)
            selectionRect!!.bottom = mStartPoint!!.y.coerceAtLeast(paramInt2)
            invalidate()
        }
    }

    init {
        mPaintBackground.alpha = 160
        mPaintSelection = Paint(0)
        mPaintSelection.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
}