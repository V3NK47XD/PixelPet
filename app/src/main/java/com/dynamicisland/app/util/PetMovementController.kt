package com.dynamicisland.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.dynamicisland.app.view.PixelPetView
import kotlin.random.Random

/**
 * PetMovementController: Controls the pet's movement around the front camera world.
 * - Dynamic walking from left to right, underneath, and peeking
 * - Hide-and-seek behind the camera cutout with cautious peeking
 * - Mood-aware gait (sleepy crawl, angry stomp, happy hop, nerdy adjust)
 * - Autonomous non-distracting scheduler with randomized cooldowns
 */
class PetMovementController(
    private val context: Context,
    private val petView: PixelPetView,
    private val onPositionUpdated: (xDp: Int, yDp: Int) -> Unit
) {

    enum class CameraSpot {
        LEFT,
        RIGHT,
        UNDER,
        HIDING
    }

    private val prefs = PreferencesHelper(context)
    private val handler = Handler(Looper.getMainLooper())

    var currentSpot: CameraSpot = CameraSpot.LEFT
        private set

    private var currentXDp: Int = 0
    private var currentYDp: Int = 0

    private var moveAnimator: ValueAnimator? = null
    private var isRunning = false
    private val orbitDistanceDp = 44

    private val actionScheduler = object : Runnable {
        override fun run() {
            if (!isRunning || !prefs.isDynamicMovementEnabled) return
            performRandomLifeAction()
            // Next action after 18 to 45 seconds to keep it subtle and non-distracting
            val nextDelayMs = Random.nextLong(18000L, 45000L)
            handler.postDelayed(this, nextDelayMs)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        // Calculate initial resting position (Left of camera)
        currentSpot = CameraSpot.LEFT
        val (startX, startY) = getCoordinatesForSpot(CameraSpot.LEFT)
        currentXDp = startX
        currentYDp = startY
        onPositionUpdated(currentXDp, currentYDp)

        // Schedule first action after 15 seconds
        handler.postDelayed(actionScheduler, 15000L)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        moveAnimator?.cancel()
        petView.setWalkingState(false)
        petView.setHidingState(false)
    }

    fun updateCameraReference() {
        val (targetX, targetY) = getCoordinatesForSpot(currentSpot)
        currentXDp = targetX
        currentYDp = targetY
        onPositionUpdated(currentXDp, currentYDp)
    }

    private fun getCoordinatesForSpot(spot: CameraSpot): Pair<Int, Int> {
        val baseRefX = prefs.cameraRefX
        val baseRefY = prefs.cameraRefY

        return when (spot) {
            CameraSpot.LEFT -> Pair(baseRefX - orbitDistanceDp, baseRefY)
            CameraSpot.RIGHT -> Pair(baseRefX + orbitDistanceDp, baseRefY)
            CameraSpot.UNDER -> Pair(baseRefX, baseRefY + 28)
            CameraSpot.HIDING -> Pair(baseRefX, baseRefY - 6)
        }
    }

    /**
     * Walks visibly from current position to target spot with mood-appropriate gait
     */
    fun walkToSpot(targetSpot: CameraSpot, onComplete: (() -> Unit)? = null) {
        if (currentSpot == targetSpot) {
            onComplete?.invoke()
            return
        }

        val (destX, destY) = getCoordinatesForSpot(targetSpot)
        val startX = currentXDp
        val startY = currentYDp

        val direction = if (destX >= startX) 1 else -1

        // Duration depends on pet's mood
        val durationMs = when (petView.getMood()) {
            PixelPetView.PetMood.SLEEPY -> 2400L // Sluggish sleepy walk
            PixelPetView.PetMood.ANGRY -> 900L   // Fast furious stomp
            PixelPetView.PetMood.HAPPY -> 1100L  // Bouncy hop
            PixelPetView.PetMood.NERD -> 1400L   // Steady measured walk
            else -> 1500L                        // Normal cute waddle
        }

        moveAnimator?.cancel()
        petView.setWalkingState(true, direction)

        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                currentXDp = (startX + ((destX - startX) * f)).toInt()
                currentYDp = (startY + ((destY - startY) * f)).toInt()
                onPositionUpdated(currentXDp, currentYDp)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    petView.setWalkingState(false)
                    currentSpot = targetSpot
                    onComplete?.invoke()
                }
            })
        }
        moveAnimator?.start()
    }

    /**
     * Camera Hide-and-Seek: ducks behind camera, cautiously peeks out, and returns
     */
    fun performHideAndSeek(onComplete: (() -> Unit)? = null) {
        if (!prefs.isHideAndSeekEnabled) return

        // 1. Walk towards camera center
        walkToSpot(CameraSpot.HIDING) {
            // 2. Hide behind cutout
            petView.setHidingState(hiding = true, peeking = false)

            // 3. Stay hidden for 2.4 seconds
            handler.postDelayed({
                if (!isRunning) return@postDelayed

                // 4. Peek out cautiously with one eye
                petView.setHidingState(hiding = true, peeking = true)

                // 5. Look around for 1.8 seconds
                handler.postDelayed({
                    if (!isRunning) return@postDelayed

                    // 6. Return fully visible and walk back to Left or Right
                    petView.setHidingState(hiding = false, peeking = false)
                    val returnSpot = if (Random.nextBoolean()) CameraSpot.LEFT else CameraSpot.RIGHT
                    walkToSpot(returnSpot) {
                        onComplete?.invoke()
                    }
                }, 1800L)
            }, 2400L)
        }
    }

    private fun performRandomLifeAction() {
        val roll = Random.nextInt(100)
        when {
            // 45% chance: Walk between Left, Right, or Underneath
            roll < 45 -> {
                val nextSpot = when (currentSpot) {
                    CameraSpot.LEFT -> if (Random.nextInt(3) == 0) CameraSpot.UNDER else CameraSpot.RIGHT
                    CameraSpot.RIGHT -> if (Random.nextInt(3) == 0) CameraSpot.UNDER else CameraSpot.LEFT
                    CameraSpot.UNDER -> if (Random.nextBoolean()) CameraSpot.LEFT else CameraSpot.RIGHT
                    CameraSpot.HIDING -> CameraSpot.LEFT
                }
                walkToSpot(nextSpot)
            }
            // 20% chance: Play hide-and-seek behind camera cutout
            roll < 65 -> {
                performHideAndSeek()
            }
            // 25% chance: Random personality idle activity in place
            roll < 90 -> {
                val activity = when (Random.nextInt(6)) {
                    0 -> PixelPetView.IdleActivity.WAVING
                    1 -> PixelPetView.IdleActivity.SUSPICIOUS_LOOK
                    2 -> PixelPetView.IdleActivity.BLUSHING
                    3 -> PixelPetView.IdleActivity.GIGGLE
                    4 -> PixelPetView.IdleActivity.YAWNING
                    else -> PixelPetView.IdleActivity.LIGHTBULB
                }
                petView.triggerIdleActivity(activity)
            }
            // 10% chance: Tiny excited hop in place
            else -> {
                petView.triggerExcitedHop()
            }
        }
    }
}
