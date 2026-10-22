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

    // Keychain Wire Paints: 2-tone Duolingo 2.5D styling (Noticeably Thicker & Chunky)
    private val leashShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val leashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val anchorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#F59E0B")
    }

    private val anchorCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D97706")
    }

    private val keychainLinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FCD34D") // Metallic gold sheen for keychain connector link
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

    // Leash connection coordinates & Keychain Physics State
    private var petTopXPx: Float = 0f
    private var petTopYPx: Float = 0f
    private var leashSlackFactor: Float = 1.0f
    private var velocityXDp: Float = 0f
    private var shiverAmplitudeDp: Float = 0f
    private var shiverPhase: Float = 0f

    init {
        val density = context.resources.displayMetrics.density
        leashShadowPaint.strokeWidth = 5.4f * density
        leashPaint.strokeWidth = 3.6f * density
        anchorRingPaint.strokeWidth = 2.8f * density
        keychainLinkPaint.strokeWidth = 2.2f * density
        updateLeashColor()
    }
    fun updateLeashColor() {
        val baseColor = try {
            Color.parseColor(prefs.leashColor)
        } catch (e: Exception) {
            Color.parseColor("#F43F5E")
        }
        leashPaint.color = baseColor
        leashShadowPaint.color = getShadowColor(baseColor)
        invalidate()
    }

    private fun getShadowColor(color: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * 0.58f).toInt()
        val g = (Color.green(color) * 0.58f).toInt()
        val b = (Color.blue(color) * 0.58f).toInt()
        return Color.argb(a, r, g, b)
    }

    fun updateKeychainWire(
        petTopX: Float,
        petTopY: Float,
        slackFactor: Float = 1.0f,
        velocityXDp: Float = 0f,
        shiverAmplitudeDp: Float = 0f,
        shiverPhase: Float = 0f
    ) {
        this.petTopXPx = petTopX
        this.petTopYPx = petTopY
        this.leashSlackFactor = slackFactor
        this.velocityXDp = velocityXDp
        this.shiverAmplitudeDp = shiverAmplitudeDp
        this.shiverPhase = shiverPhase
        invalidate()
    }

    fun updatePetAttachment(petTopX: Float, petTopY: Float, slackFactor: Float = 1.0f) {
        updateKeychainWire(petTopX, petTopY, slackFactor, this.velocityXDp, this.shiverAmplitudeDp, this.shiverPhase)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val anchorX = w / 2f
        val anchorY = 16.dpToPx().toFloat()
        // 1. Touch Debug Mode: Render Pass-Through Area Boundary
        if (prefs.isTouchDebugEnabled) {
            debugRect.set(1.dpToPx().toFloat(), 1.dpToPx().toFloat(), w - 1.dpToPx(), h - 1.dpToPx())
            canvas.drawRoundRect(debugRect, 8.dpToPx().toFloat(), 8.dpToPx().toFloat(), debugFillPaint)
            canvas.drawRoundRect(debugRect, 8.dpToPx().toFloat(), 8.dpToPx().toFloat(), debugPassThroughPaint)
            canvas.drawText("PASS-THROUGH AREA (Touches drop to app)", 8.dpToPx().toFloat(), h - 6.dpToPx(), debugTextPaint)
        }

        // 2. Camera Anchor Keyring & Keychain Hardware (Chunky & Tactile)
        val ringRadius = 5.5.dpToPx().toFloat()
        canvas.drawCircle(anchorX, anchorY, ringRadius, anchorRingPaint)
        canvas.drawCircle(anchorX, anchorY, ringRadius * 0.45f, anchorCenterPaint)
        // 3. Multi-Segment Keychain Wire with Fluid Inertia & Shiver Ripples
        if (petTopXPx > 0f && petTopYPx > 0f) {
            val startX = anchorX
            val startY = anchorY + ringRadius
            val dx = petTopXPx - startX
            val dy = petTopYPx - startY
            val length = sqrt((dx * dx) + (dy * dy))

            if (length > 2f) {
                val nx = -dy / length
                val ny = dx / length

                // Catenary sag droop (flexible & rich)
                val baseSagPx = length * 0.22f * leashSlackFactor
                // Swing inertia flex (middle of wire flexes opposing velocity)
                val density = context.resources.displayMetrics.density
                val bendPx = (-velocityXDp * 1.8f) * density
                // Rapid shake shiver vibration
                val shiverPx = (shiverAmplitudeDp * density)

                val segments = 18
                leashPath.reset()

                for (i in 0..segments) {
                    val u = i.toFloat() / segments.toFloat()
                    val bx = startX + (u * dx)
                    val by = startY + (u * dy)

                    // Parabolic envelope: 0 at anchor (u=0) and sprout (u=1), 1 at middle (u=0.5)
                    val envelope = 4f * u * (1f - u)
                    val sagY = envelope * baseSagPx

                    // Inertia flex & secondary S-curve counter-wave
                    val arcBend = kotlin.math.sin(u * Math.PI.toFloat()) * bendPx
                    val sBend = kotlin.math.sin(u * 2f * Math.PI.toFloat()) * (bendPx * 0.40f)
                    // Standing wave harmonic shiver (shivers and ripples when phone is shaken)
                    val wave = kotlin.math.sin(shiverPhase + u * 2.5f * Math.PI.toFloat()) +
                            0.35f * kotlin.math.sin(shiverPhase * 2f + u * 5f * Math.PI.toFloat())
                    val shiver = envelope * shiverPx * wave

                    val lateralDeflection = arcBend + sBend + shiver
                    val ptX = bx + (lateralDeflection * nx)
                    val ptY = by + sagY + (lateralDeflection * ny)

                    if (i == 0) {
                        leashPath.moveTo(ptX, ptY)
                    } else {
                        leashPath.lineTo(ptX, ptY)
                    }
                }

                // Draw 2-Tone Duolingo-style wire: darker shadow path underneath + bright core
                canvas.drawPath(leashPath, leashShadowPaint)
                canvas.drawPath(leashPath, leashPaint)

                // Keychain Jump-Ring Hinge (swivels with wire departure angle)
                val linkAngleRad = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                val linkAngleDeg = Math.toDegrees(linkAngleRad).toFloat()
                canvas.save()
                canvas.translate(startX, startY)
                canvas.rotate(linkAngleDeg - 90f)
                val linkOval = RectF(-2.4.dpToPx().toFloat(), -1.0.dpToPx().toFloat(), 2.4.dpToPx().toFloat(), 4.2.dpToPx().toFloat())
                canvas.drawOval(linkOval, keychainLinkPaint)
                canvas.restore()

                // Clasp bead where keychain wire connects to pet sprout
                canvas.drawCircle(petTopXPx, petTopYPx, 3.4.dpToPx().toFloat(), anchorRingPaint)
                canvas.drawCircle(petTopXPx, petTopYPx, 1.8.dpToPx().toFloat(), keychainLinkPaint)
            }
        }
    }

    private fun Number.dpToPx(): Int =
        (this.toFloat() * context.resources.displayMetrics.density).toInt()
}
