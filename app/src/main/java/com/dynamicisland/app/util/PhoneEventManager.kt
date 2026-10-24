package com.dynamicisland.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Monitors system phone events:
 * - Phone unlocked (ACTION_USER_PRESENT)
 * - Power connected / charging (ACTION_POWER_CONNECTED)
 * - Power disconnected (ACTION_POWER_DISCONNECTED)
 * - Battery level changes (ACTION_BATTERY_CHANGED)
 */
class PhoneEventManager(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onPhoneUnlocked()
        fun onPowerConnected()
        fun onPowerDisconnected()
        fun onBatteryLevelChanged(levelPercent: Int, isLow: Boolean, isCritical: Boolean)
        fun onScreenTurnedOff()
        fun onScreenTurnedOn()
    }

    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    listener.onPhoneUnlocked()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    listener.onPowerConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    listener.onPowerDisconnected()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    listener.onScreenTurnedOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    listener.onScreenTurnedOn()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (level >= 0 && scale > 0) {
                        val batteryPct = ((level / scale.toFloat()) * 100).toInt()
                        val isCritical = batteryPct <= 5 && !isCharging
                        val isLow = batteryPct <= 15 && !isCharging

                        listener.onBatteryLevelChanged(batteryPct, isLow, isCritical)
                    }
                }
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRegistered = false
    }
}
