package com.dynamicisland.app.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.dynamicisland.app.util.PreferencesHelper
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * TetheredPetStageView: Transparent stage around the front camera where the pet lives
 * tied to a natural curved leash/thread anchored right at the camera position.
 *
 * Behaviors:
 * - Leash curves and sags with organic physics between camera anchor and pet sprout
 * - Continuous natural floating, tiny bounces, and physics tilts
 * - Autonomous drift engine: never sits in one spot for > 10 seconds (drifts every 4-8s)
 * - Drifts left, right, underneath to dangle playfully, or leans to one side
 * - Zero back-and-forth walking (pure buoyant floating companion)
 */
class TetheredPetStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val petView: PixelPetView
    private val prefs = PreferencesHelper(context)
    private val handler = Handler(Looper.getMainLooper())

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

    private val leashPath = Path()

    // Physics & Drift State (in DP)
    private var currentDriftXDp = -24f
    private var currentDriftYDp = 22f

    private var targetDriftXDp = -24f
    private var targetDriftYDp = 22f

    private var floatBobYDp = 0f
    private var tiltAngleDeg = 0f
    private var leashSlackFactor = 1.0f

    private var driftAnimator: ValueAnimator? = null
    private var continuousFloatAnimator: ValueAnimator? = null
    private var isDriftEngineRunning = false

    // Autonomous drift scheduler (every 4 to 8 seconds)
    private val driftRunnable = object : Runnable {
        override fun run() {
            if (!isDriftEngineRunning) return
            pickNextFloatingSpot()
            val nextDelayMs = Random.nextLong(4500L, 8500L) // 4.5s to 8.5s (never sits > 10s)
            handler.postDelayed(this, nextDelayMs)
        }
    }

    init {
        setWillNotDraw(false) // Enables onDraw for the leash

        petView = PixelPetView(context).apply {
            setAnimationIntensity(prefs.animationIntensity)
        }
        addView(petView)

        setupFloatAnimators()
        updateLeashColor()
    }

    fun updateLeashColor() {
        try {
            leashPaint.color = Color.parseColor(prefs.leashColor)
        } catch (e: Exception) {
            leashPaint.color = Color.parseColor("#F43F5E") // Default cute red ribbon
        }
        invalidate()
    }

    private fun setupFloatAnimators() {
        // Continuous tiny natural buoyant bouncing & tilts
        continuousFloatAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                val intensity = prefs.animationIntensity

                floatBobYDp = sin(p) * (2.8f * intensity)
                // Tilt sways based on horizontal position from camera
                val baseTilt = (currentDriftXDp * 0.28f).coerceIn(-12f, 12f)
                val dynamicSway = sin(p) * (2.0f * intensity)
                tiltAngleDeg = baseTilt + dynamicSway

                updatePetViewLayout()
                invalidate() // Redraw curved leash
            }
        }
    }

    fun startDriftEngine() {
        if (isDriftEngineRunning) return
        isDriftEngineRunning = true

        if (continuousFloatAnimator?.isStarted != true) {
            continuousFloatAnimator?.start()
        }

        // Initial spot selection
        pickNextFloatingSpot()
        handler.postDelayed(driftRunnable, 5000L)
    }

    fun stopDriftEngine() {
        isDriftEngineRunning = false
        handler.removeCallbacksAndMessages(null)
        driftAnimator?.cancel()
        continuousFloatAnimator?.cancel()
    }

    /**
     * Autonomous spot picker: floats to a new organic spot around the camera cutout
     */
    fun pickNextFloatingSpot() {
        val leashLength = prefs.leashLengthDp.toFloat()

        // Spots around the camera:
        // 1. Left of camera
        // 2. Right of camera
        // 3. Directly underneath (dangling pendant)
        // 4. Leaning left
        // 5. Leaning right
        // 6. Close hover looking at lens
        val spotPick = Random.nextInt(6)

        val (nextX, nextY) = when (spotPick) {
            0 -> Pair(-(leashLength * 0.75f), leashLength * 0.55f) // Left
            1 -> Pair(leashLength * 0.75f, leashLength * 0.55f)  // Right
            2 -> Pair(0f, leashLength * 0.95f)                  // Underneath dangling
            3 -> Pair(-(leashLength * 0.55f), leashLength * 0.8f) // Lean left
            4 -> Pair(leashLength * 0.55f, leashLength * 0.8f)  // Lean right
            else -> Pair(
                if (Random.nextBoolean()) -(leashLength * 0.4f) else (leashLength * 0.4f),
                leashLength * 0.4f // Close hover
            )
        }

        driftTo(nextX, nextY)
    }

    fun driftTo(targetXDp: Float, targetYDp: Float, durationMs: Long = 1600L) {
        val startX = currentDriftXDp
        val startY = currentDriftYDp

        driftAnimator?.cancel()
        driftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                currentDriftXDp = startX + ((targetXDp - startX) * f)
                currentDriftYDp = startY + ((targetYDp - startY) * f)

                // Leash sags and tightens dynamically with momentum
                leashSlackFactor = 1f + (sin(f * Math.PI.toFloat()) * 0.35f)
                updatePetViewLayout()
                invalidate()
            }
        }
        driftAnimator?.start()
    }

    private fun updatePetViewLayout() {
        val petSizePx = prefs.petSizeDp.dpToPx()
        val anchorXPx = width / 2f
        val anchorYPx = 8.dpToPx().toFloat()

        val petCenterXPx = anchorXPx + currentDriftXDp.dpToPx()
        val petCenterYPx = anchorYPx + (currentDriftYDp + floatBobYDp).dpToPx()

        val left = (petCenterXPx - (petSizePx / 2f)).toInt()
        val top = (petCenterYPx - (petSizePx / 2f)).toInt()

        petView.layout(left, top, left + petSizePx, top + petSizePx)
        petView.rotation = tiltAngleDeg
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updatePetViewLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDriftEngine()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDriftEngine()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        if (w <= 0f) return

        val anchorX = w / 2f
        val anchorY = 8.dpToPx().toFloat()

        // 1. Draw Anchor Ring / Knot at Camera Lens Center
        val ringRadius = 4.5.dpToPx().toFloat()
        canvas.drawCircle(anchorX, anchorY, ringRadius, anchorRingPaint)
        canvas.drawCircle(anchorX, anchorY, ringRadius * 0.45f, anchorCenterPaint)

        // 2. Calculate Pet Attachment Point (Head Sprout at col 14, row 5 of Pet)
        val petSizePx = prefs.petSizeDp.dpToPx().toFloat()
        val petCenterXPx = anchorX + currentDriftXDp.dpToPx()
        val petCenterYPx = anchorY + (currentDriftYDp + floatBobYDp).dpToPx()

        // Top of pet head sprout attachment
        val petTopX = petCenterXPx
        val petTopY = petCenterYPx - (petSizePx * 0.42f)

        // 3. Draw Curved Physics String / Leash
        leashPath.reset()
        leashPath.moveTo(anchorX, anchorY + ringRadius)

        // Calculate natural curved sagging control point
        val midX = (anchorX + petTopX) / 2f
        val midY = (anchorY + petTopY) / 2f
        val dist = sqrt(((petTopX - anchorX) * (petTopX - anchorX)) + ((petTopY - anchorY) * (petTopY - anchorY)))

        // Leash sags down slightly with gravity
        val sagY = (dist * 0.18f * leashSlackFactor)
        val controlX = midX + (currentDriftXDp.dpToPx() * 0.12f)
        val controlY = midY + sagY

        leashPath.quadTo(controlX, controlY, petTopX, petTopY)
        canvas.drawPath(leashPath, leashPaint)

        // Tiny knot where leash ties to the sprout
        canvas.drawCircle(petTopX, petTopY, 2.dpToPx().toFloat(), anchorRingPaint)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!prefs.isTouchReactionEnabled) {
            return false // 100% click-through when touch reactions disabled
        }

        // Check if touch hit near the pet sprite
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val petSizePx = prefs.petSizeDp.dpToPx().toFloat()
            val petCenterXPx = (width / 2f) + currentDriftXDp.dpToPx()
            val petCenterYPx = 8.dpToPx().toFloat() + (currentDriftYDp + floatBobYDp).dpToPx()

            val dx = abs(ev.x - petCenterXPx)
            val dy = abs(ev.y - petCenterYPx)

            if (dx <= petSizePx * 0.65f && dy <= petSizePx * 0.65f) {
                petView.handleUserTap()
                // Little leash jiggle on tap
                leashSlackFactor = 1.6f
                postDelayed({ leashSlackFactor = 1.0f; invalidate() }, 300)
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun Number.dpToPx(): Int =
        (this.toFloat() * context.resources.displayMetrics.density).toInt()

    private fun Float.dpToPx(): Float =
        this * context.resources.displayMetrics.density
}
