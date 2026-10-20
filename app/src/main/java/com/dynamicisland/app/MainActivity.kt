package com.dynamicisland.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.app.databinding.ActivityMainBinding
import com.dynamicisland.app.util.BatteryOptimizationHelper
import com.dynamicisland.app.util.ForegroundAppDetector
import com.dynamicisland.app.util.OverlayPermissionHelper
import com.dynamicisland.app.util.PreferencesHelper
import com.dynamicisland.app.view.PixelPetView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesHelper

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission handled silently
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesHelper(this)

        setupUI()
        setupListeners()
        requestNotificationPermissionIfNeeded()

        // Check permissions and auto-prompt when appropriate on launch
        checkAndHandlePermissions(autoLaunch = true)
    }

    override fun onResume() {
        super.onResume()
        checkAndHandlePermissions(autoLaunch = false)
    }

    private fun setupUI() {
        // 1. Sliders initialization
        binding.sliderOffsetX.value = prefs.petOffsetX.toFloat().coerceIn(-150f, 150f)
        binding.tvOffsetXVal.text = "${prefs.petOffsetX} dp"

        binding.sliderOffsetY.value = prefs.petOffsetY.toFloat().coerceIn(0f, 120f)
        binding.tvOffsetYVal.text = "${prefs.petOffsetY} dp"

        binding.sliderPetSize.value = prefs.petSizeDp.toFloat().coerceIn(32f, 96f)
        binding.tvPetSizeVal.text = "${prefs.petSizeDp} dp"

        binding.sliderIntensity.value = prefs.animationIntensity.coerceIn(0.5f, 1.5f)
        binding.tvIntensityVal.text = "${(prefs.animationIntensity * 100).toInt()}%"

        binding.sliderAngerMinutes.value = prefs.instagramAngerMinutes.toFloat().coerceIn(1f, 15f)
        binding.tvAngerMinutesVal.text = "${prefs.instagramAngerMinutes} minutes"

        binding.sliderLeashLength.value = prefs.leashLengthDp.toFloat().coerceIn(18f, 60f)
        binding.tvLeashLengthVal.text = "${prefs.leashLengthDp} dp"
        // 2. Camera alignment preset
        updateAlignmentButtonsUI(prefs.cameraAlignment)

        // 3. Nerd mode app checkboxes
        val nerdApps = prefs.nerdApps
        binding.cbChatGPT.isChecked = nerdApps.contains("com.openai.chatgpt")
        binding.cbGemini.isChecked = nerdApps.contains("com.google.android.apps.bard")
        binding.cbWPS.isChecked = nerdApps.contains("cn.wps.moffice_eng") || nerdApps.contains("com.onlyoffice.documents")
        binding.cbPDF.isChecked = nerdApps.contains("com.adobe.reader")

        // 4. Camera World & Behavior Switches
        binding.switchDynamicMovement.isChecked = prefs.isDynamicMovementEnabled
        binding.switchHideAndSeek.isChecked = prefs.isHideAndSeekEnabled
        binding.switchTouchReactions.isChecked = prefs.isTouchReactionEnabled

        // 5. Sleepiness & System Settings
        binding.switchSleepiness.isChecked = prefs.isSleepinessEnabled
        binding.sliderSleepyHour.value = prefs.sleepyStartHour.toFloat().coerceIn(19f, 25f)
        val startH = prefs.sleepyStartHour
        val hourLabel = if (startH == 24 || startH == 0) "12 AM (Midnight)" else if (startH > 12) "${startH - 12} PM" else "$startH AM"
        binding.tvSleepyHourVal.text = "$startH:00 ($hourLabel)"

        binding.switchGoogleNerd.isChecked = prefs.isGoogleNerdAppsEnabled
        binding.switchPhoneEvents.isChecked = prefs.isPhoneEventsEnabled

        // 6. Preview Pet in Tether Stage
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
        // Master Pet Toggle Switch
        binding.switchPetMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startPetOverlay()
            } else {
                stopPetOverlay()
            }
        }

        // Permission Buttons
        binding.btnGrantPermission.setOnClickListener {
            OverlayPermissionHelper.requestOverlayPermission(this)
        }

        binding.btnGrantUsageAccess.setOnClickListener {
            ForegroundAppDetector.openUsageAccessSettings(this)
        }

        binding.btnFixRestrictedSettings.setOnClickListener {
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

        // Mood preview chips
        binding.chipMoodIdle.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.IDLE, "Idle • Breathing and floating gently")
        }
        binding.chipMoodHappy.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.HAPPY, "Happy • Joyful hops and star sparkles!")
        }
        binding.chipMoodSad.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.SAD, "Sad (Instagram) • Droopy head, frown & trickling tears")
        }
        binding.chipMoodAngry.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.ANGRY, "Angry (>5m Instagram) • Fiery red, furious vibration & steam puffs!")
        }
        binding.chipMoodNerd.setOnClickListener {
            setPreviewMood(PixelPetView.PetMood.NERD, "Nerd (Learning) • Glasses drop down and eyes scan reading text")
        }

        // Camera Alignment Presets
        binding.btnAlignLeft.setOnClickListener { setCameraAlignment("LEFT") }
        binding.btnAlignCenter.setOnClickListener { setCameraAlignment("CENTER") }
        binding.btnAlignRight.setOnClickListener { setCameraAlignment("RIGHT") }

        // Position & Size Sliders (Real-time updates)
        binding.sliderOffsetX.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val offset = value.toInt()
                prefs.petOffsetX = offset
                binding.tvOffsetXVal.text = "$offset dp"
                notifySettingsChanged()
            }
        }

        binding.sliderOffsetY.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val offset = value.toInt()
                prefs.petOffsetY = offset
                binding.tvOffsetYVal.text = "$offset dp"
                notifySettingsChanged()
            }
        }

        binding.sliderPetSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val size = value.toInt()
                prefs.petSizeDp = size
                binding.tvPetSizeVal.text = "$size dp"
                notifySettingsChanged()
            }
        }

        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.animationIntensity = value
                binding.tvIntensityVal.text = "${(value * 100).toInt()}%"
                binding.previewTetherStage.petView.setAnimationIntensity(value)
                notifySettingsChanged()
            }
        }

        binding.sliderAngerMinutes.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val mins = value.toInt()
                prefs.instagramAngerMinutes = mins
                binding.tvAngerMinutesVal.text = "$mins minutes"
            }
        }

        binding.sliderLeashLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val len = value.toInt()
                prefs.leashLengthDp = len
                binding.tvLeashLengthVal.text = "$len dp"
                notifySettingsChanged()
            }
        }

        binding.btnLeashRed.setOnClickListener { setLeashColor("#F43F5E") }
        binding.btnLeashGold.setOnClickListener { setLeashColor("#F59E0B") }
        binding.btnLeashSilver.setOnClickListener { setLeashColor("#E2E8F0") }
        binding.btnLeashMint.setOnClickListener { setLeashColor("#34D399") }
        // Nerd Mode App Checkboxes
        val updateNerdApps = {
            val apps = mutableSetOf<String>()
            if (binding.cbChatGPT.isChecked) apps.add("com.openai.chatgpt")
            if (binding.cbGemini.isChecked) apps.add("com.google.android.apps.bard")
            if (binding.cbWPS.isChecked) {
                apps.add("cn.wps.moffice_eng")
                apps.add("cn.wps.moffice")
                apps.add("com.onlyoffice.documents")
            }
            if (binding.cbPDF.isChecked) {
                apps.add("com.adobe.reader")
                apps.add("com.google.android.apps.pdfviewer")
            }
            prefs.nerdApps = apps
        }
        binding.cbChatGPT.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        binding.cbGemini.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        binding.cbWPS.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        binding.cbPDF.setOnCheckedChangeListener { _, _ -> updateNerdApps() }

        // Behavior & Camera World Switches
        binding.switchDynamicMovement.setOnCheckedChangeListener { _, isChecked ->
            prefs.isDynamicMovementEnabled = isChecked
            notifySettingsChanged()
        }

        binding.switchHideAndSeek.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHideAndSeekEnabled = isChecked
            notifySettingsChanged()
        }

        binding.switchTouchReactions.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTouchReactionEnabled = isChecked
            notifySettingsChanged()
        }

        // Sleepiness & System Settings
        binding.switchSleepiness.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSleepinessEnabled = isChecked
            notifySettingsChanged()
        }

        binding.sliderSleepyHour.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val h = value.toInt()
                prefs.sleepyStartHour = h
                val hLabel = if (h == 24 || h == 0) "12 AM (Midnight)" else if (h > 12) "${h - 12} PM" else "$h AM"
                binding.tvSleepyHourVal.text = "$h:00 ($hLabel)"
                notifySettingsChanged()
            }
        }

        binding.switchGoogleNerd.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGoogleNerdAppsEnabled = isChecked
            notifySettingsChanged()
        }

        binding.switchPhoneEvents.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPhoneEventsEnabled = isChecked
        }

        // Debugger Screen
        binding.btnOpenDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
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
        updateAlignmentButtonsUI(alignment)
        notifySettingsChanged()
    }

    private fun updateAlignmentButtonsUI(alignment: String) {
        val selectedColor = ContextCompat.getColor(this, R.color.smiley_yellow)
        val defaultColor = ContextCompat.getColor(this, R.color.surface_dark)

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

        // 1. Overlay Status
        if (hasOverlay) {
            binding.cardPermission.visibility = View.GONE
            binding.tvOverlayBadge.text = "Overlay: Granted 🟢"
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
            binding.cardPermission.visibility = View.VISIBLE
            binding.tvOverlayBadge.text = "Overlay: Missing ⚠️"
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
            binding.tvBatteryBadge.text = "Battery: Unrestricted ⚡"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            binding.tvBatteryBadge.text = "Battery: Optimized ⚠️"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }

        // 3. Usage Access Status
        if (hasUsage) {
            binding.cardUsageAccess.visibility = View.GONE
            binding.tvUsageBadge.text = "Usage: Active 🟢"
            binding.tvUsageBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            ForegroundAppDetector.startMonitoring(this)
        } else {
            binding.cardUsageAccess.visibility = View.VISIBLE
            binding.tvUsageBadge.text = "Usage: Missing ⚠️"
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
}
