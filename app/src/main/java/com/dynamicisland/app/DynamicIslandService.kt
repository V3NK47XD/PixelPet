package com.dynamicisland.app

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dynamicisland.app.util.OverlayPermissionHelper
import com.dynamicisland.app.util.PreferencesHelper

/**
 * Persistent Foreground Service responsible for hosting the floating Dynamic Island
 * overlay over other apps using Android WindowManager.
 */
class DynamicIslandService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.dynamicisland.app.ACTION_START"
        const val ACTION_STOP = "com.dynamicisland.app.ACTION_STOP"
        const val ACTION_UPDATE_SETTINGS = "com.dynamicisland.app.ACTION_UPDATE_SETTINGS"
        const val ACTION_UPDATE_INSTAGRAM = "com.dynamicisland.app.ACTION_UPDATE_INSTAGRAM"
        const val EXTRA_IS_INSTAGRAM = "extra_is_instagram"
        fun start(context: Context) {
            val intent = Intent(context, DynamicIslandService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                try { context.startService(intent) } catch (ex: Exception) {}
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DynamicIslandService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateSettings(context: Context) {
            val intent = Intent(context, DynamicIslandService::class.java).apply {
                action = ACTION_UPDATE_SETTINGS
            }
            context.startService(intent)
        }

        fun updateInstagramState(context: Context, isInstagram: Boolean) {
            val intent = Intent(context, DynamicIslandService::class.java).apply {
                action = ACTION_UPDATE_INSTAGRAM
                putExtra(EXTRA_IS_INSTAGRAM, isInstagram)
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var stageView: com.dynamicisland.app.view.TetheredPetStageView? = null
    private var petView: com.dynamicisland.app.view.PixelPetView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private lateinit var prefs: PreferencesHelper
    private lateinit var moodManager: com.dynamicisland.app.util.PetMoodManager
    private var phoneEventManager: com.dynamicisland.app.util.PhoneEventManager? = null
    private val appChangeListener: (String, Boolean) -> Unit = { packageName, _ ->
        moodManager.onForegroundPackageChanged(packageName)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)
        moodManager = com.dynamicisland.app.util.PetMoodManager(this)
        moodManager.onMoodChanged = { mood, _ ->
            petView?.setMood(mood)
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        phoneEventManager = com.dynamicisland.app.util.PhoneEventManager(this, object : com.dynamicisland.app.util.PhoneEventManager.Listener {
            override fun onPhoneUnlocked() {
                if (!prefs.isPhoneEventsEnabled) return
                petView?.triggerExcitedHop()
                moodManager.recalculateMood()
            }

            override fun onPowerConnected() {
                if (!prefs.isPhoneEventsEnabled) return
                moodManager.onPowerConnected()
            }

            override fun onPowerDisconnected() {
                if (!prefs.isPhoneEventsEnabled) return
                moodManager.onPowerDisconnected()
            }

            override fun onBatteryLevelChanged(levelPercent: Int, isLow: Boolean, isCritical: Boolean) {
                if (!prefs.isPhoneEventsEnabled) return
                moodManager.onBatteryLevelChanged(levelPercent, isLow, isCritical)
            }
        })
        phoneEventManager?.start()

        com.dynamicisland.app.util.ForegroundAppDetector.addListener(appChangeListener)
        com.dynamicisland.app.util.ForegroundAppDetector.startMonitoring(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeOverlay()
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    // Ignored
                }
                stopSelf()
                prefs.isIslandEnabled = false
                return START_NOT_STICKY
            }
            ACTION_UPDATE_SETTINGS -> {
                applyUpdatedSettings()
            }
            ACTION_UPDATE_INSTAGRAM -> {
                val isIg = intent.getBooleanExtra(EXTRA_IS_INSTAGRAM, false)
                if (isIg) {
                    moodManager.simulateMood(com.dynamicisland.app.view.PixelPetView.PetMood.SAD, "Instagram Triggered")
                } else {
                    moodManager.simulateMood(com.dynamicisland.app.view.PixelPetView.PetMood.HAPPY, "Normal Triggered")
                }
            }
            ACTION_START, null -> {
                if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                tryStartForegroundNotification()
                showOverlay()
                prefs.isIslandEnabled = true
                petView?.setMood(moodManager.currentMood)
            }
        }
        return START_STICKY
    }

    private fun tryStartForegroundNotification() {
        try {
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(this, DynamicIslandService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, DynamicIslandApp.CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPendingIntent)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Safe fallback: Service continues running as standard service if foreground not allowed
            e.printStackTrace()
        }
    }

    private fun showOverlay() {
        if (stageView != null) return

        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            return
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val stageWidth = (prefs.leashLengthDp * 2 + prefs.petSizeDp + 36).dpToPx()
        val stageHeight = (prefs.leashLengthDp + prefs.petSizeDp + 24).dpToPx()

        val touchFlag = if (prefs.isTouchReactionEnabled) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                touchFlag

        layoutParams = WindowManager.LayoutParams(
            stageWidth,
            stageHeight,
            overlayType,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = prefs.cameraRefX.dpToPx()
            y = (prefs.cameraRefY - 8).dpToPx()
        }

        val stage = com.dynamicisland.app.view.TetheredPetStageView(this)
        stageView = stage
        petView = stage.petView
        petView?.setMood(moodManager.currentMood)

        try {
            windowManager?.addView(stage, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyUpdatedSettings() {
        layoutParams?.let { params ->
            val stageWidth = (prefs.leashLengthDp * 2 + prefs.petSizeDp + 36).dpToPx()
            val stageHeight = (prefs.leashLengthDp + prefs.petSizeDp + 24).dpToPx()
            params.width = stageWidth
            params.height = stageHeight
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = prefs.cameraRefX.dpToPx()
            params.y = (prefs.cameraRefY - 8).dpToPx()

            val touchFlag = if (prefs.isTouchReactionEnabled) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    touchFlag

            stageView?.updateLeashColor()
            petView?.setAnimationIntensity(prefs.animationIntensity)
            moodManager.recalculateMood()

            if (stageView?.isAttachedToWindow == true) {
                windowManager?.updateViewLayout(stageView, params)
            }
        }
    }

    private fun removeOverlay() {
        stageView?.let { view ->
            try {
                view.stopDriftEngine()
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stageView = null
            petView = null
        }
    }

    override fun onDestroy() {
        phoneEventManager?.stop()
        com.dynamicisland.app.util.ForegroundAppDetector.removeListener(appChangeListener)
        removeOverlay()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (prefs.isIslandEnabled && OverlayPermissionHelper.hasOverlayPermission(this)) {
            try {
                val restartIntent = Intent(applicationContext, DynamicIslandService::class.java).apply {
                    action = ACTION_START
                }
                val restartPendingIntent = PendingIntent.getService(
                    applicationContext,
                    101,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 800L,
                    restartPendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Number.dpToPx(): Int =
        (this.toFloat() * resources.displayMetrics.density).toInt()
}
