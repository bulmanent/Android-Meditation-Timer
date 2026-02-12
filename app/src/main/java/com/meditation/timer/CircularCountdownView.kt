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
    private val outlineWidthPx = 2f * resources.displayMetrics.density
    private var remainingFraction = 1f

    private val remainingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.red_600)
    }

    private val elapsedSlicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pale_green_50)
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = outlineWidthPx
        color = ContextCompat.getColor(context, R.color.red_200)
    }

    fun setRemainingFraction(fraction: Float) {
        remainingFraction = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = outlineWidthPx / 2f
        arcBounds.set(
            paddingLeft + halfStroke,
            paddingTop + halfStroke,
            width - paddingRight - halfStroke,
            height - paddingBottom - halfStroke
        )
        canvas.drawOval(arcBounds, remainingPaint)

        val elapsedSweep = 360f * (1f - remainingFraction)
        if (elapsedSweep > 0f) {
            canvas.drawArc(arcBounds, -90f, elapsedSweep, true, elapsedSlicePaint)
        }
        canvas.drawOval(arcBounds, outlinePaint)
    }
}
