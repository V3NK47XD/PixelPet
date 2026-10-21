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
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.dynamicisland.app.util.PreferencesHelper
import kotlin.math.abs
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
 * - DRAGGABLE by user, strictly constrained within leash radius!
 * - Elastic spring-back oscillation when released
 * - TAP INTERACTIONS:
 *   * Sleep / Wake toggle: tapping sleepy pet wakes it up; tapping again nods it back to sleep
 *   * Rapid multi-taps (3+ times): gets furiously angry for a few seconds with steam & rage!
 *   * Single tap: winks, waves, giggles, or adjusts glasses!
 */
class TetheredPetStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val petView: PixelPetView
    private val prefs = PreferencesHelper(context)
    private val handler = Handler(Looper.getMainLooper())
    private val touchableRegionHelper = com.dynamicisland.app.util.TouchableRegionHelper(this)
    // Leash Paints
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

    private var floatBobYDp = 0f
    private var tiltAngleDeg = 0f
    private var leashSlackFactor = 1.0f

    private var driftAnimator: ValueAnimator? = null
    private var continuousFloatAnimator: ValueAnimator? = null
    private var snapBackAnimator: ValueAnimator? = null
    private var isDriftEngineRunning = false

    // Drag Gesture State
    private var isDraggingPet = false
    private var hasMoved = false
    private var downTouchX = 0f
    private var downTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Tap Interaction State
    private var tapCount = 0
    private var lastTapTimeMs = 0L
    private var isTemporarilyAngry = false
    private var savedMoodBeforeAnger = PixelPetView.PetMood.IDLE
    private var isManuallyAwoken = false

    // Autonomous drift scheduler (every 4 to 8 seconds)
    private val driftRunnable = object : Runnable {
        override fun run() {
            if (!isDriftEngineRunning || isDraggingPet) return
            pickNextFloatingSpot()
            scheduleNextDrift()
        }
    }

    private val angryResetRunnable = Runnable {
        if (isTemporarilyAngry) {
            isTemporarilyAngry = false
            tapCount = 0
            petView.setMood(savedMoodBeforeAnger)
            petView.triggerIdleActivity(PixelPetView.IdleActivity.BLUSHING, 1400L) // Cute embarrassed blush
            leashSlackFactor = 1.0f
            invalidate()
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
                if (!isDraggingPet) {
                    val p = anim.animatedValue as Float
                    val intensity = prefs.animationIntensity

                    floatBobYDp = sin(p) * (2.8f * intensity)
                    val baseTilt = (currentDriftXDp * 0.28f).coerceIn(-12f, 12f)
                    val dynamicSway = sin(p) * (2.0f * intensity)
                    tiltAngleDeg = baseTilt + dynamicSway

                    updatePetViewLayout()
                    invalidate()
                }
            }
        }
    }

    fun startDriftEngine() {
        if (isDriftEngineRunning) return
        isDriftEngineRunning = true

        if (continuousFloatAnimator?.isStarted != true) {
            continuousFloatAnimator?.start()
        }

        pickNextFloatingSpot()
        scheduleNextDrift()
    }

    fun stopDriftEngine() {
        isDriftEngineRunning = false
        handler.removeCallbacksAndMessages(null)
        driftAnimator?.cancel()
        snapBackAnimator?.cancel()
        continuousFloatAnimator?.cancel()
    }

    private fun scheduleNextDrift() {
        handler.removeCallbacks(driftRunnable)
        val nextDelayMs = Random.nextLong(4500L, 8500L) // 4.5s to 8.5s (never sits > 10s)
        handler.postDelayed(driftRunnable, nextDelayMs)
    }

    /**
     * Autonomous spot picker: floats to a new organic spot around the camera cutout
     */
    fun pickNextFloatingSpot() {
        if (isDraggingPet) return
        val leashLength = prefs.leashLengthDp.toFloat()

        // Spots around the camera:
        // 0: Left, 1: Right, 2: Underneath dangling, 3: Lean Left, 4: Lean Right, 5: Close Hover
        val spotPick = Random.nextInt(6)

        val (nextX, nextY) = when (spotPick) {
            0 -> Pair(-(leashLength * 0.75f), leashLength * 0.55f)
            1 -> Pair(leashLength * 0.75f, leashLength * 0.55f)
            2 -> Pair(0f, leashLength * 0.95f)
            3 -> Pair(-(leashLength * 0.55f), leashLength * 0.8f)
            4 -> Pair(leashLength * 0.55f, leashLength * 0.8f)
            else -> Pair(
                if (Random.nextBoolean()) -(leashLength * 0.4f) else (leashLength * 0.4f),
                leashLength * 0.4f
            )
        }

        driftTo(nextX, nextY)
    }

    fun driftTo(targetXDp: Float, targetYDp: Float, durationMs: Long = 1600L) {
        if (isDraggingPet) return
        val startX = currentDriftXDp
        val startY = currentDriftYDp

        driftAnimator?.cancel()
        driftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { anim ->
                if (!isDraggingPet) {
                    val f = anim.animatedFraction
                    currentDriftXDp = startX + ((targetXDp - startX) * f)
                    currentDriftYDp = startY + ((targetYDp - startY) * f)
                    leashSlackFactor = 1f + (sin(f * Math.PI.toFloat()) * 0.35f)
                    updatePetViewLayout()
                    invalidate()
                }
            }
        }
        driftAnimator?.start()
    }

    private fun performElasticSnapBack() {
        val startX = currentDriftXDp
        val startY = currentDriftYDp

        // Snap back to nearest comfortable hover spot
        val leashLength = prefs.leashLengthDp.toFloat()
        val targetX = if (startX < -8f) -(leashLength * 0.65f) else if (startX > 8f) (leashLength * 0.65f) else 0f
        val targetY = if (targetX == 0f) leashLength * 0.85f else leashLength * 0.55f

        snapBackAnimator?.cancel()
        snapBackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = OvershootInterpolator(2.2f) // Elastic spring-back bounce
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                currentDriftXDp = startX + ((targetX - startX) * f)
                currentDriftYDp = startY + ((targetY - startY) * f)
                // Spring wobble in leash slack
                leashSlackFactor = 0.5f + (sin(f * Math.PI.toFloat() * 3) * 0.5f * (1f - f))
                updatePetViewLayout()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    leashSlackFactor = 1.0f
                    scheduleNextDrift()
                }
            })
        }
        snapBackAnimator?.start()
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
        touchableRegionHelper.requestUpdate()
    }

    fun getPetTouchRegion(): android.graphics.Region {
        val petSizePx = prefs.petSizeDp.dpToPx().toFloat()
        val anchorXPx = width / 2f
        val anchorYPx = 8.dpToPx().toFloat()
        val petCenterXPx = anchorXPx + currentDriftXDp.dpToPx()
        val petCenterYPx = anchorYPx + (currentDriftYDp + floatBobYDp).dpToPx()

        // Non-rectangular circular shape matching the pet sprite
        val radius = (petSizePx / 2f) + 3.dpToPx()
        val path = Path().apply {
            addCircle(petCenterXPx, petCenterYPx, radius, Path.Direction.CW)
        }

        val clipRegion = android.graphics.Region(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        val region = android.graphics.Region()
        region.setPath(path, clipRegion)
        return region
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updatePetViewLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDriftEngine()
        touchableRegionHelper.attach { getPetTouchRegion() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDriftEngine()
        touchableRegionHelper.detach()
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

        // 2. Calculate Pet Attachment Point (Head Sprout at top of Pet)
        val petSizePx = prefs.petSizeDp.dpToPx().toFloat()
        val petCenterXPx = anchorX + currentDriftXDp.dpToPx()
        val petCenterYPx = anchorY + (currentDriftYDp + floatBobYDp).dpToPx()

        val petTopX = petCenterXPx
        val petTopY = petCenterYPx - (petSizePx * 0.42f)

        // 3. Draw Curved Physics String / Leash
        leashPath.reset()
        leashPath.moveTo(anchorX, anchorY + ringRadius)

        val midX = (anchorX + petTopX) / 2f
        val midY = (anchorY + petTopY) / 2f
        val dist = sqrt(((petTopX - anchorX) * (petTopX - anchorX)) + ((petTopY - anchorY) * (petTopY - anchorY)))

        // When dragging to max distance, leash is taut; when slack, sags with gravity
        val sagY = (dist * 0.18f * leashSlackFactor)
        val controlX = midX + (currentDriftXDp.dpToPx() * 0.12f)
        val controlY = midY + sagY

        leashPath.quadTo(controlX, controlY, petTopX, petTopY)
        canvas.drawPath(leashPath, leashPaint)

        // Tiny knot where leash ties to the sprout
        canvas.drawCircle(petTopX, petTopY, 2.dpToPx().toFloat(), anchorRingPaint)
    }

    /**
     * Touch dispatch: handles both dragging within leash radius AND tapping interactions
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!prefs.isTouchReactionEnabled) {
            return false // 100% click-through when touch reactions disabled
        }

        val anchorX = width / 2f
        val anchorY = 8.dpToPx().toFloat()
        val petSizePx = prefs.petSizeDp.dpToPx().toFloat()
        val petCenterXPx = anchorX + currentDriftXDp.dpToPx()
        val petCenterYPx = anchorY + (currentDriftYDp + floatBobYDp).dpToPx()

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = ev.x - petCenterXPx
                val dy = ev.y - petCenterYPx
                val dist = sqrt((dx * dx) + (dy * dy))
                val hitRadius = (petSizePx / 2f) + 6.dpToPx()

                // Hit detection matching the pet's visible circular shape
                if (dist <= hitRadius) {
                    isDraggingPet = true
                    hasMoved = false
                    downTouchX = ev.x
                    downTouchY = ev.y

                    // Pause drift while user holds pet
                    handler.removeCallbacks(driftRunnable)
                    driftAnimator?.cancel()
                    snapBackAnimator?.cancel()

                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false // Touches outside the pet pass through to underlying apps
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingPet) {
                    val moveDist = sqrt(
                        ((ev.x - downTouchX) * (ev.x - downTouchX)) +
                                ((ev.y - downTouchY) * (ev.y - downTouchY))
                    )
                    if (moveDist > touchSlop) {
                        hasMoved = true
                    }

                    // Vector from Camera Anchor to touch point
                    var vecX = ev.x - anchorX
                    var vecY = ev.y - anchorY

                    // Strict leash radius constraint (cannot exceed max leash radius)
                    val distFromAnchor = sqrt(vecX * vecX + vecY * vecY)
                    val maxLeashRadiusPx = (prefs.leashLengthDp * 1.15f).dpToPx().toFloat()

                    if (distFromAnchor > maxLeashRadiusPx && distFromAnchor > 0f) {
                        val clampScale = maxLeashRadiusPx / distFromAnchor
                        vecX *= clampScale
                        vecY *= clampScale
                    }

                    // Pet follows touch within leash radius
                    val density = context.resources.displayMetrics.density
                    currentDriftXDp = vecX / density
                    currentDriftYDp = (vecY - (floatBobYDp * density)) / density

                    // Dynamic tilt and tight leash tension during drag
                    tiltAngleDeg = (currentDriftXDp * 0.35f).coerceIn(-25f, 25f)
                    leashSlackFactor = 0.25f // Taut leash string!

                    updatePetViewLayout()
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingPet) {
                    isDraggingPet = false

                    if (!hasMoved) {
                        // User tapped the pet without dragging!
                        handlePetInteractionTap()
                        scheduleNextDrift()
                    } else {
                        // User released dragged pet -> Elastic spring-back!
                        performElasticSnapBack()
                    }
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    /**
     * Handles rich contextual tap interactions:
     * - Rapid multi-taps (3+ times): Explodes into angry tantrum with steam for a few seconds!
     * - Sleepy pet: Wakes up on tap; tapping again nods it back to sleep!
     * - Single tap: Contextual reactions (happy wink, wave, chuckle, nerd glasses push)
     */
    private fun handlePetInteractionTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTimeMs < 650L) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTimeMs = now

        // 1. REPEATED RAPID TAPS -> GET ANGRY FOR A FEW SECONDS!
        if (tapCount >= 3) {
            triggerTemperTantrum()
            return
        }

        // 2. SLEEP / WAKE TOGGLE
        if (petView.getMood() == PixelPetView.PetMood.SLEEPY) {
            // WAKE UP!
            isManuallyAwoken = true
            petView.setMood(PixelPetView.PetMood.HAPPY)
            petView.triggerIdleActivity(PixelPetView.IdleActivity.STARTLED, 700L)
            postDelayed({
                if (petView.getMood() == PixelPetView.PetMood.HAPPY) {
                    petView.triggerIdleActivity(PixelPetView.IdleActivity.YAWNING, 1400L)
                }
            }, 750L)
            return
        } else if (isManuallyAwoken) {
            // GO BACK TO SLEEP!
            isManuallyAwoken = false
            petView.triggerIdleActivity(PixelPetView.IdleActivity.YAWNING, 1200L)
            postDelayed({
                petView.setMood(PixelPetView.PetMood.SLEEPY)
            }, 1250L)
            return
        }

        // 3. CONTEXTUAL MOOD TAP
        petView.handleUserTap()

        // Playful leash jiggle
        leashSlackFactor = 1.6f
        postDelayed({ leashSlackFactor = 1.0f; invalidate() }, 300)
    }

    /**
     * Angry tantrum reaction when repeatedly tapped:
     * Turns furious red, vents steam, trembles with rage for 3.8 seconds, then blushes and calms down!
     */
    private fun triggerTemperTantrum() {
        handler.removeCallbacks(angryResetRunnable)
        if (!isTemporarilyAngry) {
            savedMoodBeforeAnger = petView.getMood()
            isTemporarilyAngry = true
        }

        petView.setMood(PixelPetView.PetMood.ANGRY)
        petView.triggerExcitedHop()

        // Angry leash vibration
        leashSlackFactor = 0.2f
        invalidate()

        // Calm down and return to normal after 3.8 seconds
        handler.postDelayed(angryResetRunnable, 3800L)
    }

    private fun Number.dpToPx(): Int =
        (this.toFloat() * context.resources.displayMetrics.density).toInt()

    private fun Float.dpToPx(): Float =
        this * context.resources.displayMetrics.density
}
