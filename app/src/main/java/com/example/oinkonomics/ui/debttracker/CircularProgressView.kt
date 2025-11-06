package com.example.oinkonomics.ui.debttracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.oinkonomics.R

/**
 * SIMPLE CIRCULAR PROGRESS INDICATOR RENDERED AS A RING THAT FILLS CLOCKWISE.
 */
class CircularProgressView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		color = ContextCompat.getColor(context, R.color.teal_200)
		strokeWidth = resources.displayMetrics.density * 4f
	}
	private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		color = 0x33000000
		strokeWidth = ringPaint.strokeWidth
	}

	private val arcBounds = RectF()
	private var progressInternal: Float = 0f // 0..100

	fun setProgress(percent: Float) {
		val clamped = percent.coerceIn(0f, 100f)
		if (clamped != progressInternal) {
			progressInternal = clamped
			invalidate()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val size = minOf(width, height).toFloat()
		val inset = ringPaint.strokeWidth / 2f
		arcBounds.set(inset, inset, size - inset, size - inset)
		// Track
		canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)
		// Progress
		val sweep = 360f * (progressInternal / 100f)
		canvas.drawArc(arcBounds, -90f, sweep, false, ringPaint)
	}
}


