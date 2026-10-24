package com.dynamicisland.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.app.databinding.ActivityMainBinding
import com.dynamicisland.app.databinding.DialogCalibrateOrientationBinding
import com.dynamicisland.app.databinding.DialogDevToolsBinding
import com.dynamicisland.app.databinding.DialogMenuPetBinding
import com.dynamicisland.app.databinding.DialogMenuPositionBinding
import com.dynamicisland.app.databinding.DialogMenuReactionsBinding
import com.dynamicisland.app.databinding.DialogMenuSoundBinding
import com.dynamicisland.app.databinding.DialogRenamePetBinding
import com.dynamicisland.app.util.PetSoundHelper
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

        // First Launch: Prompt user to calibrate their comfortable holding orientation
        if (!prefs.isOrientationCalibrated) {
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    showOrientationCalibrationDialog()
                }
            }, 850)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndHandlePermissions(autoLaunch = false)
    }

    private fun setupUI() {
        // Pet Name Header
        binding.tvPetName.text = prefs.petName

        // Live Interactive Preview in Tether Stage
        binding.previewTetherStage.petView.setAnimationIntensity(prefs.animationIntensity)
        binding.previewTetherStage.petView.setMood(PixelPetView.PetMood.IDLE)
        binding.previewTetherStage.updateLeashColor()
        binding.tvPreviewMoodDesc.text = "${prefs.petName} • Floating near camera"
    }

    private fun setupListeners() {
        // Rename Pet Listeners
        binding.tvPetName.setOnClickListener { showRenamePetDialog() }
        binding.btnEditPetName.setOnClickListener { showRenamePetDialog() }

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

        // 5 Modular Bottom Menu Button Click Handlers Below Preview
        binding.btnMenuPosition.setOnClickListener { showPositionConfigSheet() }
        binding.btnMenuPet.setOnClickListener { showPetConfigSheet() }
        binding.btnMenuSound.setOnClickListener { showSoundConfigSheet() }
        binding.btnMenuReactions.setOnClickListener { showReactionsConfigSheet() }
        binding.btnMenuDevTools.setOnClickListener { showDevToolsBottomSheet() }
    }

    private fun showPositionConfigSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = DialogMenuPositionBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.knobCameraX.apply {
            minValue = -150f
            maxValue = 150f
            stepSize = 1f
            unitSuffix = "dp"
            knobTitle = "CAM X"
            value = prefs.cameraRefX.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    val intVal = newVal.toInt()
                    debouncePrefWrite("cameraRefX") {
                        prefs.cameraRefX = intVal
                        binding.previewTetherStage.updatePetViewLayout()
                        binding.previewTetherStage.invalidate()
                        notifySettingsChanged()
                    }
                }
            }
        }

        sheet.knobCameraY.apply {
            minValue = 0f
            maxValue = 120f
            stepSize = 1f
            unitSuffix = "dp"
            knobTitle = "CAM Y"
            value = prefs.cameraRefY.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    val intVal = newVal.toInt()
                    debouncePrefWrite("cameraRefY") {
                        prefs.cameraRefY = intVal
                        binding.previewTetherStage.updatePetViewLayout()
                        binding.previewTetherStage.invalidate()
                        notifySettingsChanged()
                    }
                }
            }
        }

        sheet.knobPetSize.apply {
            minValue = 32f
            maxValue = 96f
            stepSize = 2f
            unitSuffix = "dp"
            knobTitle = "SIZE"
            value = prefs.petSizeDp.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    val intVal = newVal.toInt()
                    debouncePrefWrite("petSizeDp") {
                        prefs.petSizeDp = intVal
                        binding.previewTetherStage.updatePetViewLayout()
                        binding.previewTetherStage.invalidate()
                        notifySettingsChanged()
                    }
                }
            }
        }

        sheet.knobLeash.apply {
            minValue = 20f
            maxValue = 120f
            stepSize = 2f
            unitSuffix = "dp"
            knobTitle = "LEASH"
            value = prefs.leashLengthDp.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    val intVal = newVal.toInt()
                    debouncePrefWrite("leashLengthDp") {
                        prefs.leashLengthDp = intVal
                        binding.previewTetherStage.updatePetViewLayout()
                        binding.previewTetherStage.invalidate()
                        notifySettingsChanged()
                    }
                }
            }
        }

        val updatePresetsUI = { alignment: String ->
            val sel = ContextCompat.getColor(this, R.color.peach_primary)
            val selText = ContextCompat.getColor(this, R.color.peach_dark)
            val defBg = ContextCompat.getColor(this, R.color.peach_surface)
            val defText = ContextCompat.getColor(this, R.color.peach_light)

            sheet.btnAlignLeft.setBackgroundColor(if (alignment == "LEFT") sel else defBg)
            sheet.btnAlignLeft.setTextColor(if (alignment == "LEFT") selText else defText)
            sheet.btnAlignCenter.setBackgroundColor(if (alignment == "CENTER") sel else defBg)
            sheet.btnAlignCenter.setTextColor(if (alignment == "CENTER") selText else defText)
            sheet.btnAlignRight.setBackgroundColor(if (alignment == "RIGHT") sel else defBg)
            sheet.btnAlignRight.setTextColor(if (alignment == "RIGHT") selText else defText)
        }
        updatePresetsUI(prefs.cameraAlignment)

        val setAlign = { alignment: String ->
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
            sheet.knobCameraX.value = prefs.cameraRefX.toFloat()
            sheet.knobCameraY.value = prefs.cameraRefY.toFloat()
            updatePresetsUI(alignment)
            binding.previewTetherStage.updatePetViewLayout()
            binding.previewTetherStage.invalidate()
            notifySettingsChanged()
        }

        sheet.btnAlignLeft.setOnClickListener { setAlign("LEFT") }
        sheet.btnAlignCenter.setOnClickListener { setAlign("CENTER") }
        sheet.btnAlignRight.setOnClickListener { setAlign("RIGHT") }

        sheet.btnLeashRed.setOnClickListener { setLeashColor("#F43F5E") }
        sheet.btnLeashGold.setOnClickListener { setLeashColor("#F59E0B") }
        sheet.btnLeashSilver.setOnClickListener { setLeashColor("#E2E8F0") }
        sheet.btnLeashMint.setOnClickListener { setLeashColor("#34D399") }
        // Leash Motion Sensitivity / Deadzone Threshold Slider
        val updateSensitivityUI = { thresh: Float ->
            val formatted = "%.1f".format(thresh)
            sheet.tvSensitivityValue.text = "Deadzone: $formatted m/s²"
            val desc = when {
                thresh <= 0.3f -> "High Sensitivity: reacts to micro-movements."
                thresh <= 0.8f -> "Balanced: ignores normal hand tremors & tiny tilts."
                thresh <= 1.4f -> "Firm: ignores fidgeting, reacts to deliberate tilts."
                else -> "Ultra-Firm: only moves on large deliberate phone tilts."
            }
            sheet.tvSensitivityDescription.text = desc
        }

        sheet.sliderLeashSensitivity.value = prefs.leashMotionThreshold.coerceIn(0.1f, 2.5f)
        updateSensitivityUI(sheet.sliderLeashSensitivity.value)

        sheet.sliderLeashSensitivity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateSensitivityUI(value)
                debouncePrefWrite("leashSensitivity") {
                    prefs.leashMotionThreshold = value
                    notifySettingsChanged()
                }
            }
        }

        sheet.btnClosePosition.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPetConfigSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = DialogMenuPetBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvSheetPetName.text = prefs.petName
        sheet.btnRenameInSheet.setOnClickListener {
            dialog.dismiss()
            showRenamePetDialog()
        }

        sheet.sheetSwitchGyroPhysics.isChecked = prefs.isGyroPhysicsEnabled
        sheet.sheetSwitchGyroPhysics.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGyroPhysicsEnabled = isChecked
            binding.previewTetherStage.onSettingsChanged()
            updateSheetCalibrationUI(sheet)
            notifySettingsChanged()
        }

        updateSheetCalibrationUI(sheet)
        sheet.sheetBtnCalibrateHold.setOnClickListener {
            dialog.dismiss()
            showOrientationCalibrationDialog()
        }

        sheet.sheetSwitchSleepiness.isChecked = prefs.isSleepinessEnabled
        sheet.sheetSwitchSleepiness.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSleepinessEnabled = isChecked
            notifySettingsChanged()
        }

        sheet.sheetSwitchTouchReactions.isChecked = prefs.isTouchReactionEnabled
        sheet.sheetSwitchTouchReactions.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTouchReactionEnabled = isChecked
            notifySettingsChanged()
        }

        sheet.sheetChipIdle.setOnClickListener { setPreviewMood(PixelPetView.PetMood.IDLE, "Idle and floating") }
        sheet.sheetChipHappy.setOnClickListener { setPreviewMood(PixelPetView.PetMood.HAPPY, "Joyful hops and sparkles") }
        sheet.sheetChipSad.setOnClickListener { setPreviewMood(PixelPetView.PetMood.SAD, "Teardrops crying") }
        sheet.sheetChipAngry.setOnClickListener { setPreviewMood(PixelPetView.PetMood.ANGRY, "Rage steam tantrum") }
        sheet.sheetChipNerd.setOnClickListener { setPreviewMood(PixelPetView.PetMood.NERD, "Glasses reading") }
        sheet.sheetChipTexting.setOnClickListener { setPreviewMood(PixelPetView.PetMood.TEXTING, "Chat typing") }

        sheet.btnClosePetConfig.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateSheetCalibrationUI(sheet: DialogMenuPetBinding) {
        if (!prefs.isGyroPhysicsEnabled) {
            sheet.sheetTvCalibrationStatus.text = "Mode 1: Autonomous Drift (Physics OFF)"
            sheet.sheetTvCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            sheet.sheetBtnCalibrateHold.visibility = View.GONE
        } else {
            sheet.sheetBtnCalibrateHold.visibility = View.VISIBLE
            if (prefs.isOrientationCalibrated) {
                val y = prefs.calibratedAccelY
                val pitchDeg = Math.toDegrees(kotlin.math.asin((y / 9.8f).coerceIn(-1f, 1f).toDouble())).toInt()
                sheet.sheetTvCalibrationStatus.text = "Gravity Mode: Calibrated (~${pitchDeg}° pitch)"
                sheet.sheetTvCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.peach_primary))
            } else {
                sheet.sheetTvCalibrationStatus.text = "Gravity Mode: Tap calibrate angle"
                sheet.sheetTvCalibrationStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun showSoundConfigSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = DialogMenuSoundBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.switchSoundMaster.isChecked = prefs.isSoundEnabled
        sheet.switchSoundMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoundEnabled = isChecked
        }

        sheet.knobSoundVolume.apply {
            minValue = 0f
            maxValue = 100f
            stepSize = 5f
            unitSuffix = "%"
            knobTitle = "VOL"
            value = (prefs.soundVolume * 100f).coerceIn(0f, 100f)
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    prefs.soundVolume = (newVal / 100f).coerceIn(0f, 1f)
                }
            }
        }

        sheet.switchSoundTap.isChecked = prefs.isTapSoundEnabled
        sheet.switchSoundTap.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTapSoundEnabled = isChecked
        }

        sheet.switchSoundPunch.isChecked = prefs.isPunchSoundEnabled
        sheet.switchSoundPunch.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPunchSoundEnabled = isChecked
        }

        sheet.switchSoundAnger.isChecked = prefs.isAngerSoundEnabled
        sheet.switchSoundAnger.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAngerSoundEnabled = isChecked
        }

        sheet.btnTestChirp.setOnClickListener {
            PetSoundHelper.playHappyChirp(this)
        }
        sheet.btnTestPunch.setOnClickListener {
            PetSoundHelper.playPunchSqueak(this)
        }
        sheet.btnTestAnger.setOnClickListener {
            PetSoundHelper.playAngryTantrum(this)
        }

        sheet.btnCloseSound.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showReactionsConfigSheet() {
        val dialog = BottomSheetDialog(this)
        val sheet = DialogMenuReactionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.sheetKnobInstagramAnger.apply {
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

        sheet.sheetSwitchGoogleNerd.isChecked = prefs.isGoogleNerdAppsEnabled
        sheet.sheetSwitchGoogleNerd.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGoogleNerdAppsEnabled = isChecked
        }

        val nerdApps = prefs.nerdApps
        sheet.sheetCbChatGPT.isChecked = nerdApps.contains("com.openai.chatgpt")
        sheet.sheetCbGemini.isChecked = nerdApps.contains("com.google.android.apps.bard")
        sheet.sheetCbWPS.isChecked = nerdApps.contains("cn.wps.moffice_eng") || nerdApps.contains("com.onlyoffice.documents")
        sheet.sheetCbPDF.isChecked = nerdApps.contains("com.adobe.reader")

        val updateNerdApps = {
            val apps = mutableSetOf<String>()
            if (sheet.sheetCbChatGPT.isChecked) apps.add("com.openai.chatgpt")
            if (sheet.sheetCbGemini.isChecked) apps.add("com.google.android.apps.bard")
            if (sheet.sheetCbWPS.isChecked) {
                apps.add("cn.wps.moffice_eng")
                apps.add("cn.wps.moffice")
                apps.add("com.onlyoffice.documents")
            }
            if (sheet.sheetCbPDF.isChecked) {
                apps.add("com.adobe.reader")
                apps.add("com.google.android.apps.pdfviewer")
            }
            prefs.nerdApps = apps
        }
        sheet.sheetCbChatGPT.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheet.sheetCbGemini.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheet.sheetCbWPS.setOnCheckedChangeListener { _, _ -> updateNerdApps() }
        sheet.sheetCbPDF.setOnCheckedChangeListener { _, _ -> updateNerdApps() }

        sheet.btnCloseReactions.setOnClickListener { dialog.dismiss() }
        dialog.show()
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

        sheetBinding.knobTantrumDuration.apply {
            minValue = 3f
            maxValue = 30f
            stepSize = 1f
            unitSuffix = "s"
            knobTitle = "COOL"
            value = prefs.angerDurationSeconds.toFloat()
            onValueChanged = { newVal, fromUser ->
                if (fromUser) {
                    prefs.angerDurationSeconds = newVal.toInt()
                    notifySettingsChanged()
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
    private fun updateCalibrationStatusUI() {
        binding.previewTetherStage.invalidate()
    }

    private fun showRenamePetDialog() {
        val dialog = BottomSheetDialog(this)
        val diagBinding = DialogRenamePetBinding.inflate(layoutInflater)
        dialog.setContentView(diagBinding.root)

        diagBinding.etPetNameInput.setText(prefs.petName)
        diagBinding.etPetNameInput.selectAll()

        diagBinding.btnSuggestKiddo.setOnClickListener { diagBinding.etPetNameInput.setText("Kiddo") }
        diagBinding.btnSuggestMochi.setOnClickListener { diagBinding.etPetNameInput.setText("Mochi") }
        diagBinding.btnSuggestPip.setOnClickListener { diagBinding.etPetNameInput.setText("Pip") }
        diagBinding.btnSuggestSunny.setOnClickListener { diagBinding.etPetNameInput.setText("Sunny") }

        diagBinding.btnSavePetName.setOnClickListener {
            val name = diagBinding.etPetNameInput.text.toString().trim()
            if (name.isNotBlank()) {
                prefs.petName = name
                binding.tvPetName.text = name
                binding.tvPreviewMoodDesc.text = "$name • Floating near camera"
                binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                notifySettingsChanged()
                dialog.dismiss()
            }
        }

        diagBinding.btnCancelRename.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showOrientationCalibrationDialog() {
        val dialog = BottomSheetDialog(this)
        val diagBinding = DialogCalibrateOrientationBinding.inflate(layoutInflater)
        dialog.setContentView(diagBinding.root)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var currentAx = 0f
        var currentAy = 6.8f
        var currentAz = 6.8f
        var isCalibrating = false
        val samples = mutableListOf<Triple<Float, Float, Float>>()

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                currentAx = event.values[0]
                currentAy = event.values[1]
                currentAz = event.values[2]

                // Live visual bubble level readout
                val density = resources.displayMetrics.density
                val bubbleRangePx = 36f * density
                val bubbleX = (-currentAx / 9.8f * bubbleRangePx).coerceIn(-bubbleRangePx, bubbleRangePx)
                val bubbleY = ((currentAy - 6.8f) / 9.8f * bubbleRangePx).coerceIn(-bubbleRangePx, bubbleRangePx)

                diagBinding.viewLevelBubble.translationX = bubbleX
                diagBinding.viewLevelBubble.translationY = bubbleY

                val rollDeg = (-currentAx * 5.7f).toInt()
                val pitchDeg = Math.toDegrees(kotlin.math.asin((currentAy / 9.8f).coerceIn(-1f, 1f).toDouble())).toInt()
                diagBinding.tvLiveTiltReadout.text = "Roll: ${rollDeg}° • Pitch: ${pitchDeg}°"

                if (isCalibrating) {
                    samples.add(Triple(currentAx, currentAy, currentAz))
                }
            }
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        dialog.setOnDismissListener {
            sensorManager?.unregisterListener(listener)
        }

        diagBinding.btnStartCalibration.setOnClickListener {
            if (isCalibrating) return@setOnClickListener
            isCalibrating = true
            samples.clear()

            diagBinding.progressCalibration.visibility = View.VISIBLE
            diagBinding.progressCalibration.progress = 0
            diagBinding.tvCalibrationPrompt.text = "Calibrating... Hold phone still in your hand 🐣"
            diagBinding.btnStartCalibration.isEnabled = false
            diagBinding.btnSkipCalibration.isEnabled = false

            val progressAnim = ValueAnimator.ofInt(0, 100).apply {
                duration = 1200
                addUpdateListener { anim ->
                    diagBinding.progressCalibration.progress = anim.animatedValue as Int
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isCalibrating = false
                        if (samples.isNotEmpty()) {
                            val avgX = samples.map { it.first }.average().toFloat()
                            val avgY = samples.map { it.second }.average().toFloat()
                            val avgZ = samples.map { it.third }.average().toFloat()

                            prefs.calibratedAccelX = avgX
                            prefs.calibratedAccelY = avgY
                            prefs.calibratedAccelZ = avgZ
                            prefs.isOrientationCalibrated = true

                            binding.previewTetherStage.petView.triggerExcitedHop()
                            binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            diagBinding.tvCalibrationPrompt.text = "Calibrated! ✨ Your natural position is saved."
                            diagBinding.tvCalibrationPrompt.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.peach_primary))

                            updateCalibrationStatusUI()
                            notifySettingsChanged()

                            diagBinding.root.postDelayed({
                                dialog.dismiss()
                            }, 500)
                        } else {
                            dialog.dismiss()
                        }
                    }
                })
            }
            progressAnim.start()
        }

        diagBinding.btnSkipCalibration.setOnClickListener {
            prefs.calibratedAccelX = 0f
            prefs.calibratedAccelY = 6.8f
            prefs.calibratedAccelZ = 6.8f
            prefs.isOrientationCalibrated = true
            updateCalibrationStatusUI()
            notifySettingsChanged()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun setPreviewMood(mood: PixelPetView.PetMood, description: String) {
        binding.previewTetherStage.petView.setMood(mood)
        binding.tvPreviewMoodDesc.text = "${prefs.petName} • $description"
    }
    private fun setLeashColor(colorHex: String) {
        prefs.leashColor = colorHex
        binding.previewTetherStage.updateLeashColor()
        notifySettingsChanged()
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
            binding.tvOverlayBadge.text = "Overlay Active"
            binding.tvOverlayBadge.setTextColor(ContextCompat.getColor(this, R.color.duo_green))

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
            binding.tvOverlayBadge.text = "No Overlay"
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
            binding.tvBatteryBadge.text = "Battery Safe"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.duo_green))
        } else {
            binding.tvBatteryBadge.text = "Battery Restricted"
            binding.tvBatteryBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }

        // 3. Usage Access Status
        if (hasUsage) {
            binding.tvUsageBadge.text = "App Detect"
            binding.tvUsageBadge.setTextColor(ContextCompat.getColor(this, R.color.duo_green))
            ForegroundAppDetector.startMonitoring(this)
        } else {
            binding.tvUsageBadge.text = "Usage Needed"
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
