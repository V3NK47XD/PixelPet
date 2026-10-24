# CURRENT STATE OF PIXEL PET

> **Purpose**: This document tracks the top-to-bottom architecture, components, features, and recent changes of Pixel Pet. It must be updated at each step to prevent context loss.

---

## 🐣 Project Identity & Core Concept
- **App Name**: Pixel Pet (Companion name default: **"Kiddo"**)
- **Platform**: Android 8.0+ (API 26 to API 34, Android 14)
- **Concept**: A living pixel-art companion tethered by an organic virtual keychain physics wire directly to the phone's front camera cutout $(cameraRefX, cameraRefY)$. It floats borderless, reacts to foreground apps (Instagram, WhatsApp, YouTube, Chrome, ChatGPT), reacts to phone events (charging, battery), and swings like a physical keychain when the phone is tilted, moved, or shaken.

---

## 🏛️ High-Level Architecture

```
[Phone Hardware (3-Axis Accelerometer)] 
         │ (120Hz/144Hz Choreographer VSync)
         ▼
[PetPhysicsEngine] ──> Calibrated Neutral Position Reference (ax0, ay0, az0)
         │              Full 180° circular pendulum arc math
         ▼
[DynamicIslandService] (Persistent Foreground Overlay Service)
   ├── Window A: [LeashCanvasView] (FLAG_NOT_TOUCHABLE, draws anchor ring & flexible wire)
   └── Window B: [FrameLayout -> PixelPetView] (Touch-capturing, hitbox-constrained window)
         ▲
         │ (Foreground App Events via UsageStatsManager 500ms)
[ForegroundAppDetector] ──> [PetMoodManager] (Priority state machine)
```

---

## 📁 Key File Inventory & Responsibilities

1. **`com.dynamicisland.app.DynamicIslandService`**:
   - Foreground service managing `TYPE_APPLICATION_OVERLAY`.
   - Manages Window A (Leash & Anchor) and Window B (Pet Sprite).
   - Manages touch dispatch, drag gestures, snap-back oscillation, and punch/tap anger timers.
   - Runs native `Choreographer` animation callbacks.

2. **`com.dynamicisland.app.view.TetheredPetStageView`**:
   - Single-surface custom container hosting both leash canvas and `PixelPetView` for in-app preview (`MainActivity`).
   - Implements identical 18-segment keychain wire physics, shiver waves, and touch gesture handling.

3. **`com.dynamicisland.app.view.LeashCanvasView`**:
   - Transparent background canvas for overlay Window A.
   - Renders 18-segment flexible wire with normal-vector deflection, catenary sag, swing inertia flex, and standing wave shiver ripples.
   - **Natural Resting Drape**: When hanging vertically ($dx \approx 0$), applies an organic $4\text{dp}$ lateral catenary curve so the wire never appears as a stiff pin.
   - Features 2-tone depth wire (shadow under core), metallic camera jump-ring hinge, and sprout clasp bead ($0.30f$ body anchor).
4. **`com.dynamicisland.app.view.PixelPetView`**:
   - $28 \times 28$ integer grid pixel-art renderer with anti-aliasing disabled.
   - Expressions: `IDLE`, `HAPPY`, `SAD`, `ANGRY`, `NERD`, `SLEEPY`, `CHARGING`, `LOW_BATTERY`, `CRITICAL_BATTERY`, `TEXTING`.
   - Idle activities: `WAVING`, `GIGGLE`, `BLUSHING`, `STARTLED`, `DIZZY`, `YAWNING`, `WINK`, `SURPRISED`, `LOVE`.
   - Extensible hook for future **Task Reminder Thought Cloud** (`showThoughtCloud()`).

5. **`com.dynamicisland.app.util.PetPhysicsEngine`**:
   - Universal 3-axis `Sensor.TYPE_ACCELEROMETER` primary driver (100% Android hardware compatibility) with optional gyroscope enhancement.
   - Native **120Hz / 144Hz `Choreographer` VSync** loop for ultra-smooth high-refresh rate displays.
   - Configurable **Motion Sensitivity Deadzone Slider** ($0.1$ to $2.5\text{ m/s}^2$) ignoring hand tremors.
   - Full **180° circular pendulum arc** (swings $\pm 90^\circ$ horizontally).

6. **`com.dynamicisland.app.util.PetMoodManager`**:
   - Strict priority hierarchy:
     1. `CRITICAL_BATTERY` (<5% when not charging)
     2. `isInstagram` $\implies$ `SAD` (or `ANGRY` after threshold)
     3. `isTextingApp` $\implies$ `TEXTING` (WhatsApp, Discord, Telegram, Messages, LinkedIn)
     4. `isNerdApp` / `isGoogleNerdApp` $\implies$ `NERD` (YouTube, Chrome, ChatGPT, Gemini, WPS, PDF, Docs)
     5. `currentPackageName == context.packageName` $\implies$ `HAPPY`
     6. `isCharging` $\implies$ `CHARGING` (only when not in reaction app)
     7. `isBatteryLow` $\implies$ `LOW_BATTERY`
     8. `isNighttimeSleepy` $\implies$ `SLEEPY` (10 PM - 7 AM on launcher)
     9. `IDLE` (Default)
   - Excludes system launchers (`nexuslauncher`, `miui.home`, etc.) and keyboards (`gboard`) from triggering Nerd mode.

7. **`com.dynamicisland.app.util.ForegroundAppDetector`**:
   - Queries `UsageEvents` with 2-minute lookback window and `INTERVAL_BEST` fallback.
   - Ignores self package (`com.dynamicisland.app`), `com.android.systemui`, and `android`.

8. **`com.dynamicisland.app.util.PetSoundHelper`**:
   - Real-time programmatic PCM 16-bit sine wave synthesis via Android `AudioTrack` (0ms latency, zero external assets).
   - Soft, gentle volumes: Happy Chirp ($540\text{Hz} \to 860\text{Hz}$), Soft Plush Squeak ($460\text{Hz} \to 380\text{Hz}$), Angry Rumble ($220\text{Hz} \to 260\text{Hz}$).
   - Controlled by master mute, volume dial, and individual toggles.

9. **`com.dynamicisland.app.view.RotaryKnobView`**:
   - 270° rotary dial potentiometer with tactile haptic feedback.
   - **Floating 3D Value Callout Badge**: Pops up above user's thumb during touch so the numbers are never blocked by fingers.

10. **`com.dynamicisland.app.util.PreferencesHelper`**:
    - Centralized `SharedPreferences` repository with debounced asynchronous writes.
    - Stores coordinates, sizes, colors, pet name (`"Kiddo"`), sensitivity threshold, anger duration ($1\text{s}-60\text{s}$), sound settings, and calibration baselines.

---

## 🎨 UI & Design Architecture
- **Theme**: Warm peach (`#FF9F68`), terracotta dark (`#181210`), surface card (`#2E221E`), and cream (`#F5D0C0`).
- **Main Dashboard**: Uncluttered hero view with pet name, master toggle, status chips, 3/4-height preview card (reduced for future monetization), 5 modular bottom menus, and a 50dp reserved ad banner container:
  1. **Position Config** (`dialog_menu_position.xml`): `CAM X`, `CAM Y`, `SIZE`, `LEASH`, presets, leash colors, sensitivity slider.
  2. **Pet Config** (`dialog_menu_pet.xml`): Rename, Gravity Mode vs Drift, Calibration, Sleepiness, Touch, Mood preview chips.
  3. **Sound Config** (`dialog_menu_sound.xml`): Master sound toggle, volume dial, individual effect switches & test buttons.
  4. **Foreground App Reactions** (`dialog_menu_reactions.xml`): Instagram reaction & timer, Google apps nerd toggle, custom learning apps list (ChatGPT, Gemini, WPS, PDF).
  5. **Developer Tools** (`dialog_dev_tools.xml`): Touch debug mode, camera hide-and-seek, punch anger cooldown timer (1s–60s), active app log viewer, restricted settings guide. (Deduplicated; no redundant app reaction settings).
  6. **Ad Banner Container** (`cardAdBannerContainer`): 50dp high rectangular container reserved at the bottom for future AdMob banner monetization.
---

## 📦 Versioning & Release Rules (Inviolable)
1. **Locally-Signed APK Only**: `./gradlew assembleDebug` MUST be run on developer laptop.
2. **Never Build in GitHub Actions**: Ephemeral CI debug keystores cause `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
3. **Single APK Release Policy**: GitHub Releases must attach strictly **ONE APK** (`PixelPet-${TAG_NAME}.apk`), copied directly from `app/build/outputs/apk/debug/app-debug.apk`. Redundant `app-debug.apk` must NEVER be attached to releases.
4. **Git Tracking**: `.gitignore` uses `!` negation rules to track `app-debug.apk`.
