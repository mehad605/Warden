<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" alt="Warden Icon" width="120" />

# Warden

**A self-enforcing digital focus guardian for Android.**

Warden is built for people who are serious about quitting doomscrolling, cutting down on mindless content consumption, and reclaiming their attention. If you've tried every ordinary app blocker and found yourself disabling it within five minutes of installing it — Warden is for you.

It monitors every window on your device in real time, intercepts content containing words you've flagged as distracting, and sends you straight back to the home screen before you even realize you've fallen into the scroll. When you genuinely need an exception, you have to argue your case to an **AI gatekeeper** — and it defaults to no.

> ⚠️ **Fair warning:** Warden is intentionally difficult to bypass and even harder to uninstall when protection is fully enabled. That's the point. If you need to remove it in an emergency, see the [Emergency Uninstall](#emergency-uninstall) section below.

[![Build Status](https://github.com/mehad605/Warden/actions/workflows/release.yml/badge.svg)](https://github.com/mehad605/Warden/actions/workflows/release.yml)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Download APK](#installation) · [Documentation](docs/) · [Architecture](docs/architecture.md) · [FAQ](#faq) · [Contributing](CONTRIBUTING.md)

</div>

---

## Who Is This For?

Warden is for people who:

- Catch themselves mindlessly opening Instagram, Reddit, or YouTube Shorts every few minutes without even meaning to
- Want to block specific topics (e.g. news, sports scores, political content) across *all* apps, not just at the app level
- Have tried "gentle" blockers and disabled them within seconds during a weak moment
- Need friction that is strong enough to actually hold — even against a determined, impulsive version of themselves
- Are willing to trade some convenience for genuine behavioural change

Unlike passive blockers, Warden doesn't ask for your cooperation. The moment a blocked keyword appears on screen, it sends you home — no confirmation dialog, no "are you sure?" prompt.

---

## Features

| Feature | Description |
|---|---|
| 🔍 **Keyword Blocking** | Real-time accessibility scan of every on-screen text node — blocks at the exact moment a keyword appears |
| ⏱️ **Grace Period** | Configurable delay before the home action fires, avoiding false positives on transient text |
| 🤖 **AI Gatekeeper** | Gemini-powered chat dialog adjudicates exemption, pause, and extension requests — default answer is NO |
| 📸 **Screenshot Proof** | AI exemption requests can include a screenshot as evidence for borderline cases |
| 🛡️ **Anti-Uninstall** | Device Administrator registration prevents uninstallation, force-stop, and data-clear without deactivation |
| 🔑 **Password Lock** | Settings are gated behind a strong password to prevent impulsive self-bypass |
| 📦 **App Ignore List** | Permanently or temporarily exempt specific apps from keyword scanning |
| 📤 **Export / Import** | Full settings snapshot can be exported and restored as JSON |
| 📊 **Crash Logs** | On-device crash logger, shareable for debugging |

---

## Installation

### Option A — Download APK (Recommended)

1. Go to [**Releases**](https://github.com/mehad605/Warden/releases) and download the latest `Warden-debug.apk`.
2. On your Android device, enable **Install from Unknown Sources**:
   `Settings → Apps → Special App Access → Install Unknown Apps`
3. Open the downloaded APK and tap **Install**.
4. Follow the **Quick Setup** screen inside the app to grant permissions.

### Option B — Build from Source

```bash
git clone https://github.com/mehad605/Warden.git
cd Warden
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/Warden-debug.apk
```

**Requirements:** Android Studio Hedgehog or newer · JDK 17 · Android SDK 34

---

## Quick Setup

After installing, Warden walks you through three permissions:

1. **Accessibility Service** — core engine; required for all screen content scanning
2. **Display Over Other Apps** — needed for overlay features
3. **Device Administrator** *(optional but strongly recommended)* — enables anti-uninstall protection

Then add your first blocked keyword and you're protected.

---

## Documentation

Full technical and user documentation lives in the [`docs/`](docs/) folder:

| Document | Description |
|---|---|
| [How It Works](docs/how-it-works.md) | Deep dive into the blocking engine, accessibility pipeline, and home-press mechanism |
| [AI Gatekeeper](docs/ai-gatekeeper.md) | How Gemini evaluates exemption, pause, and extension requests |
| [Anti-Uninstall](docs/anti-uninstall.md) | Device Administrator integration and self-protection strategy |
| [Architecture](docs/architecture.md) | Module map, data flow diagram, and component overview |
| [Configuration](docs/configuration.md) | All settings, their purpose, and recommended values |

---

## Emergency Uninstall

Warden is designed to be hard to remove — that's intentional. If Anti-Uninstall is enabled, **the standard UI uninstallation path is completely blocked**. The app actively prevents you from force-stopping it, clearing its data, or deactivating its Device Admin status from your phone's settings. 

If you genuinely need to uninstall it and are locked out, you must use one of the two methods below.

### Method 1 — Safe Mode (No PC Required)

The easiest way to remove Warden in an emergency is to boot your Android device into **Safe Mode**. Safe Mode prevents downloaded third-party apps and background services (including Warden's accessibility service) from running.

1. **Boot into Safe Mode:** Usually done by holding the Power button, then long-pressing the "Power Off" icon on the screen until the "Reboot to safe mode" prompt appears.
2. **Deactivate Admin:** Go to `Settings → Security → Device admin apps` and deactivate Warden.
3. **Uninstall:** Go to `Settings → Apps → Warden` and uninstall it normally.
4. **Restart:** Restart your phone to exit Safe Mode.

### Method 2 — ADB (Requires PC)

If you have a computer with ADB installed and USB Debugging enabled on your phone, you can "blind" Warden by killing its Accessibility Service via terminal.

1. **Connect your phone to your PC** and verify ADB connection (`adb devices`).
2. **Kill the Accessibility Service:**
   ```bash
   adb shell settings delete secure enabled_accessibility_services
   ```
   *(This immediately stops Warden from monitoring your screen.)*
3. **Deactivate Admin:** On your phone, go to `Settings → Security → Device admin apps` and manually deactivate Warden.
4. **Uninstall the App:**
   ```bash
   adb shell pm uninstall com.warden.app
   ```

> **Note:** The standard ADB command `dpm remove-active-admin` will **fail** with a `SecurityException` because Warden is not built as a test-only app. You must use the methods above.

---

## FAQ

**Does Warden need an internet connection?**
Only for AI gatekeeper features (Gemini API calls). All blocking logic runs entirely offline.

**What happens if I don't set up a Gemini API key?**
Everything except the AI exemption dialog works normally. You can still use keyword blocking, the ignore list, and anti-uninstall — just without the AI gatekeeper.

**Will Warden drain my battery?**
The accessibility service runs continuously, but it is lightweight — it only processes events when the screen changes. Impact is comparable to other accessibility services like TalkBack.

**Can Warden read my messages or passwords?**
No. The accessibility service scans text visible on screen for keyword matching only. It does not log, store, or transmit any screen content.

**My keyword is getting blocked in apps I need. What do I do?**
Add those apps to the **Ignore List** in settings. They will be permanently exempt from scanning.

**The AI keeps rejecting my exemption request. What counts as proof?**
For a full blocker pause, you need to include an email address, a URL, or a screenshot. For app exemptions, write a detailed justification (minimum 7 sentences) explaining exactly why you need access and why it's work-related.

**I forgot my password. How do I reset it?**
There is no password recovery. You must uninstall the app and reinstall. If Device Admin is enabled, follow the [Emergency Uninstall](#emergency-uninstall) steps above first.

**Can I use Warden with a parental control setup?**
Warden is designed for self-regulation, not parental enforcement. It lacks remote management features. For parental controls, consider a dedicated MDM solution.

**Does Warden work with VPNs or split tunnelling?**
Yes. Warden doesn't touch network traffic — it operates entirely at the accessibility layer.

**How do I disable Warden without uninstalling?**
If you have your password, you can disable the blocker from within the app. If anti-uninstall is on and you're locked out, use ADB (see [Emergency Uninstall](#emergency-uninstall)).

---

## Privacy

Warden is a **local-first** application:

- All keyword lists, settings, and crash logs stay **on-device** in Android DataStore.
- No analytics, telemetry, or tracking of any kind is collected or transmitted.
- The only outbound network call is to the **Google Gemini API** when you explicitly trigger an AI exemption request. Your key is stored locally and never leaves your device through Warden.
- Accessibility data is processed entirely in-process and is never logged, stored, or transmitted.

---

## Requirements

- Android **8.0 (API 26)** or higher
- A Google Gemini API key — required only for AI gatekeeper features (free tier available at [aistudio.google.com](https://aistudio.google.com/app/apikey))

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 · **Target SDK:** 34
- **Architecture:** Single-activity, Fragment-based MVVM
- **Persistence:** Jetpack DataStore
- **AI:** Google Generative AI SDK (`generativeai`)
- **Build:** Gradle (Kotlin DSL)

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

---

## License

```
MIT License — Copyright (c) 2024 Warden Contributors
See LICENSE for full text.
```
