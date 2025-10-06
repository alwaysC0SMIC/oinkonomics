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

// DRAWS A CUSTOM RADIAL PROGRESS INDICATOR.
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
        // ADJUSTS HOW THICK THE RING SHOULD BE.
        strokeWidthPx = widthPx
        requestLayout()
        invalidate()
    }

    fun setProgress(progress: Float, max: Float) {
        // UPDATES THE CURRENT FILL FRACTION.
        progressFraction = if (max <= 0f) {
            0f
        } else {
            (progress / max).coerceIn(0f, 1f)
        }
        invalidate()
    }

    fun setColors(progressColor: Int) {
        // APPLIES A BASE COLOR AND DERIVED TRACK COLORS.
        val trackColor = ColorUtils.setAlphaComponent(progressColor, (0.25f * 255).toInt())
        val innerColor = ColorUtils.setAlphaComponent(Color.WHITE, 255)
        setColors(progressColor, trackColor, innerColor)
    }

    fun setColors(progressColor: Int, trackColor: Int, innerColor: Int) {
        // SETS THE EXACT COLORS FOR RING, TRACK, AND CENTER.
        progressPaint.color = progressColor
        trackPaint.color = trackColor
        innerPaint.color = innerColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // FORCES A SQUARE SIZE BASED ON AVAILABLE SPACE.
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val measured = if (size == 0) {
            resources.getDimensionPixelSize(R.dimen.radial_progress_default_size)
        } else size
        setMeasuredDimension(measured, measured)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // UPDATES BOUNDS WHEN THE VIEW DIMENSIONS CHANGE.
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f
        arcBounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        // RENDERS THE TRACK, INNER FILL, AND PROGRESS ARC.
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