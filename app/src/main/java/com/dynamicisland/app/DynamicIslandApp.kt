package com.dynamicisland.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class DynamicIslandApp : Application() {

    companion object {
        const val CHANNEL_ID = "dynamic_island_overlay_channel"
    }

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        createNotificationChannel()
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DynamicIsland", "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash_trace", Log.getStackTraceString(throwable))
                    .commit()
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
