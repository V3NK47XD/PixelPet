package com.dynamicisland.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dynamicisland.app.util.OverlayPermissionHelper
import com.dynamicisland.app.util.PreferencesHelper
import com.dynamicisland.app.view.LeashCanvasView
import com.dynamicisland.app.view.PixelPetView
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Persistent Foreground Service hosting a two-window overlay architecture:
 *
 * 1. Window A (Leash & Anchor Window - LeashCanvasView):
 *    - Configured with FLAG_NOT_TOUCHABLE.
 *    - 100% of touches everywhere outside the pet pass straight through to the underlying app
 *      (Instagram Reels, status bar, browser, keyboard, etc.) without obstruction.
 *    - In Touch Debug Mode: renders cyan dashed outline for the pass-through area.
 *
 * 2. Window B (Interactive Pet Window - PixelPetView):
 *    - Tightly wrapped to petSizeDp x petSizeDp at exact pet screen coordinates.
 *    - Custom hit-testing in touch listener: dragging begins only when initial touch hits pet.
 *    - Constrained strictly within the leash radius (cannot escape leash tether).
 *    - Elastic spring-back oscillation upon drag release.
 *    - Rich tap interactions: sleep/wake toggle, rapid multi-tap anger tantrum (3.8s), and contextual taps.
 *    - In Touch Debug Mode: renders green window bounds and magenta circular pet hitbox.
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
    private lateinit var prefs: PreferencesHelper
    private lateinit var moodManager: com.dynamicisland.app.util.PetMoodManager
    private var phoneEventManager: com.dynamicisland.app.util.PhoneEventManager? = null
    private var physicsEngine: com.dynamicisland.app.util.PetPhysicsEngine? = null
    private val handler = Handler(Looper.getMainLooper())

    // Window 1: Pass-Through Leash & Anchor Window
    private var leashView: LeashCanvasView? = null
    private var leashParams: WindowManager.LayoutParams? = null

    // Window 2: Interactive Pet Window (Tightly wrapped to pet sprite)
    private var petContainer: FrameLayout? = null
    private var petView: PixelPetView? = null
    private var petParams: WindowManager.LayoutParams? = null

    // Autonomous Drift & Floating Physics State (in DP relative to camera anchor)
    private var currentDriftXDp = -24f
    private var currentDriftYDp = 22f
    private var floatBobYDp = 0f
    private var tiltAngleDeg = 0f
    private var physicsOffsetXDp = 0f
    private var physicsOffsetYDp = 0f
    private var physicsTiltDeg = 0f
    private var leashSlackFactor = 1.0f
    // Cached Settings to avoid repeated SharedPreferences lookups in animation callbacks
    private var cachedCameraRefX = 0
    private var cachedCameraRefY = 22
    private var cachedPetSizeDp = 54
    private var cachedLeashLengthDp = 36
    private var cachedIntensity = 1.0f
    private var cachedTouchReaction = true

    private var driftAnimator: ValueAnimator? = null
    private var floatBobAnimator: ValueAnimator? = null
    private var snapBackAnimator: ValueAnimator? = null
    private var isDriftEngineRunning = false

    // Dragging & Tap Detection
    private var isDragging = false
    private var hasMoved = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downDriftXDp = 0f
    private var downDriftYDp = 0f
    private var touchSlop = 0f

    // Tap Interaction State
    private var tapCount = 0
    private var lastTapTimeMs = 0L
    private var isTemporarilyAngry = false
    private var savedMoodBeforeAnger = PixelPetView.PetMood.IDLE
    private var isManuallyAwoken = false

    private val driftRunnable = object : Runnable {
        override fun run() {
            if (!isDriftEngineRunning || isDragging) return
            pickNextFloatingSpot()
            scheduleNextDrift()
        }
    }

    private val angryResetRunnable = Runnable {
        if (isTemporarilyAngry) {
            isTemporarilyAngry = false
            tapCount = 0
            petView?.setMood(savedMoodBeforeAnger)
            petView?.triggerIdleActivity(PixelPetView.IdleActivity.BLUSHING, 1400L)
            updateLeashAttachment(slackFactor = 1.0f)
        }
    }

    private val appChangeListener: (String, Boolean) -> Unit = { packageName, _ ->
        moodManager.onForegroundPackageChanged(packageName)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)
        refreshCachedSettings()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

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

            override fun onScreenTurnedOff() {
                petView?.pauseAnimations()
                floatBobAnimator?.pause()
                driftAnimator?.pause()
                handler.removeCallbacks(driftRunnable)
                physicsEngine?.onScreenOff()
                com.dynamicisland.app.util.ForegroundAppDetector.pauseMonitoring()
            }

            override fun onScreenTurnedOn() {
                com.dynamicisland.app.util.ForegroundAppDetector.resumeMonitoring()
                petView?.resumeAnimations()
                floatBobAnimator?.resume()
                driftAnimator?.resume()
                physicsEngine?.onScreenOn()
                scheduleNextDrift()
                moodManager.recalculateMood()
            }
        })
        phoneEventManager?.start()

        physicsEngine = com.dynamicisland.app.util.PetPhysicsEngine(this) { offsetXDp, offsetYDp, tiltDeg, isTension ->
            if (!isDragging) {
                physicsOffsetXDp = offsetXDp
                physicsOffsetYDp = offsetYDp
                physicsTiltDeg = tiltDeg
                petView?.rotation = tiltAngleDeg + physicsTiltDeg
                updatePetWindowLayout()
                updateLeashAttachment(slackFactor = if (isTension) 0.35f else 1.0f)
            }
        }
        physicsEngine?.start()

        com.dynamicisland.app.util.ForegroundAppDetector.addListener(appChangeListener)
        com.dynamicisland.app.util.ForegroundAppDetector.startMonitoring(this)

        setupFloatingPhysicsAnimators()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                com.dynamicisland.app.util.ForegroundAppDetector.stopMonitoring()
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
                    moodManager.simulateMood(PixelPetView.PetMood.SAD, "Instagram Triggered")
                } else {
                    moodManager.simulateMood(PixelPetView.PetMood.HAPPY, "Normal Triggered")
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
            e.printStackTrace()
        }
    }

    private fun setupFloatingPhysicsAnimators() {
        // Continuous tiny buoyant bobbing & tilting
        floatBobAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (!isDragging) {
                    val p = anim.animatedValue as Float
                    floatBobYDp = (sin(p) * 2.6f * cachedIntensity)
                    val baseTilt = (currentDriftXDp * 0.28f).coerceIn(-12f, 12f)
                    tiltAngleDeg = baseTilt + (sin(p) * 2.0f * cachedIntensity)

                    petView?.rotation = tiltAngleDeg
                    // Per-frame WindowManager.updateViewLayout IPC removed!
                    // Vertical float is smoothly drawn inside PixelPetView canvas
                    updateLeashAttachment(slackFactor = 1.0f)
                }
            }
        }
    }

    private fun startDriftEngine() {
        if (isDriftEngineRunning) return
        isDriftEngineRunning = true

        if (floatBobAnimator?.isStarted != true) {
            floatBobAnimator?.start()
        }

        pickNextFloatingSpot()
        scheduleNextDrift()
    }

    private fun stopDriftEngine() {
        isDriftEngineRunning = false
        handler.removeCallbacksAndMessages(null)
        driftAnimator?.cancel()
        snapBackAnimator?.cancel()
        floatBobAnimator?.cancel()
    }

    private fun scheduleNextDrift() {
        handler.removeCallbacks(driftRunnable)
        val nextDelayMs = Random.nextLong(4500L, 8500L) // Never sits > 10s
        handler.postDelayed(driftRunnable, nextDelayMs)
    }

    private fun pickNextFloatingSpot() {
        if (isDragging) return
        val leashLength = prefs.leashLengthDp.toFloat()

        // Spots around camera: Left, Right, Underneath dangling, Lean Left, Lean Right, Close Hover
        val (nextX, nextY) = when (Random.nextInt(6)) {
            0 -> Pair(-(leashLength * 0.75f), leashLength * 0.55f)
            1 -> Pair(leashLength * 0.75f, leashLength * 0.55f)
            2 -> Pair(0f, leashLength * 0.95f) // Dangle underneath
            3 -> Pair(-(leashLength * 0.55f), leashLength * 0.8f)
            4 -> Pair(leashLength * 0.55f, leashLength * 0.8f)
            else -> Pair(
                if (Random.nextBoolean()) -(leashLength * 0.4f) else (leashLength * 0.4f),
                leashLength * 0.4f
            )
        }

        driftTo(nextX, nextY)
    }

    private fun driftTo(targetXDp: Float, targetYDp: Float, durationMs: Long = 1600L) {
        if (isDragging) return
        val startX = currentDriftXDp
        val startY = currentDriftYDp

        driftAnimator?.cancel()
        driftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { anim ->
                if (!isDragging) {
                    val f = anim.animatedFraction
                    currentDriftXDp = startX + ((targetXDp - startX) * f)
                    currentDriftYDp = startY + ((targetYDp - startY) * f)
                    leashSlackFactor = 1f + (sin(f * PI.toFloat()) * 0.35f)
                    updatePetWindowLayout()
                    updateLeashAttachment(leashSlackFactor)
                }
            }
        }
        driftAnimator?.start()
    }

    private fun performElasticSnapBack() {
        val startX = currentDriftXDp
        val startY = currentDriftYDp

        val leashLength = prefs.leashLengthDp.toFloat()
        val targetX = if (startX < -8f) -(leashLength * 0.65f) else if (startX > 8f) (leashLength * 0.65f) else 0f
        val targetY = if (targetX == 0f) leashLength * 0.85f else leashLength * 0.55f

        snapBackAnimator?.cancel()
        snapBackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                currentDriftXDp = startX + ((targetX - startX) * f)
                currentDriftYDp = startY + ((targetY - startY) * f)
                leashSlackFactor = 0.5f + (sin(f * PI.toFloat() * 3) * 0.5f * (1f - f))
                updatePetWindowLayout()
                updateLeashAttachment(leashSlackFactor)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    leashSlackFactor = 1.0f
                    scheduleNextDrift()
                }
            })
        }
        snapBackAnimator?.start()
    }

    private fun showOverlay() {
        if (petContainer != null || leashView != null) return

        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            return
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val petSize = prefs.petSizeDp.dpToPx()
        val stageWidth = (prefs.leashLengthDp * 2 + prefs.petSizeDp + 36).dpToPx()
        val stageHeight = (prefs.leashLengthDp + prefs.petSizeDp + 24).dpToPx()

        // -------------------------------------------------------------
        // WINDOW A: Leash & Anchor Window (FLAG_NOT_TOUCHABLE)
        // Passes 100% of touches outside the pet straight to underlying app!
        // -------------------------------------------------------------
        val leashWindowFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        leashParams = WindowManager.LayoutParams(
            stageWidth,
            stageHeight,
            overlayType,
            leashWindowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = prefs.cameraRefX.dpToPx()
            y = (prefs.cameraRefY - 8).dpToPx()
        }

        val leash = LeashCanvasView(this)
        leashView = leash

        // -------------------------------------------------------------
        // WINDOW B: Interactive Pet Window (Tightly wrapped to petSize)
        // -------------------------------------------------------------
        val petTouchFlag = if (prefs.isTouchReactionEnabled) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val petWindowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                petTouchFlag

        val initialPetX = (prefs.cameraRefX + currentDriftXDp).toInt().dpToPx()
        val initialPetY = (prefs.cameraRefY + currentDriftYDp - (prefs.petSizeDp / 2f)).toInt().dpToPx()

        petParams = WindowManager.LayoutParams(
            petSize,
            petSize,
            overlayType,
            petWindowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = initialPetX
            y = initialPetY
        }

        val pet = PixelPetView(this).apply {
            setAnimationIntensity(prefs.animationIntensity)
            setMood(moodManager.currentMood)
        }
        petView = pet

        val container = FrameLayout(this).apply {
            addView(pet)
            setupPetTouchInteractions(this, pet)
        }
        petContainer = container

        try {
            // Add Window A first (background leash), then Window B (interactive pet on top)
            windowManager?.addView(leash, leashParams)
            windowManager?.addView(container, petParams)
            startDriftEngine()
            updateLeashAttachment(slackFactor = 1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hit-testing and gesture handling for Window B:
     * - Dragging starts ONLY when initial touch is inside pet's visible circular hitbox.
     * - Movement is strictly constrained to the leash radius.
     * - Releasing a drag triggers elastic snap-back (no accidental clicks!).
     * - Tapping without dragging triggers contextual tap interactions.
     */
    private fun setupPetTouchInteractions(container: FrameLayout, pet: PixelPetView) {
        container.setOnTouchListener { _, ev ->
            if (!prefs.isTouchReactionEnabled) return@setOnTouchListener false

            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Custom circular hit-testing: only start gesture if touch is on pet hitbox!
                    if (!pet.isTouchInsideHitbox(ev.x, ev.y)) {
                        return@setOnTouchListener false
                    }

                    isDragging = true
                    hasMoved = false
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    downDriftXDp = currentDriftXDp
                    downDriftYDp = currentDriftYDp

                    handler.removeCallbacks(driftRunnable)
                    driftAnimator?.cancel()
                    snapBackAnimator?.cancel()
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val density = resources.displayMetrics.density
                        val deltaXDp = (ev.rawX - downRawX) / density
                        val deltaYDp = (ev.rawY - downRawY) / density

                        val moveDist = sqrt((deltaXDp * deltaXDp) + (deltaYDp * deltaYDp))
                        if (moveDist * density > touchSlop) {
                            hasMoved = true
                        }

                        // Proposed offset from camera anchor
                        var vecX = downDriftXDp + deltaXDp
                        var vecY = downDriftYDp + deltaYDp

                        // Clamp strictly to leash radius
                        val distFromAnchor = sqrt((vecX * vecX) + (vecY * vecY))
                        val maxRadius = prefs.leashLengthDp * 1.15f

                        if (distFromAnchor > maxRadius && distFromAnchor > 0f) {
                            val scale = maxRadius / distFromAnchor
                            vecX *= scale
                            vecY *= scale
                        }

                        currentDriftXDp = vecX
                        currentDriftYDp = vecY

                        // Tilt pet and pull leash taut during drag
                        tiltAngleDeg = (currentDriftXDp * 0.35f).coerceIn(-25f, 25f)
                        pet.rotation = tiltAngleDeg

                        updatePetWindowLayout()
                        updateLeashAttachment(slackFactor = 0.25f)
                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        if (!hasMoved) {
                            // User tapped without dragging!
                            handlePetTapInteraction(pet)
                            scheduleNextDrift()
                        } else {
                            // User dragged and released -> Elastic spring-back!
                            performElasticSnapBack()
                        }
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun handlePetTapInteraction(pet: PixelPetView) {
        val now = System.currentTimeMillis()
        if (now - lastTapTimeMs < 650L) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTimeMs = now

        // 1. Rapid Multi-Taps (3+ times) -> Angry Temper Tantrum for 3.8s!
        if (tapCount >= 3) {
            triggerTemperTantrum(pet)
            return
        }

        // 2. Sleep / Wake Toggle
        if (pet.getMood() == PixelPetView.PetMood.SLEEPY) {
            // Wake Up!
            isManuallyAwoken = true
            pet.setMood(PixelPetView.PetMood.HAPPY)
            pet.triggerIdleActivity(PixelPetView.IdleActivity.STARTLED, 700L)
            handler.postDelayed({
                if (pet.getMood() == PixelPetView.PetMood.HAPPY) {
                    pet.triggerIdleActivity(PixelPetView.IdleActivity.YAWNING, 1400L)
                }
            }, 750L)
            return
        } else if (isManuallyAwoken) {
            // Go back to sleep!
            isManuallyAwoken = false
            pet.triggerIdleActivity(PixelPetView.IdleActivity.YAWNING, 1200L)
            handler.postDelayed({
                pet.setMood(PixelPetView.PetMood.SLEEPY)
            }, 1250L)
            return
        }

        // 3. Contextual Mood Tap
        pet.handleUserTap()
        updateLeashAttachment(slackFactor = 1.6f)
        handler.postDelayed({ updateLeashAttachment(slackFactor = 1.0f) }, 300)
    }

    private fun triggerTemperTantrum(pet: PixelPetView) {
        handler.removeCallbacks(angryResetRunnable)
        if (!isTemporarilyAngry) {
            savedMoodBeforeAnger = pet.getMood()
            isTemporarilyAngry = true
        }

        pet.setMood(PixelPetView.PetMood.ANGRY)
        pet.triggerExcitedHop()
        updateLeashAttachment(slackFactor = 0.2f)

        handler.postDelayed(angryResetRunnable, 3800L)
    }

    private fun refreshCachedSettings() {
        cachedCameraRefX = prefs.cameraRefX
        cachedCameraRefY = prefs.cameraRefY
        cachedPetSizeDp = prefs.petSizeDp
        cachedLeashLengthDp = prefs.leashLengthDp
        cachedIntensity = prefs.animationIntensity
        cachedTouchReaction = prefs.isTouchReactionEnabled
    }

    private fun updatePetWindowLayout() {
        petParams?.let { params ->
            val petX = (cachedCameraRefX + currentDriftXDp + physicsOffsetXDp).toInt().dpToPx()
            val petY = (cachedCameraRefY + currentDriftYDp + physicsOffsetYDp - (cachedPetSizeDp / 2f)).toInt().dpToPx()

            if (params.x != petX || params.y != petY) {
                params.x = petX
                params.y = petY

                if (petContainer?.isAttachedToWindow == true) {
                    windowManager?.updateViewLayout(petContainer, params)
                }
            }
        }
    }

    private fun updateLeashAttachment(slackFactor: Float) {
        val leash = leashView ?: return
        val leashParams = leashParams ?: return

        val leashWidth = leashParams.width.toFloat()
        val leashAnchorX = leashWidth / 2f
        val leashAnchorY = 8.dpToPx().toFloat()

        val petTopXInLeash = leashAnchorX + (currentDriftXDp + physicsOffsetXDp).dpToPx()
        val petTopYInLeash = leashAnchorY + (currentDriftYDp + physicsOffsetYDp - (cachedPetSizeDp * 0.42f)).dpToPx()

        leash.updatePetAttachment(petTopXInLeash, petTopYInLeash, slackFactor)
    }

    private fun applyUpdatedSettings() {
        refreshCachedSettings()
        val petSize = cachedPetSizeDp.dpToPx()
        val stageWidth = (cachedLeashLengthDp * 2 + cachedPetSizeDp + 36).dpToPx()
        val stageHeight = (cachedLeashLengthDp + cachedPetSizeDp + 24).dpToPx()

        // 1. Update Leash Window
        leashParams?.let { params ->
            params.width = stageWidth
            params.height = stageHeight
            params.x = cachedCameraRefX.dpToPx()
            params.y = (cachedCameraRefY - 8).dpToPx()
            leashView?.updateLeashColor()
            leashView?.invalidate()
            if (leashView?.isAttachedToWindow == true) {
                windowManager?.updateViewLayout(leashView, params)
            }
        }

        // 2. Update Pet Window
        petParams?.let { params ->
            params.width = petSize
            params.height = petSize
            val touchFlag = if (cachedTouchReaction) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    touchFlag

            petView?.setAnimationIntensity(cachedIntensity)
            petView?.invalidate()
            updatePetWindowLayout()
        }

        updateLeashAttachment(slackFactor = 1.0f)
        physicsEngine?.onSettingsChanged()
        moodManager.recalculateMood()
    }

    private fun removeOverlay() {
        stopDriftEngine()

        petContainer?.let { container ->
            try {
                if (container.isAttachedToWindow) {
                    windowManager?.removeView(container)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            petContainer = null
            petView = null
        }

        leashView?.let { leash ->
            try {
                if (leash.isAttachedToWindow) {
                    windowManager?.removeView(leash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            leashView = null
        }
    }

    override fun onDestroy() {
        phoneEventManager?.stop()
        physicsEngine?.stop()
        physicsEngine = null
        com.dynamicisland.app.util.ForegroundAppDetector.removeListener(appChangeListener)
        com.dynamicisland.app.util.ForegroundAppDetector.stopMonitoring()
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

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density
}
