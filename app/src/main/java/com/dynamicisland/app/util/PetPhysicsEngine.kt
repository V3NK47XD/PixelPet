package com.dynamicisland.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PetPhysicsEngine: Fluid physical inertia and tilt-swing simulation.
 *
 * Behaviors:
 * - When the phone is tilted, the pet swings towards the tilt direction with realistic gravity pull.
 * - When the phone is shaken, adds dynamic impulse so the pet orbits and swings around on its leash!
 * - When still, a spring-damper settling model smoothly brings the pet to rest in its natural alignment.
 *
 * Battery Optimization:
 * - Completely unregisters sensor listeners whenever disabled in settings OR when the screen turns off.
 * - Uses SENSOR_DELAY_UI (~30Hz) to prevent battery drain.
 */
class PetPhysicsEngine(
    private val context: Context,
    private val onPhysicsUpdate: (
        offsetXDp: Float,
        offsetYDp: Float,
        tiltAngleDeg: Float,
        velocityXDp: Float,
        shiverAmplitudeDp: Float,
        shiverPhase: Float,
        isTensionHigh: Boolean
    ) -> Unit
) : SensorEventListener {

    private val prefs = PreferencesHelper(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val handler = Handler(Looper.getMainLooper())

    private var gravitySensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private var isRegistered = false
    private var isScreenActive = true

    // Physics state
    var currentOffsetXDp = 0f
        private set
    var currentOffsetYDp = 0f
        private set
    var currentTiltDeg = 0f
        private set

    // Keychain Wire Shiver Vibration (High-frequency harmonic ripples when shaken)
    var shiverAmplitudeDp = 0f
        private set
    var shiverPhase = 0f
        private set

    // Velocity & Spring Damping
    var velocityX = 0f
        private set
    var velocityY = 0f
        private set
    private var springK = 0.14f // Spring restorative stiffness
    private var damping = 0.84f // Velocity friction damping
    // Low-pass filtered tilt targets from sensors
    private var targetTiltXDp = 0f
    private var targetTiltYDp = 0f

    // 2D Gyroscope Time-Delta Integration & Complementary Sensor Fusion
    private var lastGyroTimeNs: Long = 0L
    private var gyroIntegratedAngleRad: Float = 0f
    private var fusedHangAngleRad: Float = 0f
    private var centrifugalForceDp: Float = 0f

    // Shake tracking
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 9.8f
    private var lastShakeTime = 0L
    // Physics step loop (runs at ~35 FPS when active and needed)
    private val physicsStepRunnable = object : Runnable {
        override fun run() {
            if (!isRegistered || !isScreenActive) return

            stepPhysics()

            val hasSignificantMovement = abs(velocityX) > 0.08f || abs(velocityY) > 0.08f ||
                    shiverAmplitudeDp > 0.05f ||
                    abs(currentOffsetXDp - targetTiltXDp) > 0.15f

            val nextDelay = if (hasSignificantMovement) 25L else 45L
            handler.postDelayed(this, nextDelay)
        }
    }

    init {
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun start() {
        if (!prefs.isGyroPhysicsEnabled || isRegistered) return
        registerSensors()
    }

    fun stop() {
        unregisterSensors()
        handler.removeCallbacks(physicsStepRunnable)
        resetState()
    }

    fun onScreenOff() {
        isScreenActive = false
        unregisterSensors()
        handler.removeCallbacks(physicsStepRunnable)
    }

    fun onScreenOn() {
        isScreenActive = true
        if (prefs.isGyroPhysicsEnabled) {
            registerSensors()
        }
    }

    fun onSettingsChanged() {
        if (prefs.isGyroPhysicsEnabled) {
            if (isScreenActive && !isRegistered) {
                registerSensors()
            }
        } else {
            unregisterSensors()
            resetState()
        }
    }

    private fun registerSensors() {
        if (isRegistered || sensorManager == null) return

        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        isRegistered = true
        handler.removeCallbacks(physicsStepRunnable)
        handler.post(physicsStepRunnable)
    }

    private fun unregisterSensors() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    private fun resetState() {
        targetTiltXDp = 0f
        targetTiltYDp = 0f
        velocityX = 0f
        velocityY = 0f
        currentOffsetXDp = 0f
        currentOffsetYDp = 0f
        currentTiltDeg = 0f
        shiverAmplitudeDp = 0f
        shiverPhase = 0f
        lastGyroTimeNs = 0L
        gyroIntegratedAngleRad = 0f
        fusedHangAngleRad = 0f
        centrifugalForceDp = 0f
        onPhysicsUpdate(0f, 0f, 0f, 0f, 0f, 0f, false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isScreenActive) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0] // Lateral tilt (left/right)
                val ay = event.values[1] // Forward tilt (up/down)
                val az = event.values[2]

                // Accelerometer reference angle in 2D screen plane
                val accelAngleRad = kotlin.math.atan2(-ax.toDouble(), ay.toDouble()).toFloat()

                // Complementary filter fusion (94% smooth gyro integration, 6% gravity reference pull)
                fusedHangAngleRad = (0.94f * (fusedHangAngleRad + gyroIntegratedAngleRad)) + (0.06f * accelAngleRad)
                gyroIntegratedAngleRad = 0f // consumed into fused hanging angle

                // Project 2D hanging offset using effective pendulum arm (28dp radius)
                val hangRadiusDp = 28f
                val rawTiltXDp = (kotlin.math.sin(fusedHangAngleRad.toDouble()).toFloat() * hangRadiusDp).coerceIn(-28f, 28f)
                val pitchSagDp = ((ay - 6.5f) * 1.5f).coerceIn(-10f, 20f)
                val rawTiltYDp = (hangRadiusDp * (1f - kotlin.math.cos(fusedHangAngleRad.toDouble()).toFloat()) * 0.45f + pitchSagDp).coerceIn(-10f, 22f)

                // Smooth low-pass filter toward target
                targetTiltXDp = (targetTiltXDp * 0.72f) + (rawTiltXDp * 0.28f)
                targetTiltYDp = (targetTiltYDp * 0.72f) + (rawTiltYDp * 0.28f)

                // Shake detection for rapid phone shake & wire shiver
                val deltaAccel = abs(ax - lastAccelX) + abs(ay - lastAccelY) + abs(az - lastAccelZ)
                lastAccelX = ax
                lastAccelY = ay
                lastAccelZ = az

                // Rapid phone movement or shaking injects high-frequency wire shiver waves
                if (deltaAccel > 5.2f) {
                    val addedShiver = ((deltaAccel - 5.2f) * 0.75f).coerceIn(0f, 16f)
                    shiverAmplitudeDp = maxOf(shiverAmplitudeDp, addedShiver)
                }

                val now = SystemClock.elapsedRealtime()
                if (deltaAccel > 13f && (now - lastShakeTime) > 550L) {
                    lastShakeTime = now
                    triggerShakeImpulse(ax)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val nowNs = event.timestamp
                val timeDiff = if (lastGyroTimeNs != 0L) {
                    ((nowNs - lastGyroTimeNs) / 1_000_000_000f).coerceIn(0.005f, 0.1f)
                } else {
                    0.025f
                }
                lastGyroTimeNs = nowNs

                val gx = event.values[0] // Pitch angular velocity
                val gy = event.values[1] // Roll angular velocity
                val gz = event.values[2] // Yaw angular velocity (in screen plane)

                // Deadzone to eliminate micro-drift when phone is stationary
                val deadzone = 0.035f
                val cleanGz = if (abs(gz) > deadzone) gz * 0.96f else 0f
                val cleanGy = if (abs(gy) > deadzone) gy * 0.96f else 0f

                // Time-differential integration of in-plane rotation
                gyroIntegratedAngleRad += (-cleanGz * timeDiff)

                // Roll velocity imparts lateral linear impulse to hanging bob
                if (abs(cleanGy) > 0.05f) {
                    velocityX += (-cleanGy * 18f * timeDiff)
                }

                // In-plane spin creates centrifugal tension: F = m * omega^2 * R
                val spinSpeed = abs(cleanGz)
                centrifugalForceDp = (spinSpeed * spinSpeed * 2.5f).coerceIn(0f, 14f)

                if (spinSpeed > 1.0f) {
                    velocityX += (-cleanGz * 4.2f)
                    val gyroShiver = (spinSpeed * 2.2f).coerceIn(0f, 12f)
                    shiverAmplitudeDp = maxOf(shiverAmplitudeDp, gyroShiver)
                }
            }
        }
    }
    private fun triggerShakeImpulse(directionX: Float) {
        // Shaking causes energetic pendulum swing impulse!
        val impulseDirection = if (directionX != 0f) -Math.signum(directionX) else 1f
        velocityX += impulseDirection * 18f
        velocityY += -8f
    }
    private fun stepPhysics() {
        // Centrifugal force from in-plane spinning pulls leash downward
        val effectiveTargetY = targetTiltYDp + (centrifugalForceDp * 0.5f)
        centrifugalForceDp *= 0.88f // exponential decay

        // Damped harmonic spring toward sensor target
        val forceX = (targetTiltXDp - currentOffsetXDp) * springK
        val forceY = (effectiveTargetY - currentOffsetYDp) * springK

        velocityX = (velocityX + forceX) * damping
        velocityY = (velocityY + forceY) * damping

        currentOffsetXDp += velocityX
        currentOffsetYDp += velocityY
        val maxSwing = 32f
        val dist = sqrt((currentOffsetXDp * currentOffsetXDp) + (currentOffsetYDp * currentOffsetYDp))
        if (dist > maxSwing && dist > 0f) {
            val scale = maxSwing / dist
            currentOffsetXDp *= scale
            currentOffsetYDp *= scale
        }

        // Tilt angle corresponds to lateral swing & velocity
        val dynamicTilt = (currentOffsetXDp * 0.65f + velocityX * 1.35f).coerceIn(-26f, 26f)
        currentTiltDeg = dynamicTilt

        // High-frequency wire shiver vibration wave
        if (shiverAmplitudeDp > 0.05f) {
            shiverPhase += 0.95f // advances wave phase (~22-26 Hz)
            shiverAmplitudeDp *= 0.87f // exponential friction damping
            if (shiverAmplitudeDp < 0.05f) {
                shiverAmplitudeDp = 0f
            }
        } else {
            shiverAmplitudeDp = 0f
        }

        val isHighTension = dist > 18f

        onPhysicsUpdate(
            currentOffsetXDp,
            currentOffsetYDp,
            currentTiltDeg,
            velocityX,
            shiverAmplitudeDp,
            shiverPhase,
            isHighTension
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored
    }
}
