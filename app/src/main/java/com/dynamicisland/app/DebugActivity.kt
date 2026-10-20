package com.dynamicisland.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.app.databinding.ActivityDebugBinding
import com.dynamicisland.app.util.ForegroundAppDetector

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding

    private val logListener: (ForegroundAppDetector.AppLogEntry) -> Unit = {
        runOnUiThread {
            refreshDebugView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        ForegroundAppDetector.addLogListener(logListener)
        ForegroundAppDetector.startMonitoring(this)
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndRefresh()
    }

    override fun onDestroy() {
        ForegroundAppDetector.removeLogListener(logListener)
        super.onDestroy()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnGrantUsage.setOnClickListener {
            ForegroundAppDetector.openUsageAccessSettings(this)
        }

        binding.btnClearLog.setOnClickListener {
            ForegroundAppDetector.clearLogs()
            refreshDebugView()
        }

        binding.btnSimulateInstagram.setOnClickListener {
            ForegroundAppDetector.recordManualSimulate(
                packageName = "com.instagram.android",
                isInstagram = true,
                note = "Simulated via Debug Page -> Sad Face triggered"
            )
            DynamicIslandService.updateInstagramState(this, true)
        }

        binding.btnSimulateHome.setOnClickListener {
            ForegroundAppDetector.recordManualSimulate(
                packageName = "com.android.launcher",
                isInstagram = false,
                note = "Simulated via Debug Page -> Happy Face restored"
            )
            DynamicIslandService.updateInstagramState(this, false)
        }
    }

    private fun checkPermissionAndRefresh() {
        val hasPermission = ForegroundAppDetector.hasUsageStatsPermission(this)
        if (hasPermission) {
            binding.cardDebugPermission.visibility = View.GONE
            binding.tvPollingBadge.text = "500ms Active"
            binding.tvPollingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            binding.cardDebugPermission.visibility = View.VISIBLE
            binding.tvPollingBadge.text = "Permission Required"
            binding.tvPollingBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }
        refreshDebugView()
    }

    private fun refreshDebugView() {
        // 1. Current App Info
        val currentPkg = ForegroundAppDetector.currentForegroundPackage
        val currentLabel = ForegroundAppDetector.currentAppLabel
        val isIg = ForegroundAppDetector.isInstagramActive

        if (currentPkg.isNotBlank()) {
            binding.tvCurrentAppLabel.text = currentLabel
            binding.tvCurrentPackage.text = currentPkg

            if (isIg) {
                binding.tvLiveMoodBadge.text = "🥺 Sad Mode (Instagram)"
                binding.tvLiveMoodBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
            } else {
                binding.tvLiveMoodBadge.text = "😊 Happy Mode"
                binding.tvLiveMoodBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            }
        } else {
            binding.tvCurrentAppLabel.text = "Detecting active app..."
            binding.tvCurrentPackage.text = "Waiting for window change..."
        }

        // 2. Event Logs
        val logs = ForegroundAppDetector.getLogs()
        binding.tvLogCount.text = "${logs.size} events"

        if (logs.isEmpty()) {
            binding.tvConsoleLogs.text = "No foreground events captured yet.\nGrant Usage Access and switch apps to see real-time detection logs."
        } else {
            val sb = StringBuilder()
            logs.takeLast(60).reversed().forEach { entry ->
                sb.append(entry.displayText()).append("\n\n")
            }
            binding.tvConsoleLogs.text = sb.toString().trimEnd()
        }
    }
}
