package com.dynamicisland.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dynamicisland.app.view.PixelPetView
import java.util.Calendar

/**
 * Manages the emotional state machine of the phone pet with full priority hierarchy:
 * Priority 1: CRITICAL_BATTERY (<5%) - fainting collapse
 * Priority 2: CHARGING - mint green energy particles
 * Priority 3: LOW_BATTERY (<15%) - amber worried look towards battery indicator
 * Priority 4: ANGRY - Instagram open continuously >= instagramAngerMinutes (default 5m)
 * Priority 5: SAD - Instagram open
 * Priority 6: NERD - Learning apps & Google apps (Chrome, Gmail, Docs, Drive, ChatGPT, WPS, PDF)
 * Priority 7: SLEEPY - Late-night local time curve (default >= 10:00 PM)
 * Priority 8: IDLE / HAPPY - Standard peaceful or celebratory state
 */
class PetMoodManager(private val context: Context) {

    private val prefs = PreferencesHelper(context)
    private val handler = Handler(Looper.getMainLooper())

    var currentMood: PixelPetView.PetMood = PixelPetView.PetMood.IDLE
        private set

    var instagramSessionStartMs: Long = 0L
        private set

    var currentPackageName: String = ""
        private set

    var isCharging: Boolean = false
        private set
    var isBatteryLow: Boolean = false
        private set
    var isBatteryCritical: Boolean = false
        private set

    var onMoodChanged: ((mood: PixelPetView.PetMood, reason: String) -> Unit)? = null

    // Periodic check while in Instagram to transition from SAD -> ANGRY when timer expires
    private val angerCheckRunnable = object : Runnable {
        override fun run() {
            if (isInstagram(currentPackageName)) {
                evaluateInstagramDuration()
                handler.postDelayed(this, 3000L) // Check every 3 seconds
            }
        }
    }

    fun onForegroundPackageChanged(packageName: String) {
        currentPackageName = packageName
        val isIg = isInstagram(packageName)

        if (isIg) {
            if (instagramSessionStartMs == 0L) {
                instagramSessionStartMs = System.currentTimeMillis()
                handler.removeCallbacks(angerCheckRunnable)
                handler.post(angerCheckRunnable)
            }
        } else {
            // Reset Instagram session timer when exiting Instagram
            if (instagramSessionStartMs != 0L) {
                instagramSessionStartMs = 0L
                handler.removeCallbacks(angerCheckRunnable)
            }
        }

        recalculateMood()
    }

    fun onPowerConnected() {
        isCharging = true
        recalculateMood()
    }

    fun onPowerDisconnected() {
        isCharging = false
        recalculateMood()
    }

    fun onBatteryLevelChanged(percent: Int, isLow: Boolean, isCritical: Boolean) {
        this.isBatteryLow = isLow
        this.isBatteryCritical = isCritical
        recalculateMood()
    }

    fun recalculateMood() {
        when {
            isBatteryCritical -> {
                updateMood(PixelPetView.PetMood.CRITICAL_BATTERY, "Critical battery (<5%) • Fainting")
            }
            isCharging -> {
                updateMood(PixelPetView.PetMood.CHARGING, "Charging • Mint energy particles")
            }
            isBatteryLow -> {
                updateMood(PixelPetView.PetMood.LOW_BATTERY, "Low battery (<15%) • Looking worried")
            }
            isInstagram(currentPackageName) -> {
                evaluateInstagramDuration()
            }
            isNerdApp(currentPackageName) || isGoogleNerdApp(currentPackageName) -> {
                updateMood(PixelPetView.PetMood.NERD, "Learning / Google App ($currentPackageName)")
            }
            currentPackageName == context.packageName -> {
                updateMood(PixelPetView.PetMood.HAPPY, "Companion App Active")
            }
            isNighttimeSleepy() -> {
                updateMood(PixelPetView.PetMood.SLEEPY, "Late-night sleepiness • Zzz")
            }
            else -> {
                updateMood(PixelPetView.PetMood.IDLE, "Idle ($currentPackageName)")
            }
        }
    }

    private fun evaluateInstagramDuration() {
        if (instagramSessionStartMs == 0L) return

        val elapsedMs = System.currentTimeMillis() - instagramSessionStartMs
        val angerThresholdMs = prefs.instagramAngerMinutes * 60 * 1000L

        if (elapsedMs >= angerThresholdMs) {
            val minutesSpent = (elapsedMs / 60000L).toInt()
            updateMood(
                PixelPetView.PetMood.ANGRY,
                "Instagram open for $minutesSpent mins! Rage mode activated \uD83D\uDE21"
            )
        } else {
            updateMood(PixelPetView.PetMood.SAD, "Instagram open \uD83E\uDD7A")
        }
    }

    private fun updateMood(mood: PixelPetView.PetMood, reason: String) {
        if (currentMood != mood) {
            currentMood = mood
            onMoodChanged?.invoke(mood, reason)
        }
    }

    fun isInstagram(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.instagram.android" ||
                lower == "com.instagram.lite" ||
                lower == "com.instagram.barcelona" ||
                lower.contains("instagram")
    }

    fun isNerdApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val configuredApps = prefs.nerdApps
        val lower = packageName.lowercase()

        if (configuredApps.any { it.equals(lower, ignoreCase = true) }) {
            return true
        }

        return lower.contains("chatgpt") ||
                lower.contains("openai") ||
                lower.contains("bard") ||
                lower.contains("gemini") ||
                lower.contains("moffice") ||
                lower.contains("onlyoffice") ||
                lower.contains("pdfviewer") ||
                lower.contains("reader") ||
                lower.contains("coursera") ||
                lower.contains("duolingo")
    }

    fun isGoogleNerdApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || !prefs.isGoogleNerdAppsEnabled) return false
        val lower = packageName.lowercase()

        if (prefs.excludedGooglePackages.any { it.equals(lower, ignoreCase = true) }) {
            return false
        }

        return lower.startsWith("com.google.") ||
                lower == "com.android.chrome" ||
                lower.contains("google")
    }

    fun isNighttimeSleepy(): Boolean {
        if (!prefs.isSleepinessEnabled) return false
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY) // 0 to 23
        val startHour = prefs.sleepyStartHour
        // From startHour (e.g. 22:00) until 06:00 in the morning
        return hour >= startHour || hour < 6
    }

    fun reset() {
        instagramSessionStartMs = 0L
        handler.removeCallbacks(angerCheckRunnable)
        currentMood = PixelPetView.PetMood.IDLE
    }

    fun simulateMood(mood: PixelPetView.PetMood, reason: String) {
        updateMood(mood, reason)
    }
}
