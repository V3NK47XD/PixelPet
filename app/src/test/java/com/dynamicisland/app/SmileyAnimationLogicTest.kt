package com.dynamicisland.app

import com.dynamicisland.app.util.PreferencesHelper
import com.dynamicisland.app.view.AnimatedSmileyView
import org.junit.Assert.assertEquals
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
}
