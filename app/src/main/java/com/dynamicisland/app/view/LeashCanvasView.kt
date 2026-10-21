package com.dynamicisland.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.dynamicisland.app.util.PreferencesHelper
import kotlin.math.sqrt

/**
 * LeashCanvasView: Background Window (Window A) hosted with FLAG_NOT_TOUCHABLE.
 *
 * Responsibilities:
 * - 100% of touches to this window pass straight through to whatever app is running underneath.
 * - Draws the front camera golden anchor ring.
 * - Draws the organic curved physics leash connecting the camera anchor to the interactive pet.
 * - In Touch Debug Mode: Displays distinct cyan outline indicating the pass-through area.
 */
class LeashCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val prefs = PreferencesHelper(context)

    // Leash Paint
    private val leashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val anchorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#F59E0B")
    }

    private val anchorCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D97706")
    }

    // Touch Debug Visualization Paints
    private val debugPassThroughPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#06B6D4") // Cyan for pass-through area
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val debugFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A06B6D4") // Ultra-light cyan tint for visualization
    }

    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06B6D4")
        textSize = 10f * context.resources.displayMetrics.density
        isFakeBoldText = true
    }

    private val leashPath = Path()
    private val debugRect = RectF()

    // Leash connection coordinates (relative to this view)
    private var petTopXPx: Float = 0f
    private var petTopYPx: Float = 0f
    private var leashSlackFactor: Float = 1.0f

    init {
        updateLeashColor()
    }

    fun updateLeashColor() {
        try {
            leashPaint.color = Color.parseColor(prefs.leashColor)
        } catch (e: Exception) {
            leashPaint.color = Color.parseColor("#F43F5E")
        }
        invalidate()
    }

    fun updatePetAttachment(petTopX: Float, petTopY: Float, slackFactor: Float = 1.0f) {
        this.petTopXPx = petTopX
        this.petTopYPx = petTopY
        this.leashSlackFactor = slackFactor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val anchorX = w / 2f
        val anchorY = 8.dpToPx().toFloat()

        // 1. Touch Debug Mode: Render Pass-Through Area Boundary
        if (prefs.isTouchDebugEnabled) {
            debugRect.set(1.dpToPx().toFloat(), 1.dpToPx().toFloat(), w - 1.dpToPx(), h - 1.dpToPx())
            canvas.drawRoundRect(debugRect, 8.dpToPx().toFloat(), 8.dpToPx().toFloat(), debugFillPaint)
            canvas.drawRoundRect(debugRect, 8.dpToPx().toFloat(), 8.dpToPx().toFloat(), debugPassThroughPaint)
            canvas.drawText("PASS-THROUGH AREA (Touches drop to app)", 8.dpToPx().toFloat(), h - 6.dpToPx(), debugTextPaint)
        }

        // 2. Camera Anchor Ring
        val ringRadius = 4.5.dpToPx().toFloat()
        canvas.drawCircle(anchorX, anchorY, ringRadius, anchorRingPaint)
        canvas.drawCircle(anchorX, anchorY, ringRadius * 0.45f, anchorCenterPaint)

        // 3. Curved Physics Leash
        if (petTopXPx > 0f && petTopYPx > 0f) {
            leashPath.reset()
            leashPath.moveTo(anchorX, anchorY + ringRadius)

            val midX = (anchorX + petTopXPx) / 2f
            val midY = (anchorY + petTopYPx) / 2f
            val dist = sqrt(((petTopXPx - anchorX) * (petTopXPx - anchorX)) + ((petTopYPx - anchorY) * (petTopYPx - anchorY)))

            // Dynamic gravity sag
            val sagY = (dist * 0.18f * leashSlackFactor)
            val driftOffsetXPx = petTopXPx - anchorX
            val controlX = midX + (driftOffsetXPx * 0.12f)
            val controlY = midY + sagY

            leashPath.quadTo(controlX, controlY, petTopXPx, petTopYPx)
            canvas.drawPath(leashPath, leashPaint)

            // Knot where leash meets pet sprout
            canvas.drawCircle(petTopXPx, petTopYPx, 2.dpToPx().toFloat(), anchorRingPaint)
        }
    }

    private fun Number.dpToPx(): Int =
        (this.toFloat() * context.resources.displayMetrics.density).toInt()
}
