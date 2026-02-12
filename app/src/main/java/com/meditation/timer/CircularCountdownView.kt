package com.meditation.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class CircularCountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val arcBounds = RectF()
    private val strokeWidthPx = 18f * resources.displayMetrics.density
    private var remainingFraction = 1f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.red_200)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.red_600)
    }

    fun setRemainingFraction(fraction: Float) {
        remainingFraction = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = strokeWidthPx / 2f
        arcBounds.set(
            paddingLeft + halfStroke,
            paddingTop + halfStroke,
            width - paddingRight - halfStroke,
            height - paddingBottom - halfStroke
        )
        canvas.drawArc(arcBounds, -90f, 360f, false, basePaint)
        val sweep = 360f * remainingFraction
        if (sweep > 0f) {
            canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint)
        }
    }
}
