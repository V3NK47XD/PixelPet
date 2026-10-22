package com.dynamicisland.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.app.databinding.ActivityMainBinding
import com.dynamicisland.app.databinding.DialogDevToolsBinding
import com.dynamicisland.app.util.BatteryOptimizationHelper
import com.dynamicisland.app.util.ForegroundAppDetector
import com.dynamicisland.app.util.OverlayPermissionHelper
import com.dynamicisland.app.util.PreferencesHelper
import com.dynamicisland.app.view.PixelPetView
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesHelper

    private val prefWriteHandler = Handler(Looper.getMainLooper())
    private val pendingWrites = mutableMapOf<String, Runnable>()

    private fun debouncePrefWrite(key: String, delayMs: Long = 250L, writeAction: () -> Unit) {
        pendingWrites[key]?.let { prefWriteHandler.removeCallbacks(it) }
        val runnable = Runnable {
            writeAction()
            pendingWrites.remove(key)
        }
        pendingWrites[key] = runnable
        prefWriteHandler.postDelayed(runnable, delayMs)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Handled silently
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesHelper(this)

        setupUI()
        setupListeners()
        requestNotificationPermissionIfNeeded()

        checkAndHandlePermissions(autoLaunch = true)
    }

    override fun onResume() {
        super.onResume()
        checkAndHandlePermissions(autoLaunch = false)
    }

    private fun setupUI() {
        // 1. Configure Rotary Knobs Deck (Compact Zero-Scroll Dials)
        binding.knobCameraX.apply {
            minValue = -150f
            maxValue = 150f
            stepSize = 1f
            unitSuffix = "dp"
            knobTitle = "CAM X"
            value = prefs.cameraRefX.toFloat()
            onValueChanged = { newVal, fromUser ->
                val intVal = newVal.toInt()
                if (fromUser) {
                    debouncePrefWrite("cameraRefX") {
                        prefs.cameraRefX = intVal
                        notifySettingsChanged()
                    }
                }
            }
        }

        binding.knobCameraY.apply {
            minValue = 0f
            maxValue = 120f
            stepSize = 1f
            unitSuffix = "dp"
            knobTitle = "CAM Y"
            value = prefs.cameraRefY.toFloat()
            onValueChanged = { newVal, fromUser ->
                val intVal = newVal.toInt()
                if (fromUser) {
                    debouncePrefWrite("cameraRefY") {
                        prefs.cameraRefY = intVal
                        notifySettingsChanged()
                    }
                }
            }
        }

        binding.knobPetSize.apply {
            minValue = 32f
            maxValue = 96f
            stepSize = 2f
            unitSuffix = "dp"
            knobTitle = "SIZE"
            value = prefs.petSizeDp.toFloat()
            onValueChanged = { newVal, fromUser ->
                val intVal = newVal.toInt()
                if (fromUser) {
                    debouncePrefWrite("petSizeDp") {
                        prefs.petSizeDp = intVal
                        notifySettingsChanged()
                    }
                }
            }
        }

        binding.knobLeash.apply {
            minValue = 18f
            maxValue = 60f
            stepSize = 2f
            unitSuffix = "dp"
            knobTitle = "LEASH"
            value = prefs.leashLengthDp.toFloat()
            onValueChanged = { newVal, fromUser ->
                val intVal = newVal.toInt()
                if (fromUser) {
                    debouncePrefWrite("leashLengthDp") {
                        prefs.leashLengthDp = intVal
                        notifySettingsChanged()
                    }
                }
            }
        }

        // 2. Camera alignment preset
        updateAlignmentButtonsUI(prefs.cameraAlignment)

        // 3. Feature Switches
        binding.switchGyroPhysics.isChecked = prefs.isGyroPhysicsEnabled
        binding.switchSleepiness.isChecked = prefs.isSleepinessEnabled
        binding.switchTouchReactions.isChecked = prefs.isTouchReactionEnabled

        // 4. Preview Pet in Tether Stage
        binding.previewTetherStage.petView.setAnimationIntensity(prefs.animationIntensity)
        binding.previewTetherStage.petView.setMood(PixelPetView.PetMood.IDLE)
        binding.previewTetherStage.updateLeashColor()
    }

    private fun setupListeners() {
        // Preview pet tap reaction
        binding.previewTetherStage.petView.setOnClickListener {
            binding.previewTetherStage.petView.handleUserTap()
        }

        binding.btnTestDrift.setOnClickListener {
            binding.previewTetherStage.pickNextFloatingSpot()
        }

        // Master Switch
        binding.switchPetMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startPetOverlay() else stopPetOverlay()
        }

        // Developer / Advanced Tools Bottom Sheet
        binding.btnDevTools.setOnClickListener {
            showDevToolsBottomSheet()
        }

        // Permission Quick Fix
        binding.btnQuickFixPermission.setOnClickListener {
            if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
                OverlayPermissionHelper.requestOverlayPermission(this)
            } else if (!ForegroundAppDetector.hasUsageStatsPermission(this)) {
                ForegroundAppDetector.openUsageAccessSettings(this)
            } else if (!BatteryOptimizationHelper.isBatteryOptimizationIgnored(this)) {
                BatteryOptimizationHelper.requestIgnoreBatteryOptimization(this)
            }
        }

        // Mood preview chips
        binding.chipMoodIdle.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.IDLE, "😴 Idle • Floating near camera")
        }
        binding.chipMoodHappy.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.HAPPY, "😄 Happy • Joyful hops and sparkles")
        }
        binding.chipMoodSad.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.SAD, "🥺 Sad • Crying teardrop & frown")
        }
        binding.chipMoodAngry.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.ANGRY, "😡 Angry • Rage vibration & steam")
        }
        binding.chipMoodNerd.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.NERD, "🤓 Nerd • Glasses drop & scanning")
        }
        binding.chipMoodTexting.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.TEXTING, "💬 Texting • Speech bubble •••")
        }

        // Camera Alignment Presets
        binding.btnAlignLeft.setOnClickListener { setCameraAlignment("LEFT") }
        binding.btnAlignCenter.setOnClickListener { setCameraAlignment("CENTER") }
        binding.btnAlignRight.setOnClickListener { setCameraAlignment("RIGHT") }

        // Leash Color Selector Buttons
        binding.btnLeashRed.setOnClickListener { setLeashColor("#F43F5E") }
        binding.btnLeashGold.setOnClickListener { setLeashColor("#F59E0B") }
        binding.btnLeashSilver.setOnClickListener { setLeashColor("#E2E8F0") }
        binding.btnLeashMint.setOnClickListener { setLeashColor("#34D399") }

        // Behavior Switches
        binding.switchGyroPhysics.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGyroPhysicsEnabled = isChecked
            notifySettingsChanged()
        }

        binding.switchSleepiness.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSleepinessEnabled = isChecked
            notifySettingsChanged()
        }

        binding.switchTouchReactions.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTouchReactionEnabled = isChecked
            notifySettingsChanged()
        }
    }

    private fun showDevToolsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogDevToolsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Bind Developer Settings
        sheetBinding.switchTouchDebug.isChecked = prefs.isTouchDebugEnabled
        sheetBinding.switchTouchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTouchDebugEnabled = isChecked
            binding.previewTetherStage.invalidate()
            notifySettingsChanged()
        }

        sheetBinding.switchHideAndSeek.isChecked = prefs.isHideAndSeekEnabled
        sheetBinding.switchHideAndSeek.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHideAndSeekEnabled = isChecked
            notifySettingsChanged()
        }

        sheetBinding.switchGoogleNerd.isChecked = prefs.isGoogleNerdAppsEnabled
        sheetBinding.switchGoogleNerd.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGoogleNerdAppsEnabled = isChecked
            notifySettingsChanged()
        }

        sheetBinding.switchPhoneEvents.isChecked = prefs.isPhoneEventsEnabled
        sheetBinding.switchPhoneEvents.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPhoneEventsEnabled = isChecked
        }

        // Anger Timer Dial
        sheetBinding.knobAngerTimer.apply {
            minValue = 1f
            maxValue = 15f
            stepSize = 1f
            unitSuffix = "m"
            knobTitle = "ANGER"
            value = prefs.instagramAngerMinutes.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    debouncePrefWrite("angerMinutes") {
                        prefs.instagramAngerMinutes = newVal.toInt()
                    }
                }
            }
        }

        // Nerd Apps Checkboxes
        val nerdApps = prefs.nerdApps
        sheetBinding.cbChatGPT.isChecked = nerdApps.contains("com.openai.chatgpt")
        sheetBinding.cbGemini.isChecked = nerdApps.contains("com.google.android.apps.bard")
        sheetBinding.cbWPS.isChecked = nerdApps.contains("cn.wps.moffice_eng") || nerdApps.contains("com.onlyoffice.documents")
        sheetBinding.cbPDF.isChecked = nerdApps.contains("com.adobe.reader")

        val updateNerdApps = {
            val apps = mutableSetOf<String>()
            if (sheetBinding.cbChatGPT.isChecked) apps.add("com.openai.chatgpt")
            if (sheetBinding.cbGemini.isChecked) apps.add("com.google.android.apps.bard")
            if (sheetBinding.cbWPS.isChecked) {
                apps.add("cn.wps.moffice_eng")
                apps.add("cn.wps.moffice")
                apps.add("com.onlyoffice.documents")
            }
            if (sheetBinding.cbPDF.isChecked) {
                apps.add("com.adobe.reader")
                apps.add("com.google.android.apps.pdfviewer")
            }
            prefs.nerdApps = apps
        }
        sheetBinding.cbChatGPT.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheetBinding.cbGemini.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheetBinding.cbWPS.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheetBinding.cbPDF.setOnCheckedChangeListener { _, _ -> updateNerdApps() }

        // Action Buttons
        sheetBinding.btnOpenActiveLog.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DebugActivity::class.java))
        }

        sheetBinding.btnUnlockRestricted.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("Unlock 'Restricted Settings'")
                .setMessage(
                    "Android restricts sensitive settings on sideloaded apps by default.\n\n" +
                            "To unlock in 5 seconds:\n" +
                            "1. Tap 'Open App Info' below.\n" +
                            "2. Tap the 3 dots (⋮) in the top-right corner.\n" +
                            "3. Tap 'Allow restricted settings' (confirm fingerprint/PIN).\n" +
                            "4. Return here and tap 'Grant Access'!"
                )
                .setPositiveButton("Open App Info") { _, _ ->
                    ForegroundAppDetector.openAppDetailsForRestrictedSettings(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        sheetBinding.btnCloseDev.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setPreviewMood(mood: PixelPetView.PetMood, description: String) {
        binding.previewTetherStage.petView.setMood(mood)
        binding.tvPreviewMoodDesc.text = description
    }

    private fun setLeashColor(colorHex: String) {
        prefs.leashColor = colorHex
        binding.previewTetherStage.updateLeashColor()
        notifySettingsChanged()
    }

    private fun setCameraAlignment(alignment: String) {
        prefs.cameraAlignment = alignment
        when (alignment) {
            "LEFT" -> {
                prefs.cameraRefX = -110
                prefs.cameraRefY = 24
            }
            "RIGHT" -> {
                prefs.cameraRefX = 110
                prefs.cameraRefY = 24
            }
            else -> {
                prefs.cameraRefX = 0
                prefs.cameraRefY = 22
            }
        }
        binding.knobCameraX.value = prefs.cameraRefX.toFloat()
        binding.knobCameraY.value = prefs.cameraRefY.toFloat()

        updateAlignmentButtonsUI(alignment)
        notifySettingsChanged()
    }

    private fun updateAlignmentButtonsUI(alignment: String) {
        val selectedColor = ContextCompat.getColor(this, R.color.peach_primary)
        val defaultColor = ContextCompat.getColor(this, R.color.peach_surface)

        val selectedText = ContextCompat.getColor(this, R.color.background_dark)
        val defaultText = ContextCompat.getColor(this, R.color.text_primary)

        binding.btnAlignLeft.setBackgroundColor(if (alignment == "LEFT") selectedColor else defaultColor)
        binding.btnAlignLeft.setTextColor(if (alignment == "LEFT") selectedText else defaultText)

        binding.btnAlignCenter.setBackgroundColor(if (alignment == "CENTER") selectedColor else defaultColor)
        binding.btnAlignCenter.setTextColor(if (alignment == "CENTER") selectedText else defaultText)

        binding.btnAlignRight.setBackgroundColor(if (alignment == "RIGHT") selectedColor else defaultColor)
        binding.btnAlignRight.setTextColor(if (alignment == "RIGHT") selectedText else defaultText)
    }

    private fun notifySettingsChanged() {
        if (prefs.isIslandEnabled && OverlayPermissionHelper.hasOverlayPermission(this)) {
            DynamicIslandService.updateSettings(this)
        }
    }

    private fun checkAndHandlePermissions(autoLaunch: Boolean) {
        val hasOverlay = OverlayPermissionHelper.hasOverlayPermission(this)
        val isBatteryUnrestricted = BatteryOptimizationHelper.isBatteryOptimizationIgnored(this)
        val hasUsage = ForegroundAppDetector.hasUsageStatsPermission(this)

        val hasMissingPermissions = !hasOverlay || !isBatteryUnrestricted || !hasUsage
        binding.cardPermissionAlert.visibility = if (hasMissingPermissions) View.VISIBLE else View.GONE

        // 1. Overlay Status
        if (hasOverlay) {
            binding.tvOverlayBadge.text = "Overlay: 🟢"
            binding.tvOverlayBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))

            if (prefs.isIslandEnabled) {
                DynamicIslandService.start(this)
                updateMasterSwitchUI(true)
            } else {
                if (prefs.hasAutoRequestedPermission) {
                    startPetOverlay()
                } else {
                    updateMasterSwitchUI(false)
                }
            }
        } else {
            binding.tvOverlayBadge.text = "Overlay: ⚠️"
            binding.tvOverlayBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
            updateMasterSwitchUI(false)

            if (autoLaunch && !prefs.hasAutoRequestedPermission) {
                prefs.hasAutoRequestedPermission = true
                binding.root.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        OverlayPermissionHelper.requestOverlayPermission(this)
                    }
                }, 350)
            }
        }

        // 2. Battery Status
        if (isBatteryUnrestricted) {
            binding.tvBatteryBadge.text = "Battery: ⚡"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            binding.tvBatteryBadge.text = "Battery: ⚠️"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }

        // 3. Usage Access Status
        if (hasUsage) {
            binding.tvUsageBadge.text = "Usage: 🟢"
            binding.tvUsageBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            ForegroundAppDetector.startMonitoring(this)
        } else {
            binding.tvUsageBadge.text = "Usage: ⚠️"
            binding.tvUsageBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))

            if (hasOverlay && (!prefs.hasAutoRequestedUsage || autoLaunch)) {
                prefs.hasAutoRequestedUsage = true
                binding.root.postDelayed({
                    if (!isFinishing && !isDestroyed && !ForegroundAppDetector.hasUsageStatsPermission(this)) {
                        ForegroundAppDetector.openUsageAccessSettings(this)
                    }
                }, 600)
            }
        }
    }

    private fun startPetOverlay() {
        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            OverlayPermissionHelper.requestOverlayPermission(this)
            binding.switchPetMaster.isChecked = false
            return
        }

        prefs.isIslandEnabled = true
        DynamicIslandService.start(this)
        updateMasterSwitchUI(true)
    }

    private fun stopPetOverlay() {
        prefs.isIslandEnabled = false
        DynamicIslandService.stop(this)
        updateMasterSwitchUI(false)
    }

    private fun updateMasterSwitchUI(isActive: Boolean) {
        binding.switchPetMaster.setOnCheckedChangeListener(null)
        binding.switchPetMaster.isChecked = isActive
        binding.switchPetMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startPetOverlay() else stopPetOverlay()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        prefWriteHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
