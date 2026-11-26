<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" alt="Warden Icon" width="120" />

# Warden

**A lightweight, self-enforcing digital focus guardian for Android.**

Warden monitors on-screen text in real time through an Android `AccessibilityService`, intercepts content containing keywords you have flagged as distracting (after masking whitelisted exception phrases), and returns you to the home screen before doomscrolling begins. When you genuinely need a temporary exception, you must argue your case to an adversarial **Gemini AI Gatekeeper** whose default answer is **NO**.

> **CRITICAL WARNING:**
> Once enabled, Warden's self-protection and optional Device Administrator anti-uninstall lock are intentionally difficult to bypass. Install and use Warden at your own risk.
>
> *Privacy Note:* Warden has zero analytics, telemetry, or third-party trackers. All keyword scanning runs 100% offline on-device. The only network request is to `generativelanguage.googleapis.com` when you explicitly configure a Gemini API key and submit an AI Gatekeeper request or refresh models.

[![Release](https://github.com/mehad605/Warden/actions/workflows/release.yml/badge.svg)](https://github.com/mehad605/Warden/actions/workflows/release.yml)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Installation](#installation) · [Build & Test](#build-run-and-test) · [Architecture](docs/ARCHITECTURE.md) · [How-To Recipes](docs/HOW_TO.md) · [Releasing](docs/RELEASING.md) · [Contributing](CONTRIBUTING.md)

</div>

---

## Features

| Feature | Description |
|---|---|
| **Real-Time Keyword Blocking** | Whole-word regex scan (`(?<!\w)(?:...)+(?!\w)`) over visible `text` and `contentDescription` nodes with ±7-word context feedback |
| **Whitelist Masking** | Whitelisted phrases (e.g., `"basketball shorts"`) are masked before keyword scanning to prevent false positives |
| **Configurable Grace Period** | 0–5s delay (default 2s) with re-check before `GLOBAL_ACTION_HOME` fires |
| **Adversarial AI Gatekeeper** | Direct HTTPS Gemini REST client (no heavy SDK) adjudicating app exemptions, extensions, ignore removals, and full blocker pauses with optional screenshot proof |
| **Anti-Uninstall & Self-Protection** | Device Administrator registration paired with Accessibility interception of Force Stop, Clear Data, Accessibility revocation, and Device Admin deactivation |
| **Password Lock** | Strong password protection (SHA-256) gating keyword/whitelist removal, password updates, and Anti-Uninstall deactivation |
| **Active Ignored Apps & Countdown** | Live countdown for temporary exemptions, decrease-only slider, automatic expiry pruning, and zero polling when idle |
| **Merge-Only Backup & Restore** | JSON export (optionally excluding secrets, always clearing anti-uninstall flags) and merge-only import so restoring cannot weaken active rules |
| **Bounded On-Device Crash Logs** | Rotated `crash_log.txt` (128 KiB cap + single backup) with zero main-thread I/O at startup |

---

## Installation

1. Download the signed release APK (`Warden-vX.Y.Z.apk`) and `SHA256SUMS.txt` from [**GitHub Releases**](https://github.com/mehad605/Warden/releases).
2. Verify the checksum: `sha256sum -c SHA256SUMS.txt`.
3. Install on Android **8.0 (API 26)** or higher (tested against API 30 / Realme Narzo 20 target constraints).
4. Grant **Accessibility Service** access (and optionally **Device Administrator** for Anti-Uninstall).

---

## Build, Run, and Test

**Requirements:** JDK 17 (or 21), Android SDK Platform 34, Gradle Wrapper (`./gradlew`).

```bash
# Clone
git clone https://github.com/mehad605/Warden.git
cd Warden

# Run all static checks & unit/architecture tests
./gradlew ktlintCheck detekt lint testDebugUnitTest

# Build debug APK and install on a connected device
./gradlew installDebug

# Build R8-minified, resource-shrunk release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/Warden-release.apk
```

---

## Release Process

Releases are produced deterministically from SemVer Git tags (`vMAJOR.MINOR.PATCH`) via [`.github/workflows/release.yml`](.github/workflows/release.yml) with Gradle wrapper validation, `apksigner verify`, size budget enforcement, SBOM generation, SHA-256 checksums, and build provenance attestation. See [docs/RELEASING.md](docs/RELEASING.md) for key rotation and dry-run instructions.

---

## Documentation

| Document | Purpose |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layered architecture, Mermaid diagrams, package map, runtime footprint floor |
| [docs/HOW_TO.md](docs/HOW_TO.md) | Step-by-step recipes: add a feature, screen, setting, dependency, or replace the network layer |
| [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) | Justification and size cost of every runtime dependency |
| [docs/RELEASING.md](docs/RELEASING.md) | Release pipeline architecture, dry runs, and signing key rotation |
| [docs/adr/](docs/adr/) | Architecture Decision Records explaining every major design choice |
| [docs/how-it-works.md](docs/how-it-works.md) | Deep dive into the keyword matching and self-protection engine |
| [docs/ai-gatekeeper.md](docs/ai-gatekeeper.md) | Adversarial prompt rules, local validation, and multimodal proof |
| [docs/anti-uninstall.md](docs/anti-uninstall.md) | Device Administrator & Accessibility self-protection details |
| [docs/configuration.md](docs/configuration.md) | Settings reference and JSON schema |

---

## Emergency Uninstall

If you enabled Anti-Uninstall protection and are locked out of Settings:

1. **Via Safe Mode:** Reboot into Android Safe Mode (which temporarily disables third-party Accessibility Services), open **Settings → Security → Device admin apps** to deactivate Warden, and uninstall normally.
2. **Via ADB (from a computer):**
   ```bash
   adb shell settings delete secure enabled_accessibility_services
   # Deactivate Warden in Device Admin settings on the phone, then:
   adb shell pm uninstall com.warden.app
   ```

---

## License

MIT License — see [LICENSE](LICENSE).
