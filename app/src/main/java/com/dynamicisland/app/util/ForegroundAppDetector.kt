package com.dynamicisland.app.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Manages foreground app detection using Android UsageStatsManager
 * and maintains a real-time event log for debugging.
 */
object ForegroundAppDetector {

    data class AppLogEntry(
        val timestamp: Long,
        val packageName: String,
        val appLabel: String,
        val isInstagram: Boolean,
        val note: String
    ) {
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun displayText(): String {
            val moodTag = if (isInstagram) "🥺 SAD (Instagram)" else "😊 HAPPY"
            return "[${formattedTime()}] $appLabel ($packageName)\n  └─ State: $moodTag • $note"
        }
    }

    private val listeners = mutableListOf<(packageName: String, isInstagram: Boolean) -> Unit>()
    private val logListeners = mutableListOf<(AppLogEntry) -> Unit>()

    private val logs = Collections.synchronizedList(mutableListOf<AppLogEntry>())
    private const val MAX_LOGS = 200

    @Volatile
    var currentForegroundPackage: String = ""
        private set

    @Volatile
    var currentAppLabel: String = "Unknown"
        private set

    @Volatile
    var isInstagramActive: Boolean = false
        private set

    private var isMonitoring = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val pollIntervalMs = 500L

    fun isInstagram(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.instagram.android" ||
                lower == "com.instagram.lite" ||
                lower == "com.instagram.barcelona" ||
                lower.contains("instagram")
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Opens the App Info page for Dynamic Island.
     * On Android 13/14, sideloaded apps show "Restricted setting" until the user
     * taps the 3 dots (⋮) in the top right of this App Info page and selects "Allow restricted settings".
     */
    fun openAppDetailsForRestrictedSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListener(listener: (packageName: String, isInstagram: Boolean) -> Unit) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: (packageName: String, isInstagram: Boolean) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun addLogListener(listener: (AppLogEntry) -> Unit) {
        synchronized(logListeners) {
            if (!logListeners.contains(listener)) {
                logListeners.add(listener)
            }
        }
    }

    fun removeLogListener(listener: (AppLogEntry) -> Unit) {
        synchronized(logListeners) {
            logListeners.remove(listener)
        }
    }

    fun getLogs(): List<AppLogEntry> {
        synchronized(logs) {
            return ArrayList(logs)
        }
    }

    fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
    }

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        addLog(
            AppLogEntry(
                timestamp = System.currentTimeMillis(),
                packageName = context.packageName,
                appLabel = "Dynamic Island",
                isInstagram = false,
                note = "Foreground monitoring started (500ms polling)"
            )
        )
        handler.post(createPollingRunnable(context.applicationContext))
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun createPollingRunnable(appContext: Context): Runnable {
        return object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                checkForegroundApp(appContext)
                handler.postDelayed(this, pollIntervalMs)
            }
        }
    }

    fun checkForegroundApp(context: Context) {
        if (!hasUsageStatsPermission(context)) return

        val detectedPkg = detectTopPackage(context) ?: return
        if (detectedPkg.isBlank() || detectedPkg == currentForegroundPackage) return

        val prevPkg = currentForegroundPackage
        currentForegroundPackage = detectedPkg
        val isIg = isInstagram(detectedPkg)
        isInstagramActive = isIg

        val label = getAppLabel(context, detectedPkg)
        currentAppLabel = label

        val note = if (isIg) {
            "Instagram opened! Triggering Sad face"
        } else if (isInstagram(prevPkg)) {
            "Exited Instagram. Restoring Happy face"
        } else {
            "Active window changed"
        }

        val entry = AppLogEntry(
            timestamp = System.currentTimeMillis(),
            packageName = detectedPkg,
            appLabel = label,
            isInstagram = isIg,
            note = note
        )

        addLog(entry)

        // Notify listeners on UI thread
        handler.post {
            synchronized(listeners) {
                listeners.forEach { it.invoke(detectedPkg, isIg) }
            }
        }
    }

    fun recordManualSimulate(packageName: String, isInstagram: Boolean, note: String) {
        currentForegroundPackage = packageName
        currentAppLabel = if (isInstagram) "Instagram (Simulated)" else "Home (Simulated)"
        isInstagramActive = isInstagram

        val entry = AppLogEntry(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            appLabel = currentAppLabel,
            isInstagram = isInstagram,
            note = note
        )
        addLog(entry)

        handler.post {
            synchronized(listeners) {
                listeners.forEach { it.invoke(packageName, isInstagram) }
            }
        }
    }

    private fun addLog(entry: AppLogEntry) {
        synchronized(logs) {
            if (logs.size >= MAX_LOGS) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
        handler.post {
            synchronized(logListeners) {
                logListeners.forEach { it.invoke(entry) }
            }
        }
    }

    private fun detectTopPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val startTime = now - 25000 // Look back 25s

        val events = usageStatsManager.queryEvents(startTime, now) ?: return null
        var lastResumedPackage: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isResumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isResumed && !event.packageName.isNullOrBlank()) {
                lastResumedPackage = event.packageName
            }
        }

        if (lastResumedPackage != null) {
            return lastResumedPackage
        }

        // Secondary fallback via queryUsageStats
        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60000,
                now
            )
            val mostRecent = stats?.maxByOrNull { it.lastTimeUsed }
            if (mostRecent != null && mostRecent.lastTimeUsed > 0) {
                return mostRecent.packageName
            }
        } catch (e: Exception) {
            // Ignored
        }

        return null
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                packageName.contains("instagram") -> "Instagram"
                packageName.contains("whatsapp") -> "WhatsApp"
                packageName.contains("youtube") -> "YouTube"
                packageName.contains("chrome") -> "Chrome"
                packageName.contains("launcher") -> "Home Launcher"
                else -> packageName.substringAfterLast('.')
            }
        }
    }
}
