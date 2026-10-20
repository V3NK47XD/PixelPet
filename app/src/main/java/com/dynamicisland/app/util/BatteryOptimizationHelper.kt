package com.dynamicisland.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object BatteryOptimizationHelper {

    /**
     * Checks if the app is currently exempt from battery optimizations (Unrestricted).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    /**
     * Automatically requests exemption from battery optimizations.
     * Launches the system direct prompt dialog ("Allow app to always run in background?"),
     * with graceful fallbacks to battery settings or app details.
     */
    fun requestIgnoreBatteryOptimization(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Direct system dialog prompt
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                // Fallback 1: Battery optimization settings list
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    // Fallback 2: App details settings screen
                    try {
                        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(detailsIntent)
                    } catch (finalEx: Exception) {
                        Toast.makeText(
                            activity,
                            "Please set Battery usage to 'Unrestricted' in App Settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
