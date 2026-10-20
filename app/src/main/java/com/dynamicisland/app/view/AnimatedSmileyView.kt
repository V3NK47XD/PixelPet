package com.dynamicisland.app.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.sin

/**
 * Custom View rendering an animated, floating, looping smiley face.
 * Features:
 * - Weightless floating bobbing motion (sine-wave oscillation)
 * - Autonomous looping behavior: blinking, looking around, winking, smiling
 * - Multiple expressive moods (Happy, Wink, Excited, Cool, Love)
 * - Interactive tap reaction (bouncy excited grin)
 */
class AnimatedSmileyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mood {
        LOOP,
        HAPPY,
        WINK,
        EXCITED,
        COOL,
        LOVE,
        SAD
    }

    private var currentMood: Mood = Mood.LOOP

    // Paints
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD43B") // Vibrant golden yellow
        style = Paint.Style.FILL
    }

    private val faceOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59F00")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1E24")
        style = Paint.Style.FILL
    }

    private val eyeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B6B")
        style = Paint.Style.FILL
        alpha = 110
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1E24")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val mouthFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1E24")
        style = Paint.Style.FILL
    }

    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B81")
        style = Paint.Style.FILL
    }

    private val glassesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        style = Paint.Style.FILL
    }

    private val glassesShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 160
    }

    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.FILL
    }

    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Teardrop cyan
        style = Paint.Style.FILL
    }

    private val sadEyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1E24")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    // Animation state values
    private var floatOffsetY = 0f
    private var tiltAngle = 0f
    private var eyeScaleY = 1.0f
    private var eyeLookOffsetX = 0f
    private var isRightEyeWinking = false
    private var mouthOpenFactor = 0f // 0f = closed line smile, 1f = wide open laugh
    private var mouthCurve = 1.0f
    private var blushScale = 1.0f
    private var bounceScale = 1.0f

    // Animators
    private var floatAnimator: ValueAnimator? = null
    private var loopSequenceAnimator: ValueAnimator? = null
    private var reactionAnimator: ValueAnimator? = null
    private var tearAnimator: ValueAnimator? = null

    private var tearProgress = 0f
    private var mouthQuiver = 0f
    // Loop state machine
    private var loopStep = 0

    init {
        setupAnimators()
    }

    private fun setupAnimators() {
        // Continuous smooth floating bobbing motion
        floatAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                floatOffsetY = (sin(progress) * 4.5f) // Smooth float up/down ~4.5dp
                tiltAngle = (sin(progress) * 2.0f) // Subtle sway ~2 deg
                invalidate()
            }
        }

        // Teardrop trickling animation for sad mood
        tearAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (currentMood == Mood.SAD) {
                    tearProgress = anim.animatedFraction
                    mouthQuiver = (sin(anim.animatedFraction * Math.PI.toFloat() * 6) * 0.8f)
                    invalidate()
                }
            }
        }

        startLoopSequence()
    }

    /**
     * Autonomous sequence manager: continuously loops through playful moods and micro-actions.
     */
    private fun startLoopSequence() {
        loopSequenceAnimator?.cancel()

        // Sequence runs a cycle of 8000ms repeating indefinitely
        loopSequenceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 8400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                if (currentMood != Mood.LOOP) return@addUpdateListener

                val p = anim.animatedFraction // 0.0 to 1.0

                when {
                    // Phase 1 (0.00 - 0.25): Idle Happy Floating
                    p < 0.25f -> {
                        eyeScaleY = 1.0f
                        eyeLookOffsetX = 0f
                        isRightEyeWinking = false
                        mouthOpenFactor = 0f
                        mouthCurve = 1.0f
                        blushScale = 1.0f
                    }
                    // Phase 2 (0.25 - 0.29): Natural Blink
                    p in 0.25f..0.29f -> {
                        val blinkFraction = (p - 0.25f) / 0.04f
                        eyeScaleY = if (blinkFraction < 0.5f) {
                            1f - (blinkFraction * 2f * 0.92f)
                        } else {
                            0.08f + ((blinkFraction - 0.5f) * 2f * 0.92f)
                        }
                    }
                    // Phase 3 (0.29 - 0.42): Glance Left curiously
                    p in 0.29f..0.42f -> {
                        eyeScaleY = 1.0f
                        val glanceProgress = (p - 0.29f) / 0.13f
                        eyeLookOffsetX = if (glanceProgress < 0.5f) {
                            -(glanceProgress * 2f * 4.5f)
                        } else {
                            -4.5f + ((glanceProgress - 0.5f) * 2f * 4.5f)
                        }
                    }
                    // Phase 4 (0.42 - 0.46): Quick Double Blink
                    p in 0.42f..0.46f -> {
                        eyeLookOffsetX = 0f
                        val blinkFraction = (p - 0.42f) / 0.04f
                        eyeScaleY = if (blinkFraction < 0.5f) {
                            1f - (blinkFraction * 2f * 0.92f)
                        } else {
                            0.08f + ((blinkFraction - 0.5f) * 2f * 0.92f)
                        }
                    }
                    // Phase 5 (0.46 - 0.62): Glance Right curiously
                    p in 0.46f..0.62f -> {
                        eyeScaleY = 1.0f
                        val glanceProgress = (p - 0.46f) / 0.16f
                        eyeLookOffsetX = if (glanceProgress < 0.5f) {
                            (glanceProgress * 2f * 4.5f)
                        } else {
                            4.5f - ((glanceProgress - 0.5f) * 2f * 4.5f)
                        }
                    }
                    // Phase 6 (0.62 - 0.78): Playful Wink with rosy blush
                    p in 0.62f..0.78f -> {
                        eyeLookOffsetX = 0f
                        val winkProgress = (p - 0.62f) / 0.16f
                        if (winkProgress in 0.1f..0.9f) {
                            isRightEyeWinking = true
                            blushScale = 1.35f
                            mouthCurve = 1.25f
                        } else {
                            isRightEyeWinking = false
                            blushScale = 1.0f
                            mouthCurve = 1.0f
                        }
                    }
                    // Phase 7 (0.78 - 0.96): Joyful Grin / Giggle
                    p in 0.78f..0.96f -> {
                        isRightEyeWinking = false
                        val grinProgress = (p - 0.78f) / 0.18f
                        mouthOpenFactor = if (grinProgress < 0.5f) {
                            grinProgress * 2f
                        } else {
                            1f - ((grinProgress - 0.5f) * 2f)
                        }
                        blushScale = 1.2f
                    }
                    // Phase 8 (0.96 - 1.00): Return to serene smile
                    else -> {
                        isRightEyeWinking = false
                        mouthOpenFactor = 0f
                        blushScale = 1.0f
                        eyeLookOffsetX = 0f
                        eyeScaleY = 1.0f
                    }
                }
                invalidate()
            }
        }
        loopSequenceAnimator?.start()
    }

    /**
     * Trigger bouncy excited celebration on user touch / tap
     */
    fun triggerExcitedReaction() {
        reactionAnimator?.cancel()
        reactionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                bounceScale = 1f + (sin(f * Math.PI.toFloat()) * 0.28f)
                mouthOpenFactor = sin(f * Math.PI.toFloat())
                isRightEyeWinking = (f in 0.25f..0.75f)
                blushScale = 1.4f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bounceScale = 1.0f
                    mouthOpenFactor = 0f
                    isRightEyeWinking = false
                    blushScale = 1.0f
                    invalidate()
                }
            })
        }
        reactionAnimator?.start()
    }

    fun setMood(mood: Mood) {
        this.currentMood = mood
        when (mood) {
            Mood.LOOP -> {
                startLoopSequence()
            }
            Mood.HAPPY -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 1.0f
                eyeLookOffsetX = 0f
                isRightEyeWinking = false
                mouthOpenFactor = 0f
                mouthCurve = 1.0f
                blushScale = 1.0f
                invalidate()
            }
            Mood.WINK -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 1.0f
                eyeLookOffsetX = 0f
                isRightEyeWinking = true
                mouthOpenFactor = 0.2f
                mouthCurve = 1.2f
                blushScale = 1.3f
                invalidate()
            }
            Mood.EXCITED -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 1.0f
                eyeLookOffsetX = 0f
                isRightEyeWinking = false
                mouthOpenFactor = 1.0f
                mouthCurve = 1.4f
                blushScale = 1.4f
                invalidate()
            }
            Mood.COOL -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 1.0f
                eyeLookOffsetX = 0f
                isRightEyeWinking = false
                mouthOpenFactor = 0.1f
                mouthCurve = 0.8f
                blushScale = 0.9f
                invalidate()
            }
            Mood.LOVE -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 1.0f
                eyeLookOffsetX = 0f
                isRightEyeWinking = false
                mouthOpenFactor = 0.4f
                mouthCurve = 1.2f
                blushScale = 1.5f
                invalidate()
            }
            Mood.SAD -> {
                loopSequenceAnimator?.cancel()
                eyeScaleY = 0.82f
                eyeLookOffsetX = 0f
                isRightEyeWinking = false
                mouthOpenFactor = 0f
                mouthCurve = -1.0f // Frown
                blushScale = 0.4f
                tearAnimator?.start()
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (floatAnimator?.isStarted != true) {
            floatAnimator?.start()
        }
        if (currentMood == Mood.LOOP && loopSequenceAnimator?.isStarted != true) {
            loopSequenceAnimator?.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        floatAnimator?.cancel()
        loopSequenceAnimator?.cancel()
        reactionAnimator?.cancel()
        tearAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = (h / 2f) + floatOffsetY
        val radius = (minOf(w, h) / 2f) * 0.84f

        canvas.save()
        // Apply tilt and scale bounce
        canvas.rotate(tiltAngle, cx, cy)
        canvas.scale(bounceScale, bounceScale, cx, cy)

        // 1. Face Circle
        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawCircle(cx, cy, radius, faceOutlinePaint)

        // Dimension references
        val eyeSpacing = radius * 0.46f
        val eyeRadius = radius * 0.18f
        val eyeCenterY = cy - (radius * 0.14f)
        val leftEyeX = cx - eyeSpacing
        val rightEyeX = cx + eyeSpacing

        // 2. Cheeks (blush)
        val blushRadius = radius * 0.20f * blushScale
        val blushY = cy + (radius * 0.16f)
        canvas.drawCircle(cx - (radius * 0.62f), blushY, blushRadius, blushPaint)
        canvas.drawCircle(cx + (radius * 0.62f), blushY, blushRadius, blushPaint)

        // 3. Eyes / Accessories based on Mood
        when {
            currentMood == Mood.COOL -> {
                drawSunglasses(canvas, cx, eyeCenterY, radius)
            }
            currentMood == Mood.LOVE -> {
                drawHeartEye(canvas, leftEyeX, eyeCenterY, eyeRadius * 1.3f)
                drawHeartEye(canvas, rightEyeX, eyeCenterY, eyeRadius * 1.3f)
            }
            else -> {
                // Left Eye (Normal / Blinking)
                drawEye(canvas, leftEyeX, eyeCenterY, eyeRadius, eyeScaleY, eyeLookOffsetX, isWink = false)

                // Right Eye (Normal or Winking)
                drawEye(
                    canvas,
                    rightEyeX,
                    eyeCenterY,
                    eyeRadius,
                    if (isRightEyeWinking) 0.08f else eyeScaleY,
                    eyeLookOffsetX,
                    isWink = isRightEyeWinking
                )

                if (currentMood == Mood.SAD) {
                    drawSadEyebrows(canvas, cx, eyeCenterY, eyeSpacing, eyeRadius)
                    drawTeardrop(
                        canvas,
                        rightEyeX + (eyeRadius * 0.7f),
                        eyeCenterY + (eyeRadius * 0.9f) + (tearProgress * eyeRadius * 1.6f),
                        eyeRadius * 0.5f,
                        1f - (tearProgress * 0.4f)
                    )
                }
            }
        }

        // 4. Mouth
        drawMouth(canvas, cx, cy, radius)

        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        scaleY: Float,
        lookOffset: Float,
        isWink: Boolean
    ) {
        if (isWink) {
            // Cute curved winking arch ^
            val winkPath = Path().apply {
                val arcHalfW = radius * 1.15f
                moveTo(x - arcHalfW, y + (radius * 0.3f))
                quadTo(x, y - (radius * 0.75f), x + arcHalfW, y + (radius * 0.3f))
            }
            mouthPaint.strokeWidth = radius * 0.35f
            canvas.drawPath(winkPath, mouthPaint)
            return
        }

        canvas.save()
        canvas.scale(1.0f, scaleY, x, y)

        // Eye pupil
        val pupilX = x + lookOffset
        canvas.drawCircle(pupilX, y, radius, eyePaint)

        // Cute specular light reflections
        if (scaleY > 0.4f) {
            val highlightRadius = radius * 0.36f
            canvas.drawCircle(pupilX - (radius * 0.32f), y - (radius * 0.32f), highlightRadius, eyeHighlightPaint)

            val smallHighlightRadius = radius * 0.18f
            canvas.drawCircle(pupilX + (radius * 0.34f), y + (radius * 0.22f), smallHighlightRadius, eyeHighlightPaint)
        }

        canvas.restore()
    }

    private fun drawSunglasses(canvas: Canvas, cx: Float, eyeCenterY: Float, faceRadius: Float) {
        val glassW = faceRadius * 0.44f
        val glassH = faceRadius * 0.36f
        val spacing = faceRadius * 0.12f

        // Left lens
        val leftRect = RectF(
            cx - spacing - glassW,
            eyeCenterY - (glassH / 2f),
            cx - spacing,
            eyeCenterY + (glassH / 2f)
        )
        canvas.drawRoundRect(leftRect, 8f, 8f, glassesPaint)

        // Right lens
        val rightRect = RectF(
            cx + spacing,
            eyeCenterY - (glassH / 2f),
            cx + spacing + glassW,
            eyeCenterY + (glassH / 2f)
        )
        canvas.drawRoundRect(rightRect, 8f, 8f, glassesPaint)

        // Bridge
        canvas.drawLine(
            cx - spacing,
            eyeCenterY - (glassH * 0.15f),
            cx + spacing,
            eyeCenterY - (glassH * 0.15f),
            glassesPaint.apply { strokeWidth = 5f; style = Paint.Style.STROKE }
        )
        glassesPaint.style = Paint.Style.FILL

        // Specular glare line across sunglasses
        canvas.drawLine(
            leftRect.left + 6f,
            leftRect.top + 6f,
            leftRect.right - 10f,
            leftRect.bottom - 8f,
            glassesShinePaint
        )
        canvas.drawLine(
            rightRect.left + 6f,
            rightRect.top + 6f,
            rightRect.right - 10f,
            rightRect.bottom - 8f,
            glassesShinePaint
        )
    }

    private fun drawHeartEye(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path().apply {
            moveTo(x, y + (size * 0.45f))
            cubicTo(
                x - (size * 0.65f), y,
                x - (size * 0.65f), y - (size * 0.55f),
                x, y - (size * 0.25f)
            )
            cubicTo(
                x + (size * 0.65f), y - (size * 0.55f),
                x + (size * 0.65f), y,
                x, y + (size * 0.45f)
            )
            close()
        }
        canvas.drawPath(path, heartPaint)
    }

    private fun drawSadEyebrows(canvas: Canvas, cx: Float, eyeCenterY: Float, eyeSpacing: Float, eyeRadius: Float) {
        val browY = eyeCenterY - (eyeRadius * 1.15f)
        val browW = eyeRadius * 1.1f
        val leftEyeX = cx - eyeSpacing
        val rightEyeX = cx + eyeSpacing

        // Left eyebrow (slanted up towards center /)
        canvas.drawLine(leftEyeX - (browW * 0.5f), browY + 3.5f, leftEyeX + (browW * 0.5f), browY - 2.5f, sadEyebrowPaint)
        // Right eyebrow (slanted up towards center \)
        canvas.drawLine(rightEyeX - (browW * 0.5f), browY - 2.5f, rightEyeX + (browW * 0.5f), browY + 3.5f, sadEyebrowPaint)
    }

    private fun drawTeardrop(canvas: Canvas, x: Float, y: Float, size: Float, alphaFactor: Float) {
        tearPaint.alpha = (230 * alphaFactor).toInt().coerceIn(0, 255)
        val path = Path().apply {
            moveTo(x, y - size)
            cubicTo(x + size, y + (size * 0.3f), x + size, y + size, x, y + size)
            cubicTo(x - size, y + size, x - size, y + (size * 0.3f), x, y - size)
            close()
        }
        canvas.drawPath(path, tearPaint)
    }
    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val mouthY = cy + (radius * 0.32f)
        val mouthHalfW = radius * 0.36f * (1f + (mouthOpenFactor * 0.15f))

        mouthPaint.strokeWidth = radius * 0.12f

        if (mouthCurve < 0f) {
            // Sad Downward Frown Arc with subtle quiver
            val frownDepth = radius * 0.24f * kotlin.math.abs(mouthCurve)
            val frownPath = Path().apply {
                moveTo(cx - mouthHalfW, mouthY + (frownDepth * 0.4f) + mouthQuiver)
                quadTo(cx, mouthY - frownDepth + mouthQuiver, cx + mouthHalfW, mouthY + (frownDepth * 0.4f) + mouthQuiver)
            }
            canvas.drawPath(frownPath, mouthPaint)
        } else if (mouthOpenFactor <= 0.05f) {
            // Line Smile curve
            val smilePath = Path().apply {
                moveTo(cx - mouthHalfW, mouthY)
                quadTo(cx, mouthY + (radius * 0.32f * mouthCurve), cx + mouthHalfW, mouthY)
            }
            canvas.drawPath(smilePath, mouthPaint)
        } else {
            // Open Joyful Smile with tongue
            val openH = radius * 0.48f * mouthOpenFactor

            val openMouthPath = Path().apply {
                moveTo(cx - mouthHalfW, mouthY)
                quadTo(cx, mouthY + openH, cx + mouthHalfW, mouthY)
                close()
            }

            // Draw dark inner mouth
            canvas.drawPath(openMouthPath, mouthFillPaint)

            // Draw cute tongue at bottom
            canvas.save()
            canvas.clipPath(openMouthPath)
            val tongueRadius = mouthHalfW * 0.62f
            canvas.drawCircle(cx, mouthY + openH, tongueRadius, tonguePaint)
            canvas.restore()

            // Outer outline of open smile
            canvas.drawPath(openMouthPath, mouthPaint)
        }
    }
}
