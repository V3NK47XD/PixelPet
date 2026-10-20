package com.dynamicisland.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object OverlayPermissionHelper {

    /**
     * Checks if the app currently has permission to draw overlays over other apps.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Opens system settings to request "Display over other apps" (overlay) permission.
     * Uses direct package URI when possible, with safe fallback to general overlay list.
     */
    fun requestOverlayPermission(activity: Activity) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback for devices/ROMs where package Uri throws SecurityException or ActivityNotFoundException
            try {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(
                    activity,
                    "Unable to open overlay settings automatically. Please grant from device settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
