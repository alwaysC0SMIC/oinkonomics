package com.example.oinkonomics.ui.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import kotlin.math.max
import kotlin.math.min

class SquareLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        // Default children to the center of the square card.
        gravity = Gravity.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val boundedWidth = if (widthMode == MeasureSpec.UNSPECIFIED) 0 else widthSize
        val boundedHeight = if (heightMode == MeasureSpec.UNSPECIFIED) 0 else heightSize

        var size = when {
            boundedWidth > 0 -> boundedWidth
            boundedHeight > 0 -> boundedHeight
            else -> {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                min(measuredWidth, measuredHeight)
            }
        }

        val minimumSquare = max(paddingLeft + paddingRight, paddingTop + paddingBottom)
        size = if (size > 0) max(size, minimumSquare) else minimumSquare

        val squareSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(squareSpec, squareSpec)
    }
}