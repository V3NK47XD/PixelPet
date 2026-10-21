package com.dynamicisland.app.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.sin
import kotlin.random.Random
import com.dynamicisland.app.util.PreferencesHelper

/**
 * PixelPetView: Standalone, borderless, transparent animated pixel-art character.
 * Renders on a 28x28 pixel-perfect grid with rich life-like behaviors:
 * - Moods: IDLE, HAPPY, SAD, ANGRY, NERD, SLEEPY, CHARGING, LOW_BATTERY, CRITICAL_BATTERY
 * - Random Idle Activities: WAVING, SUSPICIOUS_LOOK, STARTLED, BLUSHING, GIGGLE, DIZZY, YAWNING, LIGHTBULB
 * - Dynamic Camera Movement: Visible walking waddle, hiding behind camera cutout, peeking out
 * - Phone Event Reactions: Phone unlock glow, charging mint sparkles, battery panic & faint
 */
class PixelPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class PetMood {
        IDLE,
        HAPPY,
        SAD,
        ANGRY,
        NERD,
        SLEEPY,
        CHARGING,
        LOW_BATTERY,
        CRITICAL_BATTERY,
        TEXTING
    }

    companion object {
        // Pre-computed flat span array: startCol, endCol, row (17 rows * 3 = 51 ints)
        // Eliminates 17 Triple instances, 1 array, and 51 boxed Integers per frame
        private val BODY_SPANS = intArrayOf(
            11, 16, 6,
            9, 18, 7,
            8, 19, 8,
            7, 20, 9,
            6, 21, 10,
            6, 21, 11,
            5, 22, 12,
            5, 22, 13,
            5, 22, 14,
            5, 22, 15,
            5, 22, 16,
            5, 22, 17,
            6, 21, 18,
            6, 21, 19,
            7, 20, 20,
            8, 19, 21,
            10, 17, 22
        )

        // Pre-parsed color constants avoiding runtime string parsing in onDraw()
        private val COLOR_SPROUT_GREEN = Color.parseColor("#4ADE80")
        private val COLOR_SPROUT_DARK = Color.parseColor("#15803D")
        private val COLOR_FEET = Color.parseColor("#F59E0B")
        private val COLOR_BLUSH_SAD = Color.parseColor("#93C5FD")
        private val COLOR_BLUSH_EMBARRASSED = Color.parseColor("#FF2D55")
        private val COLOR_DARK_YAWN_INSIDE = Color.parseColor("#4A0E17")
        private val COLOR_TONGUE_RED = Color.parseColor("#FF5252")
        private val COLOR_BUBBLE_OUTLINE = Color.parseColor("#0F172A")
        private val COLOR_BUBBLE_DOT = Color.parseColor("#64748B")
        private val COLOR_BUBBLE_DOT_ACTIVE = Color.parseColor("#0EA5E9")
        private val COLOR_SPARKLE_CHARGING = Color.parseColor("#34D399")
        private val COLOR_LIGHTBULB_BASE = Color.parseColor("#94A3B8")
    }

    enum class IdleActivity {
        NONE,
        WAVING,
        SUSPICIOUS_LOOK,
        STARTLED,
        BLUSHING,
        GIGGLE,
        DIZZY,
        YAWNING,
        LIGHTBULB
    }

    private var currentMood: PetMood = PetMood.IDLE
    private var currentActivity: IdleActivity = IdleActivity.NONE
    private var animationIntensity: Float = 1.0f
    private val prefs = PreferencesHelper(context)

    // Touch Debug Paints
    private val debugWindowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#10B981") // Green for interactive window bounds
    }

    private val debugHitboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#EC4899") // Magenta for pet hitbox
    }

    private val debugHitboxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#25EC4899") // Translucent pink
    }
    // Color definitions
    private val colorIdleBody = Color.parseColor("#FFD54F")
    private val colorIdleShadow = Color.parseColor("#FFA000")

    private val colorSadBody = Color.parseColor("#90CAF9")
    private val colorSadShadow = Color.parseColor("#64B5F6")

    private val colorAngryBody = Color.parseColor("#EF5350")
    private val colorAngryShadow = Color.parseColor("#C62828")

    private val colorNerdBody = Color.parseColor("#FFE082")
    private val colorNerdShadow = Color.parseColor("#FFB300")

    private val colorSleepyBody = Color.parseColor("#B39DDB")
    private val colorSleepyShadow = Color.parseColor("#7E57C2")

    private val colorChargingBody = Color.parseColor("#A7F3D0")
    private val colorChargingShadow = Color.parseColor("#34D399")

    private val colorLowBatteryBody = Color.parseColor("#FB923C")
    private val colorLowBatteryShadow = Color.parseColor("#EA580C")

    private val colorCriticalBatteryBody = Color.parseColor("#F87171")
    private val colorCriticalBatteryShadow = Color.parseColor("#DC2626")

    private val colorTextingBody = Color.parseColor("#2DD4BF") // Energetic cheerful teal
    private val colorTextingShadow = Color.parseColor("#0D9488")
    private val colorOutline = Color.parseColor("#261C14")
    private val colorEye = Color.parseColor("#1E1A16")
    private val colorEyeShine = Color.WHITE
    private val colorBlush = Color.parseColor("#FF6E6E")
    private val colorTear = Color.parseColor("#38BDF8")
    private val colorTearShine = Color.parseColor("#E0F2FE")
    private val colorSteam = Color.parseColor("#E2E8F0")
    private val colorGlasses = Color.parseColor("#0F172A")
    private val colorSparkle = Color.parseColor("#FDE047")
    private val colorZzz = Color.parseColor("#C4B5FD")
    private val colorLightbulb = Color.parseColor("#FBBF24")

    // Dynamic interpolated colors for smooth transition
    private var activeBodyColor = colorIdleBody
    private var activeShadowColor = colorIdleShadow
    private val argbEvaluator = ArgbEvaluator()

    // Paints
    private val pixelPaint = Paint().apply {
        isAntiAlias = false // True crisp pixel-art edges
        style = Paint.Style.FILL
    }

    // Animation state variables
    private var bobOffsetY = 0f
    private var scaleSquashX = 1.0f
    private var scaleSquashY = 1.0f
    private var sproutAngle = 0f

    // Eye state
    private var isBlinking = false
    private var eyeGazeX = 0f
    private var eyeGazeY = 0f

    // Walking & Positioning state
    var isWalking = false
        private set
    var facingDirection = 1 // 1 = right, -1 = left
        private set
    private var walkStepPhase = 0f

    // Camera Hide & Peek state
    var isHiding = false
        private set
    var isPeeking = false
        private set
    private var hideOffsetY = 0f

    // Mood & Activity specific states
    private var tearProgress = 0f
    private var rageTrembleX = 0f
    private var rageTrembleY = 0f
    private var steamProgress = 0f
    private var glassesDropY = 0f
    private var readingProgress = 0f
    private var happyHopY = 0f
    private var sparkleProgress = 0f
    private var sleepZProgress = 0f
    private var activityProgress = 0f
    private var faintTiltAngle = 0f
    private var typingDotCycle = 0
    private var typingHandPhase = 0f

    // Animators
    private var bobAnimator: ValueAnimator? = null
    private var blinkAnimator: ValueAnimator? = null
    private var tearAnimator: ValueAnimator? = null
    private var rageAnimator: ValueAnimator? = null
    private var glassesAnimator: ValueAnimator? = null
    private var readingAnimator: ValueAnimator? = null
    private var happyAnimator: ValueAnimator? = null
    private var walkAnimator: ValueAnimator? = null
    private var sleepyAnimator: ValueAnimator? = null
    private var typingAnimator: ValueAnimator? = null
    private var activityAnimator: ValueAnimator? = null
    private var colorTransitionAnimator: ValueAnimator? = null

    private val pixelRect = RectF()
    private val gridCols = 28
    private val gridRows = 28

    init {
        setupAnimators()
    }

    private fun setupAnimators() {
        // 1. Continuous breathing & gentle hovering bob
        bobAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                val intensity = animationIntensity

                if (!isWalking) {
                    bobOffsetY = sin(p) * (2.2f * intensity)
                    scaleSquashX = 1f + (sin(p) * 0.035f * intensity)
                    scaleSquashY = 1f - (sin(p) * 0.035f * intensity)
                    sproutAngle = sin(p) * (8f * intensity)
                }
                invalidate()
            }
        }

        // 2. Autonomous natural blinking & glancing cycle
        blinkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                isBlinking = (f in 0.40f..0.44f) || (f in 0.85f..0.89f)

                if (currentMood == PetMood.IDLE && currentActivity == IdleActivity.NONE) {
                    eyeGazeX = when {
                        f in 0.15f..0.35f -> -0.8f
                        f in 0.55f..0.75f -> 0.8f
                        else -> 0f
                    }
                }
            }
        }

        // 3. Sad mode: teardrop rolling animation
        tearAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                tearProgress = anim.animatedFraction
            }
        }

        // 4. Angry mode: intense vibration shake & steam puff cycle
        rageAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                steamProgress = anim.animatedFraction
                if (currentMood == PetMood.ANGRY) {
                    val shake = 1.4f * animationIntensity
                    rageTrembleX = (Random.nextFloat() * 2f - 1f) * shake
                    rageTrembleY = (Random.nextFloat() * 2f - 1f) * shake
                }
            }
        }

        // 5. Nerd mode: glasses dropping on face with elastic bounce
        glassesAnimator = ValueAnimator.ofFloat(-14f, 0f).apply {
            duration = 750
            interpolator = BounceInterpolator()
            addUpdateListener { anim ->
                glassesDropY = anim.animatedValue as Float
                invalidate()
            }
        }

        // 6. Nerd mode: reading left-to-right eye scanning
        readingAnimator = ValueAnimator.ofFloat(-1f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (currentMood == PetMood.NERD) {
                    readingProgress = anim.animatedValue as Float
                    eyeGazeX = readingProgress * 0.9f
                    eyeGazeY = 0.4f
                }
            }
        }

        // 7. Happy mode: bouncy celebratory hopping & sparkles
        happyAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = OvershootInterpolator(1.8f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                happyHopY = -sin(f * Math.PI.toFloat()) * (4.5f * animationIntensity)
                sparkleProgress = f
            }
        }

        // 8. Sleepy mode: floating Zzz particles & slow nodding
        sleepyAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                sleepZProgress = anim.animatedFraction
            }
        }

        // 9. Walking waddle step cycle
        walkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                walkStepPhase = anim.animatedFraction
                if (isWalking) {
                    val waddle = sin(walkStepPhase * Math.PI.toFloat() * 2) * 5f
                    sproutAngle = waddle * 1.5f
                    bobOffsetY = -sin(walkStepPhase * Math.PI.toFloat()) * 2f
                }
            }
        }

        // 10. Texting / Chatting mode: animated typing dots in speech bubble & tapping hands
        typingAnimator = ValueAnimator.ofFloat(0f, 3f).apply {
            duration = 850
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (currentMood == PetMood.TEXTING) {
                    val f = anim.animatedFraction
                    typingDotCycle = (f * 3).toInt() % 3
                    typingHandPhase = sin(f * Math.PI.toFloat() * 8)
                    eyeGazeX = sin(f * Math.PI.toFloat() * 2) * 0.4f
                    eyeGazeY = 0.55f // Looking down at conversation
                    invalidate()
                }
            }
        }
    }

    fun setMood(mood: PetMood) {
        if (currentMood == mood) return
        currentMood = mood

        val (targetBody, targetShadow) = when (mood) {
            PetMood.IDLE -> colorIdleBody to colorIdleShadow
            PetMood.HAPPY -> colorIdleBody to colorIdleShadow
            PetMood.SAD -> colorSadBody to colorSadShadow
            PetMood.ANGRY -> colorAngryBody to colorAngryShadow
            PetMood.NERD -> colorNerdBody to colorNerdShadow
            PetMood.SLEEPY -> colorSleepyBody to colorSleepyShadow
            PetMood.CHARGING -> colorChargingBody to colorChargingShadow
            PetMood.LOW_BATTERY -> colorLowBatteryBody to colorLowBatteryShadow
            PetMood.CRITICAL_BATTERY -> colorCriticalBatteryBody to colorCriticalBatteryShadow
            PetMood.TEXTING -> colorTextingBody to colorTextingShadow
        }

        colorTransitionAnimator?.cancel()
        val startBody = activeBodyColor
        val startShadow = activeShadowColor

        colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                activeBodyColor = argbEvaluator.evaluate(f, startBody, targetBody) as Int
                activeShadowColor = argbEvaluator.evaluate(f, startShadow, targetShadow) as Int
                invalidate()
            }
        }
        colorTransitionAnimator?.start()

        // Sync animators
        tearAnimator?.cancel()
        rageAnimator?.cancel()
        happyAnimator?.cancel()
        readingAnimator?.cancel()
        sleepyAnimator?.cancel()
        typingAnimator?.cancel()
        faintTiltAngle = 0f

        when (mood) {
            PetMood.SAD -> tearAnimator?.start()
            PetMood.ANGRY -> {
                rageAnimator?.start()
                triggerExcitedHop()
            }
            PetMood.NERD -> {
                readingAnimator?.start()
                glassesAnimator?.start()
            }
            PetMood.HAPPY -> happyAnimator?.start()
            PetMood.SLEEPY -> sleepyAnimator?.start()
            PetMood.CHARGING -> happyAnimator?.start() // Gentle energy bob
            PetMood.TEXTING -> typingAnimator?.start()
            PetMood.CRITICAL_BATTERY -> {
                // Dramatic faint tilt
                faintTiltAngle = 55f
                triggerExcitedHop()
            }
            PetMood.LOW_BATTERY -> {
                eyeGazeX = 0.8f
                eyeGazeY = -0.5f // Look at battery indicator
            }
            PetMood.IDLE -> {
                eyeGazeY = 0f
            }
        }
        invalidate()
    }

    fun getMood(): PetMood = currentMood

    fun setAnimationIntensity(intensity: Float) {
        this.animationIntensity = intensity.coerceIn(0.2f, 2.0f)
    }

    /**
     * Walking animation state: character waddles its feet and tilts body forward
     */
    fun setWalkingState(walking: Boolean, direction: Int = 1) {
        this.isWalking = walking
        this.facingDirection = if (direction >= 0) 1 else -1

        if (walking) {
            if (walkAnimator?.isStarted != true) walkAnimator?.start()
        } else {
            walkAnimator?.cancel()
            walkStepPhase = 0f
            bobOffsetY = 0f
        }
        invalidate()
    }

    /**
     * Camera hide and peek: slides behind camera cutout or peeks over edge
     */
    fun setHidingState(hiding: Boolean, peeking: Boolean = false) {
        this.isHiding = hiding
        this.isPeeking = peeking

        val targetHideY = when {
            hiding && peeking -> -12f // Just peeking top half / one eye
            hiding -> -28f           // Fully hidden behind cutout
            else -> 0f               // Fully visible
        }

        ValueAnimator.ofFloat(hideOffsetY, targetHideY).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                hideOffsetY = anim.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    /**
     * Triggers random life-like idle activity
     */
    fun triggerIdleActivity(activity: IdleActivity, durationMs: Long = 1800L) {
        if (currentActivity == activity) return
        activityAnimator?.cancel()
        currentActivity = activity

        activityAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { anim ->
                activityProgress = anim.animatedFraction
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentActivity = IdleActivity.NONE
                    activityProgress = 0f
                    invalidate()
                }
            })
        }
        activityAnimator?.start()
    }

    /**
     * Handles contextual user taps with rich personality reactions
     */
    fun handleUserTap() {
        when (currentMood) {
            PetMood.SLEEPY -> {
                // Startled wake-up followed by sleepy yawn
                triggerIdleActivity(IdleActivity.STARTLED, 700L)
                postDelayed({ triggerIdleActivity(IdleActivity.YAWNING, 1400L) }, 750L)
            }
            PetMood.SAD -> {
                // Surprised, then comforted small smile
                triggerIdleActivity(IdleActivity.STARTLED, 600L)
                postDelayed({ triggerIdleActivity(IdleActivity.BLUSHING, 1200L) }, 650L)
            }
            PetMood.ANGRY -> {
                // Anger burst, then settles
                triggerExcitedHop()
            }
            PetMood.NERD -> {
                // Push up glasses with glint shine
                glassesAnimator?.start()
                triggerIdleActivity(IdleActivity.LIGHTBULB, 1500L)
            }
            PetMood.CRITICAL_BATTERY, PetMood.LOW_BATTERY -> {
                // Weak nervous shiver
                triggerIdleActivity(IdleActivity.DIZZY, 1200L)
            }
            else -> {
                // Random fun reaction
                val randomPick = Random.nextInt(4)
                when (randomPick) {
                    0 -> triggerIdleActivity(IdleActivity.WAVING, 1600L)
                    1 -> triggerIdleActivity(IdleActivity.GIGGLE, 1400L)
                    2 -> triggerIdleActivity(IdleActivity.BLUSHING, 1500L)
                    else -> triggerExcitedHop()
                }
            }
        }
    }

    /**
     * Playful squish & bounce
     */
    fun triggerExcitedHop() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator(2.5f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                scaleSquashY = 1f + (sin(f * Math.PI.toFloat()) * 0.22f)
                scaleSquashX = 1f - (sin(f * Math.PI.toFloat()) * 0.15f)
                invalidate()
            }
        }.start()
    }
    fun pauseAnimations() {
        bobAnimator?.pause()
        blinkAnimator?.pause()
        tearAnimator?.pause()
        rageAnimator?.pause()
        glassesAnimator?.pause()
        readingAnimator?.pause()
        happyAnimator?.pause()
        walkAnimator?.pause()
        sleepyAnimator?.pause()
        typingAnimator?.pause()
        activityAnimator?.pause()
        colorTransitionAnimator?.pause()
    }

    fun resumeAnimations() {
        bobAnimator?.resume()
        blinkAnimator?.resume()
        when (currentMood) {
            PetMood.SAD -> tearAnimator?.resume()
            PetMood.ANGRY -> rageAnimator?.resume()
            PetMood.NERD -> readingAnimator?.resume()
            PetMood.HAPPY -> happyAnimator?.resume()
            PetMood.SLEEPY -> sleepyAnimator?.resume()
            PetMood.TEXTING -> typingAnimator?.resume()
            else -> {}
        }
        if (isWalking) walkAnimator?.resume()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bobAnimator?.isStarted != true) bobAnimator?.start()
        if (blinkAnimator?.isStarted != true) blinkAnimator?.start()
        when (currentMood) {
            PetMood.SAD -> tearAnimator?.start()
            PetMood.ANGRY -> rageAnimator?.start()
            PetMood.NERD -> readingAnimator?.start()
            PetMood.HAPPY -> happyAnimator?.start()
            PetMood.SLEEPY -> sleepyAnimator?.start()
            else -> {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bobAnimator?.cancel()
        blinkAnimator?.cancel()
        tearAnimator?.cancel()
        rageAnimator?.cancel()
        glassesAnimator?.cancel()
        readingAnimator?.cancel()
        happyAnimator?.cancel()
        walkAnimator?.cancel()
        sleepyAnimator?.cancel()
        typingAnimator?.cancel()
        activityAnimator?.cancel()
        colorTransitionAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pixelSize = minOf(w, h) / gridCols.toFloat()
        val offsetX = (w - (gridCols * pixelSize)) / 2f
        val offsetY = (h - (gridRows * pixelSize)) / 2f

        canvas.save()

        // Apply translations
        val totalTranslateY = offsetY + (bobOffsetY * pixelSize * 0.5f) + (happyHopY * pixelSize * 0.5f) +
                rageTrembleY + (hideOffsetY * pixelSize)
        val totalTranslateX = offsetX + rageTrembleX

        canvas.translate(totalTranslateX, totalTranslateY)

        val pivotX = 14f * pixelSize
        val pivotY = 16f * pixelSize

        // Handle horizontal facing flip (when walking left vs right)
        if (facingDirection == -1) {
            canvas.scale(-1f, 1f, pivotX, pivotY)
        }

        // Apply fainting / collapse tilt for critical battery
        if (faintTiltAngle != 0f) {
            canvas.rotate(faintTiltAngle, pivotX, pivotY)
        }

        // Apply squash & stretch
        canvas.scale(scaleSquashX, scaleSquashY, pivotX, pivotY)

        // 1. Sprout Feather on Head
        drawSprout(canvas, pixelSize, pivotX)

        // 2. Main Pet Body
        drawBody(canvas, pixelSize)

        // 3. Cheeks
        drawCheeks(canvas, pixelSize)

        // 4. Eyes & Eyebrows
        drawEyesAndBrows(canvas, pixelSize)

        // 5. Mouth
        drawMouth(canvas, pixelSize)

        // 6. Arms / Waving
        drawArms(canvas, pixelSize)

        // 7. Accessories & Particle Effects
        when {
            currentMood == PetMood.NERD -> drawGlasses(canvas, pixelSize)
            currentMood == PetMood.SAD -> drawTears(canvas, pixelSize)
            currentMood == PetMood.ANGRY -> drawSteamPuffs(canvas, pixelSize)
            currentMood == PetMood.HAPPY || currentMood == PetMood.CHARGING -> drawSparkles(canvas, pixelSize)
            currentMood == PetMood.SLEEPY -> drawZzz(canvas, pixelSize)
            currentMood == PetMood.TEXTING -> drawSpeechBubble(canvas, pixelSize)
        }

        if (currentActivity == IdleActivity.LIGHTBULB) {
            drawLightbulb(canvas, pixelSize)
        }

        canvas.restore()

        // 8. Touch Debug Visualization
        if (prefs.isTouchDebugEnabled) {
            drawTouchDebugOutlines(canvas, w, h)
        }
    }

    private fun drawTouchDebugOutlines(canvas: Canvas, w: Float, h: Float) {
        // Overlay/window bounds (green outline)
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, debugWindowPaint)

        // Pet hitbox (magenta circle matching exact hit-test region)
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) * 0.90f
        canvas.drawCircle(cx, cy, radius, debugHitboxFill)
        canvas.drawCircle(cx, cy, radius, debugHitboxPaint)
    }

    fun isTouchInsideHitbox(touchX: Float, touchY: Float): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.90f
        val dx = touchX - cx
        val dy = touchY - cy
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    private fun drawPixel(canvas: Canvas, col: Int, row: Int, color: Int, pixelSize: Float) {
        pixelPaint.color = color
        pixelRect.set(
            col * pixelSize,
            row * pixelSize,
            (col + 1) * pixelSize,
            (row + 1) * pixelSize
        )
        canvas.drawRect(pixelRect, pixelPaint)
    }

    private fun drawPixelSpan(canvas: Canvas, startCol: Int, endCol: Int, row: Int, color: Int, pixelSize: Float) {
        pixelPaint.color = color
        pixelRect.set(
            startCol * pixelSize,
            row * pixelSize,
            (endCol + 1) * pixelSize,
            (row + 1) * pixelSize
        )
        canvas.drawRect(pixelRect, pixelPaint)
    }

    private fun drawSprout(canvas: Canvas, pixelSize: Float, pivotX: Float) {
        canvas.save()
        canvas.rotate(sproutAngle, pivotX, 6f * pixelSize)

        // Stem
        drawPixel(canvas, 13, 5, COLOR_SPROUT_DARK, pixelSize)
        drawPixel(canvas, 14, 5, COLOR_SPROUT_GREEN, pixelSize)
        drawPixel(canvas, 13, 4, COLOR_SPROUT_DARK, pixelSize)
        drawPixel(canvas, 14, 4, COLOR_SPROUT_GREEN, pixelSize)

        // Left leaf
        drawPixel(canvas, 11, 3, COLOR_SPROUT_DARK, pixelSize)
        drawPixel(canvas, 12, 3, COLOR_SPROUT_GREEN, pixelSize)
        drawPixel(canvas, 10, 2, COLOR_SPROUT_DARK, pixelSize)
        drawPixel(canvas, 11, 2, COLOR_SPROUT_GREEN, pixelSize)

        // Right leaf
        drawPixel(canvas, 15, 3, COLOR_SPROUT_GREEN, pixelSize)
        drawPixel(canvas, 16, 3, COLOR_SPROUT_DARK, pixelSize)
        drawPixel(canvas, 16, 2, COLOR_SPROUT_GREEN, pixelSize)
        drawPixel(canvas, 17, 2, COLOR_SPROUT_DARK, pixelSize)

        canvas.restore()
    }

    private fun drawBody(canvas: Canvas, pixelSize: Float) {
        var idx = 0
        while (idx < BODY_SPANS.size) {
            val start = BODY_SPANS[idx]
            val end = BODY_SPANS[idx + 1]
            val row = BODY_SPANS[idx + 2]
            idx += 3

            drawPixel(canvas, start, row, colorOutline, pixelSize)
            drawPixel(canvas, end, row, colorOutline, pixelSize)

            if (end - start > 1) {
                val fillStart = start + 1
                val fillEnd = end - 1
                val isShadedRow = row >= 18
                val rowColor = if (isShadedRow) activeShadowColor else activeBodyColor
                drawPixelSpan(canvas, fillStart, fillEnd, row, rowColor, pixelSize)

                if (row in 12..17) {
                    drawPixel(canvas, fillEnd, row, activeShadowColor, pixelSize)
                }
            }
        }

        drawPixelSpan(canvas, 11, 16, 5, colorOutline, pixelSize)
        drawPixelSpan(canvas, 10, 17, 23, colorOutline, pixelSize)

        // Feet with stepping animation during walking
        var leftFootRow = 23
        var rightFootRow = 23

        if (isWalking) {
            if (walkStepPhase < 0.5f) {
                leftFootRow = 22
            } else {
                rightFootRow = 22
            }
        }

        drawPixelSpan(canvas, 9, 11, leftFootRow, COLOR_FEET, pixelSize)
        drawPixelSpan(canvas, 16, 18, rightFootRow, COLOR_FEET, pixelSize)
        drawPixelSpan(canvas, 9, 11, leftFootRow + 1, colorOutline, pixelSize)
        drawPixelSpan(canvas, 16, 18, rightFootRow + 1, colorOutline, pixelSize)
    }

    private fun drawCheeks(canvas: Canvas, pixelSize: Float) {
        val blush = when {
            currentMood == PetMood.SAD -> COLOR_BLUSH_SAD
            currentActivity == IdleActivity.BLUSHING -> COLOR_BLUSH_EMBARRASSED
            else -> colorBlush
        }
        drawPixelSpan(canvas, 7, 8, 15, blush, pixelSize)
        drawPixelSpan(canvas, 19, 20, 15, blush, pixelSize)
    }



    private fun drawEyesAndBrows(canvas: Canvas, pixelSize: Float) {
        val gazePixelX = (eyeGazeX * 1.2f).toInt()
        val gazePixelY = (eyeGazeY * 1.0f).toInt()

        when {
            currentMood == PetMood.CRITICAL_BATTERY || currentActivity == IdleActivity.DIZZY -> {
                // Dizzy X X eyes
                drawPixel(canvas, 9, 11, colorEye, pixelSize)
                drawPixel(canvas, 11, 11, colorEye, pixelSize)
                drawPixel(canvas, 10, 12, colorEye, pixelSize)
                drawPixel(canvas, 9, 13, colorEye, pixelSize)
                drawPixel(canvas, 11, 13, colorEye, pixelSize)

                drawPixel(canvas, 16, 11, colorEye, pixelSize)
                drawPixel(canvas, 18, 11, colorEye, pixelSize)
                drawPixel(canvas, 17, 12, colorEye, pixelSize)
                drawPixel(canvas, 16, 13, colorEye, pixelSize)
                drawPixel(canvas, 18, 13, colorEye, pixelSize)
            }
            currentMood == PetMood.SAD -> {
                // Sad droopy eyebrows (\ /)
                drawPixelSpan(canvas, 9, 11, 10, colorOutline, pixelSize)
                drawPixel(canvas, 12, 9, colorOutline, pixelSize)
                drawPixel(canvas, 15, 9, colorOutline, pixelSize)
                drawPixelSpan(canvas, 16, 18, 10, colorOutline, pixelSize)

                // Drooping sad eyes
                drawPixelSpan(canvas, 9, 11, 12, colorEye, pixelSize)
                drawPixelSpan(canvas, 9, 11, 13, colorEye, pixelSize)
                drawPixelSpan(canvas, 16, 18, 12, colorEye, pixelSize)
                drawPixelSpan(canvas, 16, 18, 13, colorEye, pixelSize)

                drawPixel(canvas, 9, 12, colorTearShine, pixelSize)
                drawPixel(canvas, 16, 12, colorTearShine, pixelSize)
            }
            currentMood == PetMood.ANGRY -> {
                // Fierce V-shaped eyebrows (/ \)
                drawPixel(canvas, 9, 9, colorOutline, pixelSize)
                drawPixel(canvas, 10, 10, colorOutline, pixelSize)
                drawPixelSpan(canvas, 11, 12, 11, colorOutline, pixelSize)
                drawPixelSpan(canvas, 15, 16, 11, colorOutline, pixelSize)
                drawPixel(canvas, 17, 10, colorOutline, pixelSize)
                drawPixel(canvas, 18, 9, colorOutline, pixelSize)

                drawPixelSpan(canvas, 10, 12, 12, colorEye, pixelSize)
                drawPixel(canvas, 11, 13, colorEye, pixelSize)
                drawPixelSpan(canvas, 15, 17, 12, colorEye, pixelSize)
                drawPixel(canvas, 16, 13, colorEye, pixelSize)
            }
            currentMood == PetMood.HAPPY || currentMood == PetMood.CHARGING -> {
                // Joyful happy crinkle arcs (^ ^)
                drawPixelSpan(canvas, 9, 11, 11, colorEye, pixelSize)
                drawPixel(canvas, 8, 12, colorEye, pixelSize)
                drawPixel(canvas, 12, 12, colorEye, pixelSize)

                drawPixelSpan(canvas, 16, 18, 11, colorEye, pixelSize)
                drawPixel(canvas, 15, 12, colorEye, pixelSize)
                drawPixel(canvas, 19, 12, colorEye, pixelSize)
            }
            currentMood == PetMood.SLEEPY -> {
                // Sleepy half-closed droopy horizontal slits
                drawPixelSpan(canvas, 9, 11, 13, colorEye, pixelSize)
                drawPixelSpan(canvas, 16, 18, 13, colorEye, pixelSize)
            }
            currentActivity == IdleActivity.STARTLED || currentMood == PetMood.LOW_BATTERY -> {
                // Startled / worried huge circular 3x3 eyes
                drawPixelSpan(canvas, 9, 11, 11, colorEye, pixelSize)
                drawPixelSpan(canvas, 9, 11, 13, colorEye, pixelSize)
                drawPixel(canvas, 8, 12, colorEye, pixelSize)
                drawPixel(canvas, 12, 12, colorEye, pixelSize)
                drawPixel(canvas, 10, 12, colorEyeShine, pixelSize)

                drawPixelSpan(canvas, 16, 18, 11, colorEye, pixelSize)
                drawPixelSpan(canvas, 16, 18, 13, colorEye, pixelSize)
                drawPixel(canvas, 15, 12, colorEye, pixelSize)
                drawPixel(canvas, 19, 12, colorEye, pixelSize)
                drawPixel(canvas, 17, 12, colorEyeShine, pixelSize)
            }
            else -> {
                if (isBlinking) {
                    drawPixelSpan(canvas, 9, 11, 12, colorEye, pixelSize)
                    drawPixelSpan(canvas, 16, 18, 12, colorEye, pixelSize)
                } else {
                    val leftCol = 9 + gazePixelX
                    val rightCol = 16 + gazePixelX
                    val row = 12 + gazePixelY

                    drawPixelSpan(canvas, leftCol, leftCol + 2, row, colorEye, pixelSize)
                    drawPixelSpan(canvas, leftCol, leftCol + 2, row + 1, colorEye, pixelSize)
                    drawPixel(canvas, leftCol, row, colorEyeShine, pixelSize)

                    drawPixelSpan(canvas, rightCol, rightCol + 2, row, colorEye, pixelSize)
                    drawPixelSpan(canvas, rightCol, rightCol + 2, row + 1, colorEye, pixelSize)
                    drawPixel(canvas, rightCol, row, colorEyeShine, pixelSize)
                }
            }
        }
    }

    private fun drawMouth(canvas: Canvas, pixelSize: Float) {
        when {
            currentMood == PetMood.TEXTING -> {
                // Cheerful open chatting mouth :3
                drawPixelSpan(canvas, 12, 15, 15, colorOutline, pixelSize)
                drawPixel(canvas, 11, 15, colorOutline, pixelSize)
                drawPixel(canvas, 16, 15, colorOutline, pixelSize)
                drawPixelSpan(canvas, 13, 14, 16, colorOutline, pixelSize)
            }
            currentActivity == IdleActivity.YAWNING -> {
                // Wide open yawn 'O'
                drawPixelSpan(canvas, 12, 15, 14, colorOutline, pixelSize)
                drawPixelSpan(canvas, 12, 15, 17, colorOutline, pixelSize)
                drawPixel(canvas, 11, 15, colorOutline, pixelSize)
                drawPixel(canvas, 11, 16, colorOutline, pixelSize)
                drawPixel(canvas, 16, 15, colorOutline, pixelSize)
                drawPixel(canvas, 16, 16, colorOutline, pixelSize)
                // Dark yawn inside
                drawPixelSpan(canvas, 12, 15, 15, COLOR_DARK_YAWN_INSIDE, pixelSize)
                drawPixelSpan(canvas, 12, 15, 16, COLOR_DARK_YAWN_INSIDE, pixelSize)
            }
            currentMood == PetMood.SAD -> {
                drawPixelSpan(canvas, 13, 14, 15, colorOutline, pixelSize)
                drawPixel(canvas, 12, 16, colorOutline, pixelSize)
                drawPixel(canvas, 15, 16, colorOutline, pixelSize)
            }
            currentMood == PetMood.ANGRY -> {
                drawPixelSpan(canvas, 12, 15, 15, colorOutline, pixelSize)
                drawPixel(canvas, 12, 16, colorOutline, pixelSize)
                drawPixel(canvas, 15, 16, colorOutline, pixelSize)
            }
            currentMood == PetMood.HAPPY || currentActivity == IdleActivity.GIGGLE -> {
                drawPixelSpan(canvas, 12, 15, 14, colorOutline, pixelSize)
                drawPixel(canvas, 11, 15, colorOutline, pixelSize)
                drawPixel(canvas, 16, 15, colorOutline, pixelSize)
                drawPixelSpan(canvas, 12, 15, 16, colorOutline, pixelSize)
                drawPixelSpan(canvas, 12, 15, 15, COLOR_TONGUE_RED, pixelSize)
            }
            currentMood == PetMood.LOW_BATTERY || currentMood == PetMood.CRITICAL_BATTERY -> {
                // Worried nervous wavy line
                drawPixel(canvas, 12, 16, colorOutline, pixelSize)
                drawPixel(canvas, 13, 15, colorOutline, pixelSize)
                drawPixel(canvas, 14, 16, colorOutline, pixelSize)
                drawPixel(canvas, 15, 15, colorOutline, pixelSize)
            }
            currentMood == PetMood.SLEEPY -> {
                // Cute tiny sleepy 'o' mouth
                drawPixelSpan(canvas, 13, 14, 15, colorOutline, pixelSize)
                drawPixelSpan(canvas, 13, 14, 16, colorOutline, pixelSize)
            }
            else -> {
                drawPixelSpan(canvas, 13, 14, 15, colorOutline, pixelSize)
                drawPixel(canvas, 12, 14, colorOutline, pixelSize)
                drawPixel(canvas, 15, 14, colorOutline, pixelSize)
            }
        }
    }

    private fun drawArms(canvas: Canvas, pixelSize: Float) {
        if (currentActivity == IdleActivity.WAVING) {
            // Waving arm raised up (cols 3-6, row 10-13)
            val waveWave = (sin(activityProgress * Math.PI.toFloat() * 8) * 1.5f).toInt()
            val armColor = activeBodyColor

            drawPixel(canvas, 4, 13, colorOutline, pixelSize)
            drawPixel(canvas, 4, 12, armColor, pixelSize)
            drawPixel(canvas, 3 + waveWave, 11, armColor, pixelSize)
            drawPixel(canvas, 3 + waveWave, 10, colorOutline, pixelSize)
            drawPixel(canvas, 2 + waveWave, 10, armColor, pixelSize)
        } else if (currentMood == PetMood.TEXTING) {
            // Rapid tiny typing hands tapping at front
            val leftHandY = if (typingDotCycle == 0) 16 else 17
            val rightHandY = if (typingDotCycle == 1) 16 else 17
            val handColor = activeBodyColor

            drawPixelSpan(canvas, 10, 11, leftHandY, handColor, pixelSize)
            drawPixelSpan(canvas, 10, 11, leftHandY + 1, colorOutline, pixelSize)
            drawPixelSpan(canvas, 16, 17, rightHandY, handColor, pixelSize)
            drawPixelSpan(canvas, 16, 17, rightHandY + 1, colorOutline, pixelSize)
        }
    }

    private fun drawSpeechBubble(canvas: Canvas, pixelSize: Float) {
        // Using pre-computed color constants COLOR_BUBBLE_OUTLINE, COLOR_BUBBLE_DOT, COLOR_BUBBLE_DOT_ACTIVE
        // Speech bubble at cols 19-27, rows 1-6
        drawPixelSpan(canvas, 21, 26, 1, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 20, 2, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 27, 2, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixelSpan(canvas, 21, 26, 2, Color.WHITE, pixelSize)

        drawPixel(canvas, 19, 3, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 27, 3, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixelSpan(canvas, 20, 26, 3, Color.WHITE, pixelSize)
        drawPixel(canvas, 19, 4, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 27, 4, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixelSpan(canvas, 20, 26, 4, Color.WHITE, pixelSize)
        drawPixel(canvas, 20, 5, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 26, 5, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixelSpan(canvas, 21, 25, 5, Color.WHITE, pixelSize)

        drawPixelSpan(canvas, 21, 25, 6, COLOR_BUBBLE_OUTLINE, pixelSize)

        // Tail pointing down to mouth
        drawPixel(canvas, 19, 6, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 18, 7, COLOR_BUBBLE_OUTLINE, pixelSize)
        drawPixel(canvas, 19, 7, COLOR_BUBBLE_OUTLINE, pixelSize)

        // 3 Typing Dots cycling in animation
        val dot1Y = if (typingDotCycle == 0) 3 else 4
        val dot2Y = if (typingDotCycle == 1) 3 else 4
        val dot3Y = if (typingDotCycle == 2) 3 else 4

        drawPixel(canvas, 21, dot1Y, if (typingDotCycle == 0) COLOR_BUBBLE_DOT_ACTIVE else COLOR_BUBBLE_DOT, pixelSize)
        drawPixel(canvas, 23, dot2Y, if (typingDotCycle == 1) COLOR_BUBBLE_DOT_ACTIVE else COLOR_BUBBLE_DOT, pixelSize)
        drawPixel(canvas, 25, dot3Y, if (typingDotCycle == 2) COLOR_BUBBLE_DOT_ACTIVE else COLOR_BUBBLE_DOT, pixelSize)
    }

    private fun drawGlasses(canvas: Canvas, pixelSize: Float) {
        val yOffset = glassesDropY * pixelSize

        canvas.save()
        canvas.translate(0f, yOffset)

        drawPixelSpan(canvas, 8, 12, 10, colorGlasses, pixelSize)
        drawPixelSpan(canvas, 8, 12, 14, colorGlasses, pixelSize)
        drawPixel(canvas, 7, 11, colorGlasses, pixelSize)
        drawPixel(canvas, 7, 12, colorGlasses, pixelSize)
        drawPixel(canvas, 7, 13, colorGlasses, pixelSize)
        drawPixel(canvas, 13, 11, colorGlasses, pixelSize)
        drawPixel(canvas, 13, 12, colorGlasses, pixelSize)
        drawPixel(canvas, 13, 13, colorGlasses, pixelSize)

        drawPixelSpan(canvas, 15, 19, 10, colorGlasses, pixelSize)
        drawPixelSpan(canvas, 15, 19, 14, colorGlasses, pixelSize)
        drawPixel(canvas, 14, 11, colorGlasses, pixelSize)
        drawPixel(canvas, 14, 12, colorGlasses, pixelSize)
        drawPixel(canvas, 14, 13, colorGlasses, pixelSize)
        drawPixel(canvas, 20, 11, colorGlasses, pixelSize)
        drawPixel(canvas, 20, 12, colorGlasses, pixelSize)
        drawPixel(canvas, 20, 13, colorGlasses, pixelSize)

        drawPixelSpan(canvas, 13, 14, 12, colorGlasses, pixelSize)

        drawPixel(canvas, 9, 11, Color.WHITE, pixelSize)
        drawPixel(canvas, 10, 12, Color.WHITE, pixelSize)
        drawPixel(canvas, 16, 11, Color.WHITE, pixelSize)
        drawPixel(canvas, 17, 12, Color.WHITE, pixelSize)

        canvas.restore()
    }

    private fun drawTears(canvas: Canvas, pixelSize: Float) {
        val tearY = 13 + (tearProgress * 7f).toInt()
        val tearCol = 19

        drawPixel(canvas, tearCol, tearY, colorTear, pixelSize)
        drawPixel(canvas, tearCol, tearY + 1, colorTear, pixelSize)
        drawPixel(canvas, tearCol - 1, tearY + 1, colorTear, pixelSize)
        drawPixel(canvas, tearCol, tearY + 2, colorTearShine, pixelSize)

        if (tearProgress > 0.4f) {
            val leftTearY = 14 + ((tearProgress - 0.4f) * 6f).toInt()
            drawPixel(canvas, 8, leftTearY, colorTear, pixelSize)
            drawPixel(canvas, 8, leftTearY + 1, colorTearShine, pixelSize)
        }
    }

    private fun drawSteamPuffs(canvas: Canvas, pixelSize: Float) {
        val puffY = (8 - (steamProgress * 5f)).toInt()
        val alpha = ((1f - steamProgress) * 255).toInt().coerceIn(0, 255)
        pixelPaint.alpha = alpha

        val leftX = (6 - (steamProgress * 2f)).toInt()
        drawPixel(canvas, leftX, puffY, colorSteam, pixelSize)
        drawPixel(canvas, leftX + 1, puffY - 1, colorSteam, pixelSize)
        drawPixel(canvas, leftX, puffY - 1, colorSteam, pixelSize)

        val rightX = (21 + (steamProgress * 2f)).toInt()
        drawPixel(canvas, rightX, puffY, colorSteam, pixelSize)
        drawPixel(canvas, rightX - 1, puffY - 1, colorSteam, pixelSize)
        drawPixel(canvas, rightX, puffY - 1, colorSteam, pixelSize)

        pixelPaint.alpha = 255
    }

    private fun drawSparkles(canvas: Canvas, pixelSize: Float) {
        val sparkleColor = if (currentMood == PetMood.CHARGING) COLOR_SPARKLE_CHARGING else colorSparkle
        val sparkleY = (6 - (sparkleProgress * 3f)).toInt()

        drawPixel(canvas, 5, sparkleY, sparkleColor, pixelSize)
        drawPixel(canvas, 4, sparkleY + 1, sparkleColor, pixelSize)
        drawPixel(canvas, 6, sparkleY + 1, sparkleColor, pixelSize)
        drawPixel(canvas, 5, sparkleY + 2, sparkleColor, pixelSize)

        drawPixel(canvas, 22, sparkleY + 2, sparkleColor, pixelSize)
        drawPixel(canvas, 21, sparkleY + 3, sparkleColor, pixelSize)
        drawPixel(canvas, 23, sparkleY + 3, sparkleColor, pixelSize)
        drawPixel(canvas, 22, sparkleY + 4, sparkleColor, pixelSize)
    }

    private fun drawZzz(canvas: Canvas, pixelSize: Float) {
        val zY = (7 - (sleepZProgress * 5f)).toInt()
        val alpha = ((1f - sleepZProgress) * 255).toInt().coerceIn(0, 255)
        pixelPaint.alpha = alpha

        // Big Z
        drawPixelSpan(canvas, 21, 24, zY, colorZzz, pixelSize)
        drawPixel(canvas, 23, zY + 1, colorZzz, pixelSize)
        drawPixel(canvas, 22, zY + 2, colorZzz, pixelSize)
        drawPixelSpan(canvas, 21, 24, zY + 3, colorZzz, pixelSize)

        // Small z
        if (sleepZProgress > 0.3f) {
            val smallZY = (11 - (sleepZProgress * 4f)).toInt()
            drawPixelSpan(canvas, 24, 26, smallZY, colorZzz, pixelSize)
            drawPixel(canvas, 25, smallZY + 1, colorZzz, pixelSize)
            drawPixelSpan(canvas, 24, 26, smallZY + 2, colorZzz, pixelSize)
        }

        pixelPaint.alpha = 255
    }

    private fun drawLightbulb(canvas: Canvas, pixelSize: Float) {
        // Pixel lightbulb over head
        drawPixelSpan(canvas, 13, 15, 0, colorLightbulb, pixelSize)
        drawPixelSpan(canvas, 12, 16, 1, colorLightbulb, pixelSize)
        drawPixelSpan(canvas, 13, 15, 2, colorLightbulb, pixelSize)
        drawPixelSpan(canvas, 13, 15, 3, COLOR_LIGHTBULB_BASE, pixelSize) // base
    }
}
