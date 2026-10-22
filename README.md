# Pixel Pet 🐣

[![Latest Release](https://img.shields.io/github/v/release/V3NK47XD/PixelPet?color=F59E0B&label=Release)](https://github.com/V3NK47XD/PixelPet/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/V3NK47XD/PixelPet/release.yml?branch=main&label=Build%20%26%20Release)](https://github.com/V3NK47XD/PixelPet/actions)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.2)-60A5FA?logo=android)](https://github.com/V3NK47XD/PixelPet/releases/latest)

> A cute, expressive pixel-art companion that lives near your phone's front camera, tethered by an organic physics leash, and reacts to your phone activity!

**Current Version:** `v1.0.2`  
**Direct Download:** [`PixelPet-v1.0.2.apk`](https://github.com/V3NK47XD/PixelPet/releases/latest/download/PixelPet-v1.0.2.apk) | [`app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)

---

## ✨ Features

### 📷 Tethered Front Camera World
* **Front Camera as Anchor**: The $(X, Y)$ offset you set in the app anchors the pet directly over your front camera lens with a subtle golden ring.
* **Curved Physics Leash**: A natural, sagging string (`Path.quadTo`) connects the camera anchor directly to the pet's head sprout.
* **Custom Leash Styles**: Choose from 🎀 Red Ribbon (`#F43F5E`), 🎗️ Golden Tether (`#F59E0B`), 🧵 Soft Silver (`#E2E8F0`), or 🌿 Mint Green (`#34D399`) with adjustable length ($18\text{dp}$ to $60\text{dp}$).
* **Pure Borderless Transparency**: No dynamic island pills, no capsules, no background panels, and no borders. Only the pet and its leash float over your screen.

---

### 🎈 Autonomous Floating & Drifting (No Walking)
* **Buoyant Floating**: Moves with continuous gentle oscillation ($\pm 2.8\text{dp}$) and realistic string tension tilts ($\pm 12^\circ$).
* **Never Sits Still $>10$ Seconds**: Every $4.5$ to $8.5$ seconds, the pet gently floats to a new organic spot:
  - Floats to the left of the camera
  - Floats to the right of the camera
  - Dangles directly underneath like a pendant
  - Leans to either side
  - Hovers close to stare curiously at the lens
* **Zero Walking Back and Forth**: The pet is a buoyant, tethered floating companion, not an artificial walker.

---

### 👆 Dragging & Touch Interactions
* **Leash-Constrained Dragging**: You can touch and drag the pet around the screen with your finger! The pet's movement is strictly clamped to the leash radius—it can never escape its tether.
* **Elastic Snap-Back**: While dragged, the leash stretches taut. When released, the pet springs back to a natural hover spot with an elastic overshoot bounce (`OvershootInterpolator(2.2f)`).
* **Sleep / Wake Toggle on Tap**:
  - Tap while **Sleepy 😴**: The pet immediately startles awake, opens its eyes wide, yawns, and switches into active/happy mode!
  - Tap while **Awake (at Night) 🌙**: The pet performs a wide yawn, nods off, and goes back to sleep with floating **"Zzz"** particles.
* **Rapid Multi-Tap Temper Tantrum 😡**:
  - Tapping the pet rapidly ($3+$ times within $650\text{ms}$) makes it lose its patience and have a temper tantrum!
  - Turns furious crimson red (`#EF5350`), angry V-eyebrows, puffed cheeks, rage vibration shake, and steam puffs.
  - Automatically calms down after **$3.8$ seconds**, blushes embarrassedly, and returns to its previous mood.
* **Single Tap Reactions**: Winks, waves its arm, giggles, or pushes up its glasses.
* **Shape-Matched Touch Detection**: Uses an internal window inset region (`TOUCHABLE_INSETS_REGION`) matching the pet's exact circular body. Only touches on the pet itself are captured for dragging and tapping—**100% of touches everywhere else (camera, status bar, screen edges, and apps underneath) pass straight through uninterrupted!**
* **$100\%$ Click-Through Mode**: Can be toggled in settings (`FLAG_NOT_TOUCHABLE`) so touches pass straight through even when touching the pet.
---

### 🎭 Contextual Character Moods

| Mood | Trigger Condition | Visuals & Animation |
| :--- | :--- | :--- |
| **😴 Idle** | Default / Home screen | Warm sunny yellow body, gentle breathing squash & stretch, natural blinking, looking around, sprout sway. |
| **🥺 Sad** | Instagram opened | Face turns pale blue (`#90CAF9`), droopy posture, tiny quivering frown, animated cyan teardrop trickles down cheek. |
| **😡 Angry** | Instagram open $> 5\text{ min}$ *(configurable)* | Turns furious crimson (`#EF5350`), angry V-eyebrows, puffed cheeks, high-frequency rage vibration, steam puffs rise from head. *(Timer resets on exit).* |
| **💬 Texting** | WhatsApp, Discord, Messages, LinkedIn, Telegram | Cheerful teal face (`#2DD4BF`), floating speech bubble with cycling typing dots (`• • •`), rapid finger typing animation! |
| **🤓 Nerd** | Google Apps, ChatGPT, Gemini, WPS, PDF | Cute glasses drop down from above and bounce onto face, eyes scan left-to-right reading text, occasional thinking lightbulb (`💡`). |
| **🌙 Sleepy** | Late night (local time $\ge 22:00$) | Muted lavender body (`#B39DDB`), droopy eye slits, slow nodding, wide yawns, floating animated **"Zzz"** particles. |
| **⚡ Charging** | Power connected | Soft mint green face (`#A7F3D0`) with floating energy sparkles and relaxed expression. |
| **🪫 Low Battery** | Battery $<15\%$ | Amber face (`#FB923C`), wide worried eyes glancing nervously toward the phone's battery indicator. |
| **💀 Critical Battery**| Battery $<5\%$ | Pale red face (`#F87171`), dramatic fainting collapse ($55^\circ$ tilt) with `X X` eyes and exhausted sigh. |
| **😄 Happy** | Phone unlocked / Pet app open | Joyful celebratory hops, closed crinkle eyes (`^ _ ^`), star sparkles. |

### 🔑 Virtual Keychain Physics & Natural Hold Calibration
* **Natural Neutral Position Calibration**: First-launch interactive popup allows you to hold your phone in your usual comfortable reading position. Pixel Pet auto-calibrates this as $(0, 0)$ equilibrium so the pet hangs naturally without awkward offsets!
* **Accelerometer-First Dynamics**: Direct 3-axis accelerometer sensor processing provides 100% universal compatibility across all Android phones, responding with zero delay to phone tilt, swings, and waves.
* **Rope Keychain Pendulum & Inertia Lag**: Tilting the phone swings the pet in a true gravitational pendulum arc; moving the phone quickly applies inertia lag so the pet trails behind and overshoots when stopping!
* **Rapid Shake Shiver Ripples**: When you shake the phone rapidly, dynamic high-frequency standing waves ripple and shiver through the wire, naturally damping out as it settles!
* **Duolingo-Style 2.5D Hardware**: Dual-tone depth wire styling (darker shadow under core), a rotating metallic jump-ring hinge at the camera anchor, and a golden clasp bead at the sprout knot.
* **Expressive Secondary Motion**: The pet's head sprout flexes in counter-phase to swing velocity, with playful squishy bounce when shaken!
* **No Scrolling Needed**: All core controls, live preview, presets, and dials fit neatly on a single non-scrolling screen.
* **Retro Rotary Knobs**: Replaced vertical sliders with compact 270° rotary volume dials (`CAM X`, `CAM Y`, `SIZE`, `LEASH`) with tactile haptic feedback.
* **Cute Peach Palette**: Warm peach (`#FF9F68`) and light orange accents with dark terracotta surfaces.
* **Separated Developer Tools**: Developer features (Touch Debug Mode, Foreground App Log, Anger Timer dial, Nerd app selectors) are accessible via the **⚙️ Dev Tools** sheet.
---

## 📦 Releases & Versioning

| Version | Date | Key Highlights |
| :--- | :--- | :--- |
| **`v1.0.2`** | 2026-10-22 | Virtual keychain physics (swing inertia, rapid shake wire shivering & ripples), Duolingo 2.5D wire styling, single APK release pipeline, retro rotary dials & peach theme. |
| **`v1.0.1`** | 2026-10-21 | Battery efficiency optimization: screen on/off pausing, zero IPC idle hovering, static bodyMap, debounced writes, texting app mood (WhatsApp/Discord/Messages/LinkedIn). |
| **`v1.0.0`** | 2026-10-21 | Tethered camera anchor, physics leash, autonomous drift, leash-constrained dragging, sleep/wake tap toggle, multi-tap anger tantrum, contextual app moods. |

* **Latest Release**: [PixelPet Releases Page](https://github.com/V3NK47XD/PixelPet/releases/latest)
* **Direct Single APK Download**: [`PixelPet-v1.0.2.apk`](https://github.com/V3NK47XD/PixelPet/releases/latest/download/PixelPet-v1.0.2.apk)

### Automated CI/CD Release Workflow
Whenever a new commit to `main` is pushed, the [GitHub Actions Release Workflow](.github/workflows/release.yml) packages strictly the single locally-signed APK `PixelPet-${TAG_NAME}.apk` and publishes it directly to GitHub Releases. Pushes to other branches (such as `optimization`) do not trigger releases.
---

## 🚀 Building & Installing

### Prerequisites
* JDK 17
* Android SDK 34 (Build tools 34.0.0)

### Build APK Locally
```bash
./gradlew assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Android 13/14 Restricted Settings
On Android 13 and 14+, sideloaded APKs have sensitive permissions locked by default.
1. In the app, tap **Fix Restricted 🔓** (or go to *Phone Settings $\to$ Apps $\to$ Pixel Pet*).
2. Tap the **3 dots (⋮)** in the top-right corner.
3. Tap **Allow restricted settings** (confirm your PIN/fingerprint).
4. Return to the app and tap **Grant Access**.

---

## 📄 License
MIT License.
 
