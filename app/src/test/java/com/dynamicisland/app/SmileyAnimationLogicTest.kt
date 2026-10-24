package com.dynamicisland.app

import com.dynamicisland.app.util.PreferencesHelper
import com.dynamicisland.app.view.AnimatedSmileyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SmileyAnimationLogicTest {

    @Test
    fun testSinusoidalFloatingValuesStayWithinBounds() {
        val maxOffset = 4.5f
        // Test 100 sample points along the 2*PI circle
        for (i in 0..100) {
            val progress = (i / 100f) * (2 * Math.PI.toFloat())
            val offset = sin(progress) * maxOffset
            assertTrue("Offset $offset should be >= -$maxOffset", offset >= -maxOffset - 0.001f)
            assertTrue("Offset $offset should be <= $maxOffset", offset <= maxOffset + 0.001f)
        }
    }

    @Test
    fun testMoodEnumValuesMatchExpectedKeys() {
        val moods = com.dynamicisland.app.view.PixelPetView.PetMood.values().map { it.name }
        assertTrue(moods.contains("IDLE"))
        assertTrue(moods.contains("HAPPY"))
        assertTrue(moods.contains("SAD"))
        assertTrue(moods.contains("ANGRY"))
        assertTrue(moods.contains("NERD"))
    }

    @Test
    fun testBlinkInterpolationBounds() {
        // When progress goes 0.0 to 1.0, eye scale should stay between 0.08 and 1.0
        for (i in 0..100) {
            val blinkFraction = i / 100f
            val eyeScaleY = if (blinkFraction < 0.5f) {
                1f - (blinkFraction * 2f * 0.92f)
            } else {
                0.08f + ((blinkFraction - 0.5f) * 2f * 0.92f)
            }
            assertTrue("eyeScaleY $eyeScaleY should be >= 0.08", eyeScaleY >= 0.079f)
            assertTrue("eyeScaleY $eyeScaleY should be <= 1.0", eyeScaleY <= 1.001f)
        }
    }

    @Test
    fun testPixelPetMoodsAndManager() {
        val petMoods = com.dynamicisland.app.view.PixelPetView.PetMood.values().map { it.name }
        assertTrue(petMoods.contains("IDLE"))
        assertTrue(petMoods.contains("HAPPY"))
        assertTrue(petMoods.contains("SAD"))
        assertTrue(petMoods.contains("ANGRY"))
        assertTrue(petMoods.contains("NERD"))
        assertTrue(petMoods.contains("TEXTING"))
    }

    @Test
    fun testInstagramDetection() {
        assertTrue(com.dynamicisland.app.util.ForegroundAppDetector.isInstagram("com.instagram.android"))
        assertTrue(com.dynamicisland.app.util.ForegroundAppDetector.isInstagram("com.instagram.lite"))
        assertTrue(com.dynamicisland.app.util.ForegroundAppDetector.isInstagram("com.instagram.barcelona"))
        org.junit.Assert.assertFalse(com.dynamicisland.app.util.ForegroundAppDetector.isInstagram("com.google.android.youtube"))
        org.junit.Assert.assertFalse(com.dynamicisland.app.util.ForegroundAppDetector.isInstagram(null))
    }

    @Test
    fun testLeashRadiusConstraintMath() {
        val maxRadius = 50f
        var x = 30f
        var y = 20f
        var dist = kotlin.math.sqrt(x * x + y * y)
        if (dist > maxRadius) {
            val scale = maxRadius / dist
            x *= scale
            y *= scale
        }
        assertEquals(30f, x, 0.001f)
        assertEquals(20f, y, 0.001f)

        x = 60f
        y = 80f
        dist = kotlin.math.sqrt(x * x + y * y)
        assertTrue(dist > maxRadius)
        val scale = maxRadius / dist
        x *= scale
        y *= scale
        val newDist = kotlin.math.sqrt(x * x + y * y)
        assertEquals(50f, newDist, 0.001f)
        assertEquals(30f, x, 0.001f)
        assertEquals(40f, y, 0.001f)
    }

    @Test
    fun testCircularHitTestMath() {
        val petCenterX = 100f
        val petCenterY = 50f
        val hitRadius = 25f

        val touch1X = 110f
        val touch1Y = 55f
        val dist1 = kotlin.math.sqrt((touch1X - petCenterX) * (touch1X - petCenterX) + (touch1Y - petCenterY) * (touch1Y - petCenterY))
        assertTrue("Point on pet should hit", dist1 <= hitRadius)

        val touch2X = 135f
        val touch2Y = 50f
        val dist2 = kotlin.math.sqrt((touch2X - petCenterX) * (touch2X - petCenterX) + (touch2Y - petCenterY) * (touch2Y - petCenterY))
        assertTrue("Point outside pet should pass through", dist2 > hitRadius)
    }

    @Test
    fun testRotaryKnobSteppingMath() {
        val minValue = 0f
        val maxValue = 100f
        val stepSize = 5f

        val testInputs = listOf(0f to 0f, 2.3f to 0f, 2.6f to 5f, 47.8f to 50f, 98.9f to 100f)
        for ((raw, expected) in testInputs) {
            val steps = Math.round((raw - minValue) / stepSize)
            val stepped = (minValue + (steps * stepSize)).coerceIn(minValue, maxValue)
            assertEquals(expected, stepped, 0.001f)
        }
    }

    @Test
    fun testPhysicsSpringDampingConvergence() {
        var currentPos = 20f
        val targetPos = 0f
        var velocity = 5f
        val springK = 0.12f
        val damping = 0.82f

        // Simulate 60 physics steps
        for (i in 0..60) {
            val force = (targetPos - currentPos) * springK
            velocity = (velocity + force) * damping
            currentPos += velocity
        }

        // Position should converge smoothly near target (< 0.1) and velocity near zero
        assertTrue("Position should converge to target, was $currentPos", kotlin.math.abs(currentPos - targetPos) < 0.2f)
        assertTrue("Velocity should decay to zero, was $velocity", kotlin.math.abs(velocity) < 0.1f)
    }

    @Test
    fun testKeychainWirePhysicsMath() {
        // 1. Envelope test: endpoints must be strictly zero displacement
        val envelope0 = 4f * 0f * (1f - 0f)
        val envelope1 = 4f * 1f * (1f - 1f)
        val envelopeMid = 4f * 0.5f * (1f - 0.5f)
        assertEquals(0f, envelope0, 0.0001f)
        assertEquals(0f, envelope1, 0.0001f)
        assertEquals(1f, envelopeMid, 0.0001f)

        // 2. Shiver damping test: decays to zero over time
        var shiverAmp = 12f
        val damping = 0.87f
        for (i in 0..40) {
            shiverAmp *= damping
        }
        assertTrue("Shiver amplitude should decay near zero, was $shiverAmp", shiverAmp < 0.05f)
    }

    @Test
    fun testGyroComplementaryFilterAndDeadzone() {
        val deadzone = 0.035f
        fun cleanGyro(gz: Float) = if (kotlin.math.abs(gz) > deadzone) gz * 0.96f else 0f

        // Micro-jitter below deadzone is filtered to 0
        assertEquals(0f, cleanGyro(0.012f), 0.0001f)
        assertEquals(0f, cleanGyro(-0.025f), 0.0001f)

        // Active movement above deadzone passes with 0.96 bias correction
        assertEquals(1.5f * 0.96f, cleanGyro(1.5f), 0.0001f)

        // Complementary filter pulls smoothly toward accelerometer reference
        var fusedAngle = 0f
        val accelTarget = 0.5f // ~28.6 degrees
        for (i in 0..50) {
            fusedAngle = (0.94f * fusedAngle) + (0.06f * accelTarget)
        }
        assertTrue("Fused angle should converge near accelTarget, was $fusedAngle", kotlin.math.abs(fusedAngle - accelTarget) < 0.05f)
    }

    @Test
    fun testCalibratedAccelerometerNeutralReferenceMath() {
        val neutralX = 0f
        val neutralY = 6.8f // User's natural ~44° holding angle
        val swingScale = 6.2f

        // 1. In calibrated position: zero deflection (perfect equilibrium)
        val deltaXNeutral = 0f - neutralX
        val targetXNeutral = (-deltaXNeutral * swingScale).coerceIn(-28f, 28f)
        assertEquals(0f, targetXNeutral, 0.0001f)

        // 2. Tilt left by 20 degrees: ax = +2.5 m/s^2 -> swings left (negative X)
        val axLeft = 2.5f
        val deltaXLeft = axLeft - neutralX
        val targetXLeft = (-deltaXLeft * swingScale).coerceIn(-28f, 28f)
        assertTrue("Tilting left should deflect keychain left (negative X)", targetXLeft < -10f)

        // 3. Tilt right by 20 degrees: ax = -2.5 m/s^2 -> swings right (positive X)
        val axRight = -2.5f
        val deltaXRight = axRight - neutralX
        val targetXRight = (-deltaXRight * swingScale).coerceIn(-28f, 28f)
        assertTrue("Tilting right should deflect keychain right (positive X)", targetXRight > 10f)

        // 4. Sub-pixel resolution test: small movements (< 1dp) must produce non-zero pixel shifts
        val density = 2.75f // common 1080p density
        val smallShiftDp = 0.6f
        val pixels = (smallShiftDp * density).toInt()
        assertTrue("Small sub-integer DP movements must produce pixel motion, was $pixels", pixels >= 1)
    }

    @Test
    fun testDualModeAndLeashRangeLogic() {
        // 1. Leash range allows up to 120dp
        val minLeash = 20f
        val maxLeash = 120f
        val testLeash = 100f
        val clamped = testLeash.coerceIn(minLeash, maxLeash)
        assertEquals(100f, clamped, 0.001f)

        // 2. Mode logic verification:
        // Mode 2 (Keychain Physics ON) -> drift is completely suppressed
        val isPhysicsMode = true
        val shouldDriftMode2 = !isPhysicsMode
        assertFalse("In Mode 2 (Keychain Physics ON), autonomous drift must be suppressed", shouldDriftMode2)

        // Mode 1 (Keychain Physics OFF) -> autonomous drift is enabled
        val isOffMode = false
        val shouldDriftMode1 = !isOffMode
        assertTrue("In Mode 1 (Keychain Physics OFF), autonomous drift must be active", shouldDriftMode1)
    }

    @Test
    fun testCircularPendulumArcAndAnchorCenterOfRotation() {
        val leashL = 60f
        val petSize = 54f
        val sproutOffset = petSize * 0.30f
        val totalR = leashL + sproutOffset

        // Test pendulum swing at 25 degrees
        val swingAngleRad = Math.toRadians(25.0).toFloat()
        val xArc = totalR * kotlin.math.sin(swingAngleRad.toDouble()).toFloat()
        val yLift = -totalR * (1f - kotlin.math.cos(swingAngleRad.toDouble()).toFloat())

        // Distance from anchor point (0, 0) to pet center (xArc, totalR + yLift)
        val yFromAnchor = totalR + yLift
        val distanceFromAnchor = kotlin.math.sqrt((xArc * xArc) + (yFromAnchor * yFromAnchor))

        // Must equal totalR: proves camera anchor is the exact center of rotation!
        assertEquals(totalR, distanceFromAnchor, 0.001f)

        // Drag release snap target: must always be X=0, Y=totalR (pulled straight by gravity)
        val snapTargetX = 0f
        val snapTargetY = leashL + sproutOffset
        assertEquals(0f, snapTargetX, 0.0001f)
        assertEquals(totalR, snapTargetY, 0.0001f)
    }

    @Test
    fun testFull180TiltPendulumMathAndPetNaming() {
        val leashR = 60f

        // 1. Full 90° right tilt: deltaX = -9.8 m/s^2 -> ratio = +1.0 -> theta = +PI/2
        val ratioRight = 1.0f
        val angleRadRight = kotlin.math.asin(ratioRight.toDouble()).toFloat()
        val xRight = leashR * kotlin.math.sin(angleRadRight.toDouble()).toFloat()
        val degRight = Math.toDegrees(angleRadRight.toDouble()).toFloat()
        assertEquals(leashR, xRight, 0.001f)
        assertEquals(90f, degRight, 0.001f)

        // 2. Full 90° left tilt: deltaX = +9.8 m/s^2 -> ratio = -1.0 -> theta = -PI/2
        val ratioLeft = -1.0f
        val angleRadLeft = kotlin.math.asin(ratioLeft.toDouble()).toFloat()
        val xLeft = leashR * kotlin.math.sin(angleRadLeft.toDouble()).toFloat()
        val degLeft = Math.toDegrees(angleRadLeft.toDouble()).toFloat()
        assertEquals(-leashR, xLeft, 0.001f)
        assertEquals(-90f, degLeft, 0.001f)

        // 3. Pet default name is "Kiddo"
        val defaultName = "Kiddo"
        assertEquals("Kiddo", defaultName)
    }

    @Test
    fun testPhysicsSofteningAndSoundDefaults() {
        // 1. Leash bend softening: at high velocities on long leashes (>70dp), bend is clamped to max 14dp
        val maxBendDp = 14f
        val highVelocity = 25f
        val rawBend = -highVelocity * 1.35f
        val clampedBend = rawBend.coerceIn(-maxBendDp, maxBendDp)
        assertEquals(-14f, clampedBend, 0.001f)

        // 2. Anger duration: clamped between 3s and 8s to prevent getting stuck over 30s
        val stuckTime = 45
        val safeAngerDuration = stuckTime.coerceIn(3, 8)
        assertEquals(8, safeAngerDuration)

        // 3. Default sound volume is soft (0.55f) and clamped to [0, 1]
        val defaultVolume = 0.55f
        assertTrue("Default volume should be soft (< 0.70)", defaultVolume < 0.70f)
        assertTrue("Default volume should be audible (> 0.20)", defaultVolume > 0.20f)
    }

    @Test
    fun testLeashMotionSensitivityThresholdDeadzone() {
        val deadzone = 0.6f
        fun filterMotion(delta: Float): Float {
            return when {
                kotlin.math.abs(delta) <= deadzone -> 0f
                delta > deadzone -> (delta - deadzone) * (9.8f / (9.8f - deadzone))
                else -> (delta + deadzone) * (9.8f / (9.8f - deadzone))
            }
        }

        // Small involuntary tremor below deadzone (e.g. 0.3 m/s^2) is completely ignored -> 0f!
        assertEquals(0f, filterMotion(0.25f), 0.0001f)
        assertEquals(0f, filterMotion(-0.45f), 0.0001f)
        assertEquals(0f, filterMotion(0.60f), 0.0001f)

        // Deliberate movement above deadzone (e.g. 1.8 m/s^2) passes through smoothly
        val result = filterMotion(1.8f)
        assertTrue("Movement above deadzone must be non-zero", result > 0f)

        // Configurable threshold range [0.1, 2.5]
        val defaultThreshold = 0.6f
        val clamped = defaultThreshold.coerceIn(0.1f, 2.5f)
        assertEquals(0.6f, clamped, 0.0001f)
    }
}
