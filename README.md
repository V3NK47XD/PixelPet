# Pixel Pet 🐣

> A cute, expressive pixel-art companion that lives near your phone's front camera and reacts to what you do!

---

## ✨ Features

### 📷 Tethered Front Camera World
* **Front Camera as Anchor**: The $(X, Y)$ offset you set in the app anchors the pet directly over your front camera lens with a subtle golden ring.
* **Curved Physics Leash**: A natural, sagging string (`Path.quadTo`) connects the camera anchor directly to the pet's head sprout.
* **Custom Leash Styles**: Choose from 🎀 Red Ribbon, 🎗️ Golden Tether, 🧵 Soft Silver, or 🌿 Mint Green with adjustable length ($18\text{dp}$ to $60\text{dp}$).
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

### 🎭 Contextual Character Moods

| Mood | Trigger Condition | Visuals & Animation |
| :--- | :--- | :--- |
| **😴 Idle** | Default / Home screen | Warm sunny yellow body, gentle breathing squash & stretch, natural blinking, looking around, sprout sway. |
| **🥺 Sad** | Instagram opened | Face turns pale blue (`#90CAF9`), droopy posture, tiny quivering frown, animated cyan teardrop trickles down cheek. |
| **😡 Angry** | Instagram open $> 5\text{ min}$ *(configurable)* | Turns furious crimson (`#EF5350`), angry V-eyebrows, puffed cheeks, high-frequency rage vibration, steam puffs rise from head. *(Timer resets on exit).* |
| **🤓 Nerd** | Google Apps, ChatGPT, Gemini, WPS, PDF | Cute glasses drop down from above and bounce onto face, eyes scan left-to-right reading text, occasional thinking lightbulb (`💡`). |
| **🌙 Sleepy** | Late night (local time $\ge 22:00$) | Muted lavender body (`#B39DDB`), droopy eye slits, slow nodding, wide yawns, floating animated **"Zzz"** particles. |
| **⚡ Charging** | Power connected | Soft mint green face (`#A7F3D0`) with floating energy sparkles and relaxed expression. |
| **🪫 Low Battery** | Battery $<15\%$ | Amber face (`#FB923C`), wide worried eyes glancing nervously toward the phone's battery indicator. |
| **💀 Critical Battery**| Battery $<5\%$ | Pale red face (`#F87171`), dramatic fainting collapse ($55^\circ$ tilt) with `X X` eyes and exhausted sigh. |
| **😄 Happy** | Phone unlocked / Pet app open | Joyful celebratory hops, closed crinkle eyes (`^ _ ^`), star sparkles. |

---

### 👆 Touch Reactions *(Non-Draggable)*
* **Never Draggable on Overlay**: The pet stays anchored to its camera coordinates and cannot be moved or dragged on screen.
* **Contextual Poke Reactions**:
  - Tap while **Sleepy**: Startles awake with wide eyes, shakes head, and yawns.
  - Tap while **Sad**: Looks surprised, then gives a small comforted smile.
  - Tap while **Angry**: Brief anger flare / steam puff, then calms down.
  - Tap while **Nerd**: Pushes up its glasses with a glint shine.
  - Tap while **Normal**: Winks, waves, or giggles.
* **$100\%$ Click-Through Mode**: Can be toggled off in settings (`FLAG_NOT_TOUCHABLE`) so touches pass straight through to apps below.

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

## 🚀 Building & Installing

### Prerequisites
* JDK 17
* Android SDK 34

### Build APK
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
