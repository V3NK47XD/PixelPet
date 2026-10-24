package com.dynamicisland.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PetPhysicsEngine: Realistic Rope Keychain & Pendulum Physics Engine.
 *
 * Responsibilities:
 * - Prioritizes 3-axis Accelerometer for universal, zero-latency phone movement, tilt, and inertia detection.
 * - Uses the user's calibrated comfortable holding angle as the neutral (0, 0) reference vector.
 * - Dynamic pendulum swinging, rope sag, inertia lag, and high-frequency wire shiver ripples.
 * - Optional Gyroscope enhancement for rotational momentum and centrifugal tension when available.
 * - 100% Battery-Safe: Unregisters sensor listeners on screen off or when disabled in settings.
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

    // Sensors: Accelerometer is primary and universal; Gyroscope is optional enhancement
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var isRegistered = false
    private var isScreenActive = true

    // Calibrated Neutral Orientation Reference (User's comfortable holding position)
    var neutralAccelX = 0f
        private set
    var neutralAccelY = 6.8f // Default ~44° ergonomic phone holding angle
        private set
    var neutralAccelZ = 6.8f
        private set

    // Current output physics state (in DP)
    var currentOffsetXDp = 0f
        private set
    var currentOffsetYDp = 0f
        private set
    var currentTiltDeg = 0f
        private set

    // Keychain Wire Shiver Vibration
    var shiverAmplitudeDp = 0f
        private set
    var shiverPhase = 0f
        private set

    // Velocity & Spring Damping
    var velocityX = 0f
        private set
    var velocityY = 0f
        private set
    private val springK = 0.09f  // Tuned for native 120Hz/144Hz VSync
    private val damping = 0.93f  // Smooth high-refresh pendulum damping

    // Dynamic targets from accelerometer
    private var targetTiltXDp = 0f
    private var targetTiltYDp = 0f

    // Dynamic linear acceleration filters
    private var smoothGravityX = 0f
    private var smoothGravityY = 6.8f

    // Shake & Jerk tracking
    private var lastAccelX = 0f
    private var lastAccelY = 6.8f
    private var lastAccelZ = 6.8f
    private var lastShakeTime = 0L

    // Gyroscope tracking
    private var lastGyroTimeNs = 0L
    private var centrifugalForceDp = 0f

    // High-Refresh VSync Loop (Native 120Hz / 144Hz on supported displays)
    private var isLoopScheduled = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            isLoopScheduled = false
            if (!isRegistered || !isScreenActive) return

            stepPhysics()

            val hasMovement = abs(velocityX) > 0.02f || abs(velocityY) > 0.02f ||
                    shiverAmplitudeDp > 0.02f ||
                    abs(currentOffsetXDp - targetTiltXDp) > 0.05f

            if (hasMovement) {
                scheduleNextFrame()
            }
        }
    }

    private fun scheduleNextFrame() {
        if (!isLoopScheduled && isRegistered && isScreenActive) {
            isLoopScheduled = true
            try {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } catch (e: Exception) {
                handler.post {
                    if (isRegistered && isScreenActive) {
                        Choreographer.getInstance().postFrameCallback(frameCallback)
                    }
                }
            }
        }
    }

    init {
        loadCalibration()
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun loadCalibration() {
        neutralAccelX = prefs.calibratedAccelX
        neutralAccelY = prefs.calibratedAccelY
        neutralAccelZ = prefs.calibratedAccelZ
        smoothGravityX = neutralAccelX
        smoothGravityY = neutralAccelY
    }

    fun recalibrate(x: Float, y: Float, z: Float) {
        neutralAccelX = x
        neutralAccelY = y
        neutralAccelZ = z
        smoothGravityX = x
        smoothGravityY = y
        targetTiltXDp = 0f
        targetTiltYDp = 0f
        velocityX = 0f
        velocityY = 0f
        currentOffsetXDp = 0f
        currentOffsetYDp = 0f
        currentTiltDeg = 0f
        onPhysicsUpdate(0f, 0f, 0f, 0f, 0f, 0f, false)
    }

    fun start() {
        if (!prefs.isGyroPhysicsEnabled || isRegistered) return
        registerSensors()
    }

    fun stop() {
        unregisterSensors()
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (ignored: Exception) {}
        isLoopScheduled = false
        resetState()
    }

    fun onScreenOff() {
        isScreenActive = false
        unregisterSensors()
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (ignored: Exception) {}
        isLoopScheduled = false
    }
    fun onScreenOn() {
        isScreenActive = true
        if (prefs.isGyroPhysicsEnabled) {
            registerSensors()
        }
    }

    fun onSettingsChanged() {
        loadCalibration()
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

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        isRegistered = true
        scheduleNextFrame()
    }

    private fun unregisterSensors() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (ignored: Exception) {}
        isLoopScheduled = false
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
        centrifugalForceDp = 0f
        onPhysicsUpdate(0f, 0f, 0f, 0f, 0f, 0f, false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isScreenActive) return
        scheduleNextFrame()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0] // Lateral tilt (+ left, - right)
                val ay = event.values[1] // Forward tilt (+ upright, - flat)
                val az = event.values[2]

                // Deviation from calibrated neutral orientation
                val deltaX = ax - neutralAccelX
                val deltaY = ay - neutralAccelY
                val deltaZ = az - neutralAccelZ

                // Configurable motion sensitivity deadzone: ignore small involuntary hand tremors/tilts
                val deadzone = prefs.leashMotionThreshold
                val filteredDeltaX = when {
                    abs(deltaX) <= deadzone -> 0f
                    deltaX > deadzone -> (deltaX - deadzone) * (9.8f / (9.8f - deadzone).coerceAtLeast(0.1f))
                    else -> (deltaX + deadzone) * (9.8f / (9.8f - deadzone).coerceAtLeast(0.1f))
                }
                val filteredDeltaY = when {
                    abs(deltaY) <= deadzone -> 0f
                    deltaY > deadzone -> (deltaY - deadzone)
                    else -> (deltaY + deadzone)
                }

                // Full 180° lateral tilt ratio: -filteredDeltaX / 9.8f across full [-1.0, 1.0]
                val normalizedTiltX = (-filteredDeltaX / 9.8f).coerceIn(-1.0f, 1.0f)
                val swingAngleRad = kotlin.math.asin(normalizedTiltX.toDouble()).toFloat()

                // Circular pendulum arc of radius R = leashLengthDp centered at camera anchor:
                val leashR = prefs.leashLengthDp.toFloat().coerceAtLeast(20f)
                // Full 180° lateral swing displacement: reaches +/- leashR at 90° tilt
                val rawTargetX = (leashR * kotlin.math.sin(swingAngleRad.toDouble()).toFloat()).coerceIn(-leashR, leashR)
                // Circular arc lift: at 90° tilt (cos = 0), the pet rises by -leashR horizontally
                val arcLiftDp = -leashR * (1f - kotlin.math.cos(swingAngleRad.toDouble()).toFloat())
                val pitchSagDp = (filteredDeltaY * 1.5f).coerceIn(-14f, 24f)
                val rawTargetY = (arcLiftDp + pitchSagDp).coerceIn(-leashR, 28f)

                // Responsive low-pass filter
                targetTiltXDp = (targetTiltXDp * 0.72f) + (rawTargetX * 0.28f)
                targetTiltYDp = (targetTiltYDp * 0.72f) + (rawTargetY * 0.28f)

                // High-pass filter for dynamic linear acceleration (inertia lag when waving phone)
                smoothGravityX = (smoothGravityX * 0.85f) + (ax * 0.15f)
                smoothGravityY = (smoothGravityY * 0.85f) + (ay * 0.15f)
                val dynAccelX = ax - smoothGravityX

                // Sudden lateral phone motion pushes keychain in opposite direction (ignores tremors below deadzone)
                if (abs(dynAccelX) > (0.6f + deadzone * 0.6f)) {
                    velocityX += (-dynAccelX * 0.30f)
                }

                // Shake / Jerk detection
                val deltaAccel = abs(ax - lastAccelX) + abs(ay - lastAccelY) + abs(az - lastAccelZ)
                lastAccelX = ax
                lastAccelY = ay
                lastAccelZ = az

                val shiverThreshold = 4.2f + (deadzone * 1.8f)
                if (deltaAccel > shiverThreshold) {
                    val addedShiver = ((deltaAccel - shiverThreshold) * 0.80f).coerceIn(0f, 14f)
                    shiverAmplitudeDp = maxOf(shiverAmplitudeDp, addedShiver)
                }

                val now = SystemClock.elapsedRealtime()
                if (deltaAccel > 12.5f && (now - lastShakeTime) > 500L) {
                    lastShakeTime = now
                    triggerShakeImpulse(ax)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val nowNs = event.timestamp
                val timeDiff = if (lastGyroTimeNs != 0L) {
                    ((nowNs - lastGyroTimeNs) / 1_000_000_000f).coerceIn(0.005f, 0.1f)
                } else {
                    0.02f
                }
                lastGyroTimeNs = nowNs

                val gz = event.values[2] // Yaw angular velocity (in screen plane)
                val gy = event.values[1] // Roll angular velocity

                val deadzone = 0.035f
                val cleanGz = if (abs(gz) > deadzone) gz * 0.96f else 0f
                val cleanGy = if (abs(gy) > deadzone) gy * 0.96f else 0f

                // In-plane rotation directly imparts angular momentum
                if (abs(cleanGz) > 0.1f) {
                    velocityX += (-cleanGz * 3.5f * timeDiff)
                }
                if (abs(cleanGy) > 0.1f) {
                    velocityX += (-cleanGy * 14f * timeDiff)
                }

                // In-plane spin creates centrifugal tension
                val spinSpeed = abs(cleanGz)
                centrifugalForceDp = (spinSpeed * spinSpeed * 2.2f).coerceIn(0f, 14f)

                if (spinSpeed > 1.2f) {
                    shiverAmplitudeDp = maxOf(shiverAmplitudeDp, (spinSpeed * 2.5f).coerceIn(0f, 12f))
                }
            }
        }
    }

    private fun triggerShakeImpulse(directionX: Float) {
        val impulseDirection = if (directionX != 0f) -Math.signum(directionX) else 1f
        velocityX += impulseDirection * 18f
        velocityY += -8f
    }

    private fun stepPhysics() {
        val effectiveTargetY = targetTiltYDp + (centrifugalForceDp * 0.45f)
        centrifugalForceDp *= 0.88f

        // Damped harmonic pendulum spring
        val forceX = (targetTiltXDp - currentOffsetXDp) * springK
        val forceY = (effectiveTargetY - currentOffsetYDp) * springK

        velocityX = (velocityX + forceX) * damping
        velocityY = (velocityY + forceY) * damping

        currentOffsetXDp += velocityX
        currentOffsetYDp += velocityY

        // Constrain max physics swing to full leash arc radius
        val leashR = prefs.leashLengthDp.toFloat().coerceAtLeast(20f)
        val maxSwing = leashR * 1.15f
        val dist = sqrt((currentOffsetXDp * currentOffsetXDp) + (currentOffsetYDp * currentOffsetYDp))
        if (dist > maxSwing && dist > 0f) {
            val scale = maxSwing / dist
            currentOffsetXDp *= scale
            currentOffsetYDp *= scale
        }

        // Dynamic tilt angle: exact tangent to circular pendulum swing arc across full 180° (-90° to +90°)
        val swingTangentDeg = Math.toDegrees(kotlin.math.atan2(currentOffsetXDp.toDouble(), (leashR + currentOffsetYDp).coerceAtLeast(1f).toDouble())).toFloat()
        currentTiltDeg = (swingTangentDeg + velocityX * 1.25f).coerceIn(-90f, 90f)
        // High-frequency wire shiver vibration wave
        if (shiverAmplitudeDp > 0.05f) {
            shiverPhase += 0.95f
            shiverAmplitudeDp *= 0.87f
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
