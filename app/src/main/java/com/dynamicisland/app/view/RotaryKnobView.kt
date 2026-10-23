package com.dynamicisland.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom interactive Rotary Knob View styled like a retro speaker volume knob.
 * Replaces bulky linear sliders with a compact, adorable dial control that saves
 * massive vertical screen space to allow a 100% zero-scroll all-in-one UI.
 *
 * Supports:
 * - 270-degree rotation sweep (-135° to +135°)
 * - Circular rotation and vertical drag adjustments
 * - Warm peach & creamsicle orange color scheme
 * - Live value display and haptic tick feedback
 */
class RotaryKnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minValue: Float = 0f
    var maxValue: Float = 100f
    var stepSize: Float = 1f
    var unitSuffix: String = ""
    var knobTitle: String = "KNOB"

    var onValueChanged: ((value: Float, fromUser: Boolean) -> Unit)? = null

    var value: Float = 0f
        set(newVal) {
            val clamped = newVal.coerceIn(minValue, maxValue)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    // Warm Peach & Creamsicle Orange Classy Palette
    private val colorTrackInactive = Color.parseColor("#3D2B24")
    private val colorTrackActive = Color.parseColor("#FF9F68") // Warm peach
    private val colorKnobBody = Color.parseColor("#221A17")
    private val colorKnobRim = Color.parseColor("#4A3730")
    private val colorIndicatorDot = Color.parseColor("#FFE4D6")
    private val colorValueText = Color.parseColor("#FFE4D6")
    private val colorTitleText = Color.parseColor("#FDBA74")
    private val colorBubbleBg = Color.parseColor("#FF9F68")
    private val colorBubbleShadow = Color.parseColor("#D97706")
    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val knobBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorKnobBody
    }

    private val knobRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colorKnobRim
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorIndicatorDot
    }

    private val textValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorValueText
        isFakeBoldText = true
    }

    private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorTitleText
        isFakeBoldText = true
    }

    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBubbleBg
    }

    private val bubbleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBubbleShadow
    }

    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val trackArcRect = RectF()
    private val bubbleRect = RectF()
    private val bubblePointerPath = Path()

    // 270 degree rotation sweep: from 135° to 405° (i.e. -135° to +135° relative to bottom)
    private val startAngle = 135f
    private val sweepAngle = 270f

    // Touch gesture tracking
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastHapticValue = Float.MIN_VALUE

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val titleHeight = 16f * density
        val availableHeight = h - titleHeight
        val cx = w / 2f
        val cy = availableHeight / 2f + 2f * density
        val outerRadius = (min(w, availableHeight) / 2f) * 0.88f
        val trackThickness = 3.5f * density

        // 1. Draw Background Inactive Track Arc (270 degrees)
        trackPaint.strokeWidth = trackThickness
        trackPaint.color = colorTrackInactive
        trackArcRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        canvas.drawArc(trackArcRect, startAngle, sweepAngle, false, trackPaint)

        // 2. Draw Active Track Arc showing current proportion
        val fraction = if (maxValue > minValue) ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) else 0f
        val activeSweep = sweepAngle * fraction
        if (activeSweep > 0.5f) {
            trackPaint.color = colorTrackActive
            canvas.drawArc(trackArcRect, startAngle, activeSweep, false, trackPaint)
        }

        // 3. Draw Rotating Knob Bezel / Dial
        val knobRadius = outerRadius - (trackThickness * 1.5f)
        canvas.drawCircle(cx, cy, knobRadius, knobBodyPaint)
        knobRimPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(cx, cy, knobRadius, knobRimPaint)

        // 4. Draw Indicator Pointer Dot on Knob Perimeter
        val currentAngleDeg = startAngle + (sweepAngle * fraction)
        val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
        val indicatorDistance = knobRadius * 0.72f
        val indX = (cx + (indicatorDistance * cos(currentAngleRad))).toFloat()
        val indY = (cy + (indicatorDistance * sin(currentAngleRad))).toFloat()
        val indRadius = 2.8f * density
        canvas.drawCircle(indX, indY, indRadius, indicatorPaint)

        // 5. Draw Value in Center of Knob
        textValuePaint.textSize = (knobRadius * 0.44f).coerceAtLeast(10f * density)
        val displayVal = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
        val valText = if (unitSuffix.isNotBlank()) "$displayVal$unitSuffix" else displayVal
        val textY = cy - ((textValuePaint.descent() + textValuePaint.ascent()) / 2f)
        canvas.drawText(valText, cx, textY, textValuePaint)

        // 6. Draw Knob Title Label at Bottom with live value
        textTitlePaint.textSize = 10f * density
        val bottomLabel = "$knobTitle: $valText"
        canvas.drawText(bottomLabel, cx, h - 2f * density, textTitlePaint)

        // 7. Floating Callout Value Bubble (Pops up above finger while dragging so value is never obscured!)
        if (isDragging) {
            val bubbleW = 58f * density
            val bubbleH = 22f * density
            val bubbleLeft = cx - (bubbleW / 2f)
            val bubbleTop = 0f
            val bubbleBottom = bubbleH
            val cornerR = 7f * density

            // Draw 3D shadow bevel
            bubbleRect.set(bubbleLeft, bubbleTop + 2.5f * density, bubbleLeft + bubbleW, bubbleBottom + 2.5f * density)
            canvas.drawRoundRect(bubbleRect, cornerR, cornerR, bubbleShadowPaint)

            // Draw bubble face
            bubbleRect.set(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleBottom)
            canvas.drawRoundRect(bubbleRect, cornerR, cornerR, bubbleBgPaint)

            // Downward pointer arrow pointing towards the knob dial
            bubblePointerPath.reset()
            bubblePointerPath.moveTo(cx - 4.5f * density, bubbleBottom)
            bubblePointerPath.lineTo(cx + 4.5f * density, bubbleBottom)
            bubblePointerPath.lineTo(cx, bubbleBottom + 3.5f * density)
            bubblePointerPath.close()
            canvas.drawPath(bubblePointerPath, bubbleBgPaint)

            // Bubble Text
            bubbleTextPaint.textSize = 11.5f * density
            val bTextY = (bubbleTop + bubbleBottom) / 2f - ((bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f)
            canvas.drawText(valText, cx, bTextY, bubbleTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dy = lastTouchY - event.y
                    val dx = event.x - lastTouchX

                    // Support both circular angle tracking and smooth vertical flick
                    if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 2f * density) {
                        // Vertical flick delta
                        val deltaVal = (dy / (80f * density)) * (maxValue - minValue) * 0.25f
                        updateValue(value + deltaVal, fromUser = true)
                    } else {
                        // Circular angle tracking
                        updateFromTouch(event.x, event.y)
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float, touchY: Float) {
        val titleHeight = 16f * density
        val availableHeight = height - titleHeight
        val cx = width / 2f
        val cy = availableHeight / 2f + 2f * density

        val dx = touchX - cx
        val dy = touchY - cy

        // Calculate touch angle relative to center in degrees (0 to 360)
        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angleDeg < 0) {
            angleDeg += 360f
        }

        // Map angle from startAngle (135°) through bottom (270°) to end (405° / 45°)
        val relativeAngle = when {
            angleDeg >= 135f -> angleDeg - 135f
            angleDeg <= 45f -> (360f - 135f) + angleDeg
            else -> {
                // In the dead zone at bottom-left (45° to 135°)
                if (angleDeg < 90f) 270f else 0f
            }
        }

        val fraction = (relativeAngle / sweepAngle).coerceIn(0f, 1f)
        val computedVal = minValue + (fraction * (maxValue - minValue))
        updateValue(computedVal, fromUser = true)
    }

    private fun updateValue(targetValue: Float, fromUser: Boolean) {
        val stepped = if (stepSize > 0f) {
            val steps = Math.round((targetValue - minValue) / stepSize)
            minValue + (steps * stepSize)
        } else {
            targetValue
        }.coerceIn(minValue, maxValue)

        if (stepped != value) {
            value = stepped
            if (fromUser && stepped != lastHapticValue) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastHapticValue = stepped
            }
            onValueChanged?.invoke(value, fromUser)
        }
    }

    private val density: Float
        get() = resources.displayMetrics.density
}
