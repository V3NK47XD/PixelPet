# Pixel Pet 🐣

[![Latest Release](https://img.shields.io/github/v/release/V3NK47XD/PixelPet?color=F59E0B&label=Release)](https://github.com/V3NK47XD/PixelPet/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/V3NK47XD/PixelPet/release.yml?branch=main&label=Build%20%26%20Release)](https://github.com/V3NK47XD/PixelPet/actions)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.1)-60A5FA?logo=android)](https://github.com/V3NK47XD/PixelPet/releases/latest)

> A cute, expressive pixel-art companion that lives near your phone's front camera, tethered by an organic physics leash, and reacts to your phone activity!

**Current Version:** `v1.0.1`  
**Direct Download:** [`PixelPet-v1.0.1.apk`](https://github.com/V3NK47XD/PixelPet/releases/latest/download/PixelPet-v1.0.1.apk) | [`app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)

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

---

### ⚙️ In-App Settings & Live Preview
* **Front Camera Presets**:
  - `◀ Top Left` (for left corner punch-hole cameras)
  - `⏺ Top Center` (for center cutouts)
  - `Top Right ▶` (for right corner cameras)
* **Precise Sliders**:
  - **Camera X Offset**: $-150\text{dp}$ to $+150\text{dp}$
  - **Camera Y Offset**: $0\text{dp}$ to $120\text{dp}$
  - **Pet Size**: $32\text{dp}$ to $96\text{dp}$ (default $54\text{dp}$)
  - **Leash Length**: $18\text{dp}$ to $60\text{dp}$ (default $36\text{dp}$)
  - **Animation Intensity**: $50\%$ to $150\%$
  - **Instagram Anger Timer**: $1\text{ min}$ to $15\text{ min}$ (default $5\text{m}$)
  - **Bedtime Hour**: $19:00$ ($7\text{ PM}$) to $01:00$ ($1\text{ AM}$)
* **Live Tether Stage Preview**: See the anchor ring, curved leash, and pet floating live inside the settings screen, with a **"Drift to Next Spot 🎲"** test button.

---

## 📦 Releases & Versioning

| Version | Date | Key Highlights |
| :--- | :--- | :--- |
| **`v1.0.1`** | 2026-10-21 | Battery efficiency optimization: screen on/off pausing, zero IPC idle hovering, static bodyMap, debounced writes, texting app mood (WhatsApp/Discord/Messages/LinkedIn). |
| **`v1.0.0`** | 2026-10-21 | Tethered camera anchor, physics leash, autonomous drift, leash-constrained dragging, sleep/wake tap toggle, multi-tap anger tantrum, contextual app moods. |

* **Latest Release**: [PixelPet Releases Page](https://github.com/V3NK47XD/PixelPet/releases/latest)
* **Direct APK Download**: [`PixelPet-v1.0.1.apk`](https://github.com/V3NK47XD/PixelPet/releases/latest/download/PixelPet-v1.0.1.apk)
* **Tracked Build Output**: [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)

### Automated CI/CD Release Workflow
Whenever a new commit to `main` is pushed, the [GitHub Actions Release Workflow](.github/workflows/release.yml) automatically builds the application, packages `PixelPet-v${VERSION}.apk` and `app-debug.apk`, and publishes them directly to GitHub Releases. Pushes to other branches (such as `optimization`) do not trigger releases.
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
