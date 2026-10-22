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
    private val onPhysicsUpdate: (offsetXDp: Float, offsetYDp: Float, tiltAngleDeg: Float, isTensionHigh: Boolean) -> Unit
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

    // Velocity & Spring Damping
    private var velocityX = 0f
    private var velocityY = 0f
    private var springK = 0.12f // Spring restorative stiffness
    private var damping = 0.82f // Velocity friction damping

    // Low-pass filtered tilt targets from sensors
    private var targetTiltXDp = 0f
    private var targetTiltYDp = 0f

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

            val hasSignificantMovement = abs(velocityX) > 0.1f || abs(velocityY) > 0.1f ||
                    abs(currentOffsetXDp - targetTiltXDp) > 0.2f

            val nextDelay = if (hasSignificantMovement) 28L else 45L
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
        onPhysicsUpdate(0f, 0f, 0f, false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isScreenActive) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0] // Lateral tilt (left/right): positive when tilted left
                val ay = event.values[1] // Forward tilt (up/down): positive when phone held upright
                val az = event.values[2]

                // Invert ax because when phone tilts left, gravity pulls pet left (negative offset)
                val rawTiltXDp = (-ax * 2.8f).coerceIn(-28f, 28f)
                val rawTiltYDp = ((ay - 6.5f) * 1.5f).coerceIn(-10f, 20f)

                // Smooth low-pass filter
                targetTiltXDp = (targetTiltXDp * 0.75f) + (rawTiltXDp * 0.25f)
                targetTiltYDp = (targetTiltYDp * 0.75f) + (rawTiltYDp * 0.25f)

                // Shake detection for rapid phone shake
                val deltaAccel = abs(ax - lastAccelX) + abs(ay - lastAccelY) + abs(az - lastAccelZ)
                lastAccelX = ax
                lastAccelY = ay
                lastAccelZ = az

                val now = SystemClock.elapsedRealtime()
                if (deltaAccel > 14f && (now - lastShakeTime) > 600L) {
                    lastShakeTime = now
                    triggerShakeImpulse(ax)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Angular twist velocity around Z axis adds rotational swing
                val angularZ = event.values[2] // rad/s
                if (abs(angularZ) > 1.2f) {
                    velocityX += (-angularZ * 4f)
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
        // Damped harmonic spring toward sensor target
        val forceX = (targetTiltXDp - currentOffsetXDp) * springK
        val forceY = (targetTiltYDp - currentOffsetYDp) * springK

        velocityX = (velocityX + forceX) * damping
        velocityY = (velocityY + forceY) * damping

        currentOffsetXDp += velocityX
        currentOffsetYDp += velocityY

        // Constrain max physics swing to 32dp
        val maxSwing = 32f
        val dist = sqrt((currentOffsetXDp * currentOffsetXDp) + (currentOffsetYDp * currentOffsetYDp))
        if (dist > maxSwing && dist > 0f) {
            val scale = maxSwing / dist
            currentOffsetXDp *= scale
            currentOffsetYDp *= scale
        }

        // Tilt angle corresponds to lateral swing & velocity
        val dynamicTilt = (currentOffsetXDp * 0.6f + velocityX * 1.2f).coerceIn(-24f, 24f)
        currentTiltDeg = dynamicTilt

        val isHighTension = dist > 18f

        onPhysicsUpdate(currentOffsetXDp, currentOffsetYDp, currentTiltDeg, isHighTension)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored
    }
}
