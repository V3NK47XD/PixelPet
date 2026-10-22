# AGENTS.md — Pixel Pet Architecture & Guide for Autonomous Agents

This document provides comprehensive technical context, architecture specifications, and engineering guidelines for AI coding assistants and developers maintaining or extending **Pixel Pet**.

---

## 🐣 Project Overview

**Pixel Pet** is an Android application that renders a borderless, transparent, animated pixel-art companion tethered to the phone's front camera cutout. The character acts as a living digital pet with physical leash mechanics, autonomous drift, contextual emotional reactions based on foreground app usage (e.g. Instagram, Google Apps, reading/learning apps), phone events (charging, low battery, unlock), and local nighttime sleepiness.

---

## 🏛️ Core Architecture

```mermaid
graph TD
    A[Phone Hardware / OS] -->|Foreground App Events| B[ForegroundAppDetector]
    A -->|Battery & Power Events| C[PhoneEventManager]
    B --> D[PetMoodManager]
    C --> D[PetMoodManager]
    D -->|Mood & Priority State| E[PetOverlayService / DynamicIslandService]
    E --> F[TetheredPetStageView]
    F -->|Draws Curved Leash| G[Physics Spring Engine]
    F -->|Autonomous Drift 4-8s| H[Drift Engine]
    F -->|Constrained Dragging| J[Leash Radius Clamping]
    F -->|Renders 28x28 Pixel Sprite| I[PixelPetView]
```

### Component Breakdown

1. **`com.dynamicisland.app.view.PixelPetView`**:
   - High-performance, anti-aliasing-free Canvas pixel-art renderer ($28 \times 28$ integer grid).
   - Manages character expressions:
     - `IDLE`: Buoyant float, breathing squash/stretch, eye blinks, looking around, sprout sway.
     - `HAPPY`: Joyful hops, crinkled closed eyes (`^ _ ^`), star sparkles.
     - `SAD`: Pale blue body (`#90CAF9`), droopy posture, animated trickling teardrops, tiny frown.
     - `ANGRY`: Crimson body (`#EF5350`), rage vibration shake, rising steam puffs.
     - `NERD`: Falling animated nerd glasses with bounce, left-to-right scanning reading eyes.
     - `SLEEPY`: Muted lavender body (`#B39DDB`), droopy eye slits, yawns, floating "Zzz" particles.
     - `CHARGING`: Soft mint green (`#A7F3D0`), gentle energy sparkles, peaceful expression.
     - `LOW_BATTERY`: Amber body (`#FB923C`), wide worried eyes glancing at battery indicator.
     - `CRITICAL_BATTERY`: Pale red body (`#F87171`), dramatic fainting collapse ($55^\circ$ tilt) with `X X` eyes.
     - `TEXTING`: Cheerful teal body (`#2DD4BF`), animated speech bubble (`💬`) with cycling typing dots (`• • •`), rapid typing hands tapping in front of body.
2. **`com.dynamicisland.app.view.TetheredPetStageView`**:
   - Transparent overlay container positioned over the front camera reference coordinate $(cameraRefX, cameraRefY)$.
   - Draws a golden anchor ring over the camera lens.
   - Calculates a dynamic, sagging catenary curve (`Path.quadTo`) connecting the camera anchor to the pet's head sprout.
   - **Autonomous Drift Engine**: Every $4.5\text{s} - 8.5\text{s}$, the pet floats to a new organic spot (left, right, underneath pendant, leaning, close hover). Never sits still $> 10\text{s}$.
   - **Leash-Constrained Dragging**: Touch and drag allows user to move the pet, strictly clamped to the leash radius ($1.15 \times \text{leashLengthDp}$).
   - **Elastic Snap-Back**: Upon drag release, pet springs back with `OvershootInterpolator(2.2f)` damped oscillation.
   - **Tap Interactions**:
     - *Sleep/Wake Toggle*: Tapping sleepy pet wakes it up; tapping again nods it off to sleep.
     - *Rapid Multi-Tap Temper Tantrum*: $3+$ taps within $650\text{ms}$ triggers a $3.8\text{s}$ furious angry tantrum with steam puffs, followed by an embarrassed blush.
     - *Single Tap*: Contextual reactions (happy wink, wave, giggle, or glasses adjust).
   - **Shape-Matched Touch Detection (`TouchableRegionHelper.kt`)**:
     - Registers an `OnComputeInternalInsetsListener` via dynamic proxy with `TOUCHABLE_INSETS_REGION`.
     - Restricts the window's touchable region strictly to the pet's visible circular body shape.
     - **100% Touch Pass-Through Outside Pet**: Punches a transparent touch hole across the entire remainder of the window so user taps on camera, status bar, and underlying apps pass through completely uninterrupted.

3. **`com.dynamicisland.app.util.PetPhysicsEngine`**:
   - Low-overhead sensor listener (`Sensor.TYPE_GRAVITY` / `Sensor.TYPE_ACCELEROMETER` and `Sensor.TYPE_GYROSCOPE`).
   - Simulates physical inertia: tilting the phone swings the pet on its leash; shaking injects rotational momentum.
   - Settles smoothly back to equilibrium when held still via damped harmonic spring physics.
   - **100% Battery-Safe**: Completely unregisters sensor listeners whenever disabled in settings OR when the screen turns off (`ACTION_SCREEN_OFF`).

4. **`com.dynamicisland.app.view.RotaryKnobView`**:
   - Compact 270° rotary dial potentiometer custom view replacing bulky vertical sliders.
   - Saves screen space to enable a 100% zero-scroll all-in-one UI dashboard.
   - Tactile haptic feedback and smooth circular/vertical touch gestures.

5. **`com.dynamicisland.app.util.TouchableRegionHelper`**:
   - Dynamically hooks Android's internal `ViewTreeObserver.OnComputeInternalInsetsListener`.
   - Supplies an `android.graphics.Region` generated from `Path.addCircle(petCenterX, petCenterY, radius)` matching the pet's exact coordinates.
   - Synchronizes touchable region in real-time as the pet drifts or is dragged.

6. **`com.dynamicisland.app.util.PetMoodManager`**:
   - Resolves pet emotional state according to a strict priority hierarchy:
     1. `CRITICAL_BATTERY` ($<5\%$)
     2. `CHARGING` (Power connected)
     3. `LOW_BATTERY` ($<15\%$)
     4. `ANGRY` (Instagram open continuously $\ge 5\text{ min}$)
     5. `SAD` (Instagram in foreground)
     6. `TEXTING` (Messaging apps: WhatsApp, Discord, Messages, LinkedIn, Telegram, Signal, Slack)
     7. `NERD` (Learning apps: ChatGPT, Gemini, WPS, ONLYOFFICE, PDF readers, Google apps)
     8. `SLEEPY` (Local time curve $\ge 22:00$ / $10\text{ PM}$)
     9. `HAPPY` (Pet app open or tap interaction)
     10. `IDLE` (Default)
   - Tracks continuous session time for Instagram and resets to zero the instant the user exits.

4. **`com.dynamicisland.app.util.ForegroundAppDetector`**:
   - Polls `UsageStatsManager.queryEvents()` (`ACTIVITY_RESUMED` / `MOVE_TO_FOREGROUND`) every $500\text{ms}$.
   - Resolves package names to human-readable app titles.
   - Buffers a live history log for the in-app debug terminal (`DebugActivity.kt`).

5. **`com.dynamicisland.app.util.PhoneEventManager`**:
   - Lifecycle-managed `BroadcastReceiver` listening to `ACTION_USER_PRESENT`, `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`, and `ACTION_BATTERY_CHANGED`.

6. **`com.dynamicisland.app.DynamicIslandService`**:
   - Persistent Foreground Service running with `TYPE_APPLICATION_OVERLAY` and `specialUse` foreground service type.
   - Configured with `android:stopWithTask="false"` and `onTaskRemoved` auto-restart alarm so the overlay never dies when swiped from recent apps.
   - Hosts `TetheredPetStageView` anchored over the camera cutout $(cameraRefX, cameraRefY)$.

---

## 📦 Releases & Versioning Architecture

### Critical Rule for APK Tracking in `.gitignore`
- **ALWAYS allow `app/build/outputs/apk/debug/app-debug.apk` to be tracked via `!` negation rules in `.gitignore`**:
  ```gitignore
  !/app/build/
  /app/build/*
  !/app/build/outputs/
  /app/build/outputs/*
  !/app/build/outputs/apk/
  /app/build/outputs/apk/*
  !/app/build/outputs/apk/debug/
  /app/build/outputs/apk/debug/*
  !/app/build/outputs/apk/debug/app-debug.apk
  ```
- **WHY**: Git cannot re-include a file if its parent directory is excluded. Therefore, all parent directories up to `app-debug.apk` MUST be negated with `!`, while ignoring other subfolders using `/*`. Future agents MUST NEVER add a blanket `app/build/` rule that blocks tracking this APK!

### Automated Release Workflow & APK Renaming (`.github/workflows/release.yml`)
- **Triggers**: Push to `main` exclusively (or manual `workflow_dispatch`). Pushes to other branches (such as `optimization`) do NOT trigger releases.
- **Guaranteed Release on Every Push to `main`**:
  1. The workflow parses `versionName` from `app/build.gradle.kts` (`BASE_VER="v${RAW_VERSION}"`).
  2. **Dynamic Tag Creation**: The workflow checks if `v${BASE_VER}` already exists on GitHub:
     - If it does NOT exist: creates release tagged `v${BASE_VER}`.
     - If it ALREADY exists (e.g. subsequent push without bumping version): tags as `v${BASE_VER}.${{ github.run_number }}`.
     - **This guarantees a brand-new release card is ALWAYS published on GitHub on EVERY push to `main`**, regardless of whether an agent or developer remembered to bump `versionName`!
  3. **Guaranteed Fresh Compilation**: The workflow always executes `./gradlew assembleDebug` in CI. Even if a developer or agent pushes from another machine without compiling locally, the release asset is 100% freshly built from the latest commit code.
  4. **Release Asset Renaming**: The workflow copies the APK to **`PixelPet-${TAG_NAME}.apk`** (e.g. `PixelPet-v1.0.2.apk`), and attaches both `PixelPet-${TAG_NAME}.apk` and `app-debug.apk` to the release.

### Step-by-Step Instructions for Future Agents Bumping Versions
1. **Update Version**: Edit `versionCode` and `versionName` in `app/build.gradle.kts` (e.g. `1.0.2` -> `1.0.3`).
2. **Update Documentation**: Update version badges and release notes in `README.md` and `AGENTS.md`.
3. **Recompile Debug APK**: Run `./gradlew assembleDebug` so `app/build/outputs/apk/debug/app-debug.apk` is refreshed locally.
4. **Run Unit Tests**: Run `./gradlew test` and ensure all tests pass.
5. **Verify Git Tracking**: Run `git status` — confirm `app/build/outputs/apk/debug/app-debug.apk` appears as tracked/modified (NOT ignored).
6. **Commit & Push to `main`**: Push to `main` to trigger `.github/workflows/release.yml`, which compiles, renames, and publishes the release assets automatically.
---

## 🔒 Security & Google Play Protect Policies

- **NEVER declare `BIND_ACCESSIBILITY_SERVICE`**: Sideloaded APKs with accessibility services are automatically blocked by Google Play Protect as Potentially Harmful Applications (PHA).
- **Usage Stats**: Uses `android.permission.PACKAGE_USAGE_STATS`. On Android 13/14+, users must unlock sideloaded restricted settings once (Settings $\to$ Apps $\to$ Pixel Pet $\to$ 3 dots $\to$ "Allow restricted settings").
- **Overlay Security**: Uses standard `android.permission.SYSTEM_ALERT_WINDOW`.

---

## 🛠️ Build & Verification Instructions

### Requirements
- JDK 17
- Android SDK 34 (Build tools 34.0.0)

### Commands
```bash
# Run unit tests
./gradlew test

# Compile debug APK
./gradlew assembleDebug

# Install to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Fast-grant permissions via ADB (optional for developers)
adb shell appops set com.dynamicisland.app SYSTEM_ALERT_WINDOW allow
adb shell appops set com.dynamicisland.app ACCESS_RESTRICTED_SETTINGS allow
adb shell appops set com.dynamicisland.app GET_USAGE_STATS allow
```
