package com.dynamicisland.app.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ISLAND_ENABLED = "island_enabled"
        private const val KEY_VERTICAL_OFFSET = "vertical_offset"
        private const val KEY_MOOD = "smiley_mood"
        private const val KEY_SIZE_SCALE = "size_scale"
        private const val KEY_AUTO_REQUESTED = "auto_requested_permission"
        private const val KEY_AUTO_REQUESTED_BATTERY = "auto_requested_battery"
        private const val KEY_AUTO_REQUESTED_USAGE = "auto_requested_usage"

        private const val KEY_PET_SIZE_DP = "pet_size_dp"
        private const val KEY_PET_OFFSET_X = "pet_offset_x"
        private const val KEY_PET_OFFSET_Y = "pet_offset_y"
        private const val KEY_CAMERA_ALIGNMENT = "camera_alignment"
        private const val KEY_ANGER_MINUTES = "anger_minutes"
        private const val KEY_ANIMATION_INTENSITY = "animation_intensity"
        private const val KEY_NERD_APPS = "nerd_apps_list"
        private const val KEY_GOOGLE_NERD_ENABLED = "google_nerd_enabled"
        private const val KEY_EXCLUDED_GOOGLE_APPS = "excluded_google_apps"

        private const val KEY_CAMERA_REF_X = "camera_ref_x"
        private const val KEY_CAMERA_REF_Y = "camera_ref_y"
        private const val KEY_DYNAMIC_MOVEMENT = "dynamic_movement_enabled"
        private const val KEY_HIDE_AND_SEEK = "hide_and_seek_enabled"
        private const val KEY_TOUCH_REACTION = "touch_reaction_enabled"
        private const val KEY_SLEEPINESS_ENABLED = "sleepiness_enabled"
        private const val KEY_SLEEPY_START_HOUR = "sleepy_start_hour"
        private const val KEY_PHONE_EVENTS_ENABLED = "phone_events_enabled"
        private const val KEY_LEASH_LENGTH_DP = "leash_length_dp"
        private const val KEY_LEASH_COLOR = "leash_color"
        private const val KEY_TOUCH_DEBUG_ENABLED = "touch_debug_enabled"
        private const val KEY_GYRO_PHYSICS_ENABLED = "gyro_physics_enabled"
        private const val KEY_ORIENTATION_CALIBRATED = "orientation_calibrated"
        private const val KEY_CALIBRATED_ACCEL_X = "calibrated_accel_x"
        private const val KEY_CALIBRATED_ACCEL_Y = "calibrated_accel_y"
        private const val KEY_CALIBRATED_ACCEL_Z = "calibrated_accel_z"
        private const val KEY_PET_NAME = "pet_name"
        private const val KEY_ANGER_DURATION_SECONDS = "anger_duration_seconds"
        private const val KEY_SOUND_ENABLED = "sound_master_enabled"
        private const val KEY_SOUND_VOLUME = "sound_master_volume"
        private const val KEY_SOUND_TAP_ENABLED = "sound_tap_enabled"
        private const val KEY_SOUND_PUNCH_ENABLED = "sound_punch_enabled"
        private const val KEY_SOUND_ANGER_ENABLED = "sound_anger_enabled"
        private const val KEY_LEASH_MOTION_THRESHOLD = "leash_motion_threshold"
        val DEFAULT_NERD_PACKAGES = setOf(
            "cn.wps.moffice_eng",
            "cn.wps.moffice",
            "com.onlyoffice.documents",
            "com.adobe.reader",
            "com.google.android.apps.pdfviewer",
            "org.coursera.android",
            "com.duolingo",
            "com.notion.id"
        )
    }

    var petSizeDp: Int
        get() = prefs.getInt(KEY_PET_SIZE_DP, 54)
        set(value) = prefs.edit().putInt(KEY_PET_SIZE_DP, value).apply()

    var petOffsetX: Int
        get() = prefs.getInt(KEY_PET_OFFSET_X, 0)
        set(value) = prefs.edit().putInt(KEY_PET_OFFSET_X, value).apply()

    var petOffsetY: Int
        get() = prefs.getInt(KEY_PET_OFFSET_Y, 22)
        set(value) = prefs.edit().putInt(KEY_PET_OFFSET_Y, value).apply()

    var cameraAlignment: String
        get() = prefs.getString(KEY_CAMERA_ALIGNMENT, "CENTER") ?: "CENTER"
        set(value) = prefs.edit().putString(KEY_CAMERA_ALIGNMENT, value).apply()

    var instagramAngerMinutes: Int
        get() = prefs.getInt(KEY_ANGER_MINUTES, 5)
        set(value) = prefs.edit().putInt(KEY_ANGER_MINUTES, value).apply()

    var animationIntensity: Float
        get() = prefs.getFloat(KEY_ANIMATION_INTENSITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ANIMATION_INTENSITY, value).apply()

    var nerdApps: Set<String>
        get() = prefs.getStringSet(KEY_NERD_APPS, DEFAULT_NERD_PACKAGES) ?: DEFAULT_NERD_PACKAGES
        set(value) = prefs.edit().putStringSet(KEY_NERD_APPS, value).apply()

    var cameraRefX: Int
        get() = prefs.getInt(KEY_CAMERA_REF_X, 0)
        set(value) = prefs.edit().putInt(KEY_CAMERA_REF_X, value).apply()

    var cameraRefY: Int
        get() = prefs.getInt(KEY_CAMERA_REF_Y, 22)
        set(value) = prefs.edit().putInt(KEY_CAMERA_REF_Y, value).apply()

    var isDynamicMovementEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_MOVEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_MOVEMENT, value).apply()

    var isHideAndSeekEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIDE_AND_SEEK, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_AND_SEEK, value).apply()

    var isTouchReactionEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_REACTION, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_REACTION, value).apply()

    var isSleepinessEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEPINESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SLEEPINESS_ENABLED, value).apply()

    var sleepyStartHour: Int
        get() = prefs.getInt(KEY_SLEEPY_START_HOUR, 22) // 10:00 PM
        set(value) = prefs.edit().putInt(KEY_SLEEPY_START_HOUR, value).apply()

    var isGoogleNerdAppsEnabled: Boolean
        get() = prefs.getBoolean(KEY_GOOGLE_NERD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GOOGLE_NERD_ENABLED, value).apply()

    var excludedGooglePackages: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_GOOGLE_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED_GOOGLE_APPS, value).apply()

    var isPhoneEventsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHONE_EVENTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PHONE_EVENTS_ENABLED, value).apply()

    var leashLengthDp: Int
        get() = prefs.getInt(KEY_LEASH_LENGTH_DP, 48)
        set(value) = prefs.edit().putInt(KEY_LEASH_LENGTH_DP, value).apply()

    var leashColor: String
        get() = prefs.getString(KEY_LEASH_COLOR, "#F43F5E") ?: "#F43F5E"
        set(value) = prefs.edit().putString(KEY_LEASH_COLOR, value).apply()

    var isTouchDebugEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_DEBUG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_DEBUG_ENABLED, value).apply()

    var isGyroPhysicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_GYRO_PHYSICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GYRO_PHYSICS_ENABLED, value).apply()

    var isOrientationCalibrated: Boolean
        get() = prefs.getBoolean(KEY_ORIENTATION_CALIBRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_ORIENTATION_CALIBRATED, value).apply()

    var calibratedAccelX: Float
        get() = prefs.getFloat(KEY_CALIBRATED_ACCEL_X, 0f)
        set(value) = prefs.edit().putFloat(KEY_CALIBRATED_ACCEL_X, value).apply()

    var calibratedAccelY: Float
        get() = prefs.getFloat(KEY_CALIBRATED_ACCEL_Y, 6.8f) // Ergonomic ~44° natural holding angle
        set(value) = prefs.edit().putFloat(KEY_CALIBRATED_ACCEL_Y, value).apply()

    var calibratedAccelZ: Float
        get() = prefs.getFloat(KEY_CALIBRATED_ACCEL_Z, 6.8f)
        set(value) = prefs.edit().putFloat(KEY_CALIBRATED_ACCEL_Z, value).apply()

    var isIslandEnabled: Boolean
        get() = prefs.getBoolean(KEY_ISLAND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ISLAND_ENABLED, value).apply()

    var verticalOffsetY: Int
        get() = prefs.getInt(KEY_VERTICAL_OFFSET, 18)
        set(value) = prefs.edit().putInt(KEY_VERTICAL_OFFSET, value).apply()

    var mood: String
        get() = prefs.getString(KEY_MOOD, "IDLE") ?: "IDLE"
        set(value) = prefs.edit().putString(KEY_MOOD, value).apply()

    var sizeScale: Float
        get() = prefs.getFloat(KEY_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SIZE_SCALE, value).apply()

    var hasAutoRequestedPermission: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REQUESTED, value).apply()

    var hasAutoRequestedBattery: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REQUESTED_BATTERY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REQUESTED_BATTERY, value).apply()

    var hasAutoRequestedUsage: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REQUESTED_USAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REQUESTED_USAGE, value).apply()

    var petName: String
        get() = prefs.getString(KEY_PET_NAME, "Kiddo") ?: "Kiddo"
        set(value) = prefs.edit().putString(KEY_PET_NAME, value).apply()

    var angerDurationSeconds: Int
        get() = prefs.getInt(KEY_ANGER_DURATION_SECONDS, 6) // Default 6s anger cooldown
        set(value) = prefs.edit().putInt(KEY_ANGER_DURATION_SECONDS, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, 0.55f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_VOLUME, value.coerceIn(0f, 1f)).apply()

    var isTapSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_TAP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_TAP_ENABLED, value).apply()

    var isPunchSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_PUNCH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_PUNCH_ENABLED, value).apply()

    var isAngerSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ANGER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ANGER_ENABLED, value).apply()

    var leashMotionThreshold: Float
        get() = prefs.getFloat(KEY_LEASH_MOTION_THRESHOLD, 0.6f)
        set(value) = prefs.edit().putFloat(KEY_LEASH_MOTION_THRESHOLD, value.coerceIn(0.1f, 2.5f)).apply()
}
