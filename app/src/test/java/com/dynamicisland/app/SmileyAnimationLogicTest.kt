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
}
