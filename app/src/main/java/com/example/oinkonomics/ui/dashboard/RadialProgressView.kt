package com.example.oinkonomics.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.oinkonomics.R
import kotlin.math.max
import kotlin.math.min

class RadialProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcBounds = RectF()

    private var strokeWidthPx = resources.getDimension(R.dimen.radial_progress_stroke)
    private var progressFraction = 0f

    init {
        setColors(Color.parseColor("#5CC477"))
    }

    fun setStrokeWidth(widthPx: Float) {
        strokeWidthPx = widthPx
        requestLayout()
        invalidate()
    }

    fun setProgress(progress: Float, max: Float) {
        progressFraction = if (max <= 0f) {
            0f
        } else {
            (progress / max).coerceIn(0f, 1f)
        }
        invalidate()
    }

    fun setColors(progressColor: Int) {
        val trackColor = ColorUtils.setAlphaComponent(progressColor, (0.25f * 255).toInt())
        val innerColor = ColorUtils.setAlphaComponent(Color.WHITE, 255)
        setColors(progressColor, trackColor, innerColor)
    }

    fun setColors(progressColor: Int, trackColor: Int, innerColor: Int) {
        progressPaint.color = progressColor
        trackPaint.color = trackColor
        innerPaint.color = innerColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val measured = if (size == 0) {
            resources.getDimensionPixelSize(R.dimen.radial_progress_default_size)
        } else size
        setMeasuredDimension(measured, measured)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f
        arcBounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        trackPaint.strokeWidth = strokeWidthPx
        progressPaint.strokeWidth = strokeWidthPx

        val cx = width / 2f
        val cy = height / 2f
        val radius = (width / 2f) - (strokeWidthPx / 2f)
        val innerRadius = max(0f, radius - (strokeWidthPx * 0.9f))

        canvas.drawCircle(cx, cy, radius, trackPaint)
        canvas.drawCircle(cx, cy, innerRadius, innerPaint)

        if (progressFraction > 0f) {
            canvas.drawArc(
                arcBounds,
                -90f,
                progressFraction * 360f,
                false,
                progressPaint
            )
        }
    }
}