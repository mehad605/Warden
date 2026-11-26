# Warden — Developer & Technical Guide

This document is the technical entry point for engineers building, testing, extending, or releasing **Warden** (`com.warden.app`). For end-user overview and installation instructions, see [README.md](README.md).

---

## Technical Highlights

- **Single `:app` Android module** (`minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`, JDK 17, Kotlin 1.9.24, AGP 8.7.2, Gradle 8.10.2).
- **Zero heavy runtime SDKs or native `.so` libraries:** Only 6 direct AndroidX/Material dependencies (see [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)). Settings use single-process `AtomicFile` + `org.json` (`JsonSettingsRepository`); Gemini calls use platform `HttpURLConnection` + `org.json` (`HttpGeminiGatekeeperClient`).
- **Layered package-by-feature architecture** with automated boundary enforcement (`ArchitectureRulesTest`):
  - `core/{data,di,platform}` — `SettingsRepository`, lazy manual `AppContainer` + `AppContainerProvider`, `Clock`, rotating `CrashLogger` (128 KiB cap), `DeviceAdminController`, `AccessibilityPermissionChecker`, `InstalledAppsProvider` (`LruCache(64)`).
  - `data/models` — Backward-compatible persisted models (`Settings`, `KeywordBlocker`).
  - `feature/blocker/{domain,service,ui}` — Pure-Kotlin `KeywordMatcher` (equal-length whitelist masking + compiled alternation regex), `SelfProtectionEvaluator`, `BlockingDecisionEngine`, leak-safe `AccessibilityNodeScanner`, `BlockerCoordinator`, `BlockerViewModel` + self-reducing `BlockerAction`, and focused section binders.
  - `feature/gatekeeper/{domain,data,ui}` — `GatekeeperClient` boundary, `JustificationValidator`, `GatekeeperPromptBuilder`, `HttpGeminiGatekeeperClient`, and `BitmapImageEncoder`.
  - `feature/security/{domain,ui}` — `PasswordPolicy` (SHA-256) and `AntiUninstallUiController`.
  - `feature/backup/ui` — Off-main-thread SAF JSON export/import (`BackupDialogController`).
- **Release optimization:** R8 full mode, `shrinkResources`, English-only resources, pure-vector light/dark/monochrome adaptive launcher icons, log stripping, and embedded static baseline profile (`app/src/main/baseline-prof.txt`).

---

## Build, Test & Static Analysis

**Requirements:** JDK 17 (or 21), Android SDK Platform 34, Gradle Wrapper (`./gradlew`).

```bash
# Auto-format Kotlin files
./gradlew ktlintFormat

# Full quality gate (formatting, detekt, strict Android Lint, unit/Robolectric tests, release APK)
./gradlew ktlintCheck detekt lint testDebugUnitTest testReleaseUnitTest assembleRelease

# Generate deterministic CycloneDX 1.5 SBOM & verify release APK (signature, size budget, version, baseline profile, 0 .so)
./gradlew generateReleaseSbom -PVERSION_NAME=2.0.0
bash scripts/verify_release_apk.sh app/build/outputs/apk/release/Warden-release.apk 2.0.0 20000

# Build & install debug build on a connected device
./gradlew installDebug
```

### Automated Enforcement
- **`ktlintCheck`** (`ignoreFailures = false`) — Kotlin code formatting.
- **`detekt`** (`maxIssues: 0`, `config/detekt/detekt.yml`) — complexity, method length (≤40 LOC), swallowed exceptions, style.
- **Android Lint** (`abortOnError = true`, `warningsAsErrors = true`, `app/lint.xml`) — 0 errors, 0 warnings.
- **`ArchitectureRulesTest`** — enforces pure `*.domain` packages, data-layer isolation, acyclic package graph, ≤300 LOC/file, zero `!!`/`lateinit var`, zero global mutable singletons / static `Context` holders, and 100% public API KDoc coverage.
- **CI Workflows** (`.github/workflows/ci.yml` & `.github/workflows/release.yml`) — audited with `actionlint` and `zizmor`.

---

## Upgrade-Safe Invariants (Do Not Break)

Existing users upgrade in-place over prior releases. Never change:
1. **Manifest component FQCNs:**
   - `com.warden.app.ui.activity.FragmentActivity` (launcher shortcuts)
   - `com.warden.app.services.WardenService` (Accessibility Service permission grant)
   - `com.warden.app.receivers.WardenDeviceAdminReceiver` (Device Administrator registration)
2. **Persistence paths & formats:**
   - `filesDir/datastore/settings.json` (backward-compatible JSON keys with defaults in `SettingsJsonCodec`)
   - `SharedPreferences("app_prefs")["agreed_to_terms"]`
   - Lowercase SHA-256 hex password hashes in `PasswordPolicy`

---

## Technical Documentation Map

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layered architecture diagrams, package map, data flows, and runtime footprint floor |
| [docs/HOW_TO.md](docs/HOW_TO.md) | Step-by-step recipes: add a setting, feature, screen, dependency, or swap the AI client |
| [docs/adr/](docs/adr/) | Architecture Decision Records explaining every major design tradeoff |
| [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) | Justification and post-R8 cost of every runtime dependency |
| [docs/RELEASING.md](docs/RELEASING.md) | Deterministic SemVer versioning, dry runs, key rotation, and rollback |
| [docs/GITHUB_REPO_SETTINGS.md](docs/GITHUB_REPO_SETTINGS.md) | Repository rulesets, `release` environment, and supply-chain security settings |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branch workflow, commit conventions, and PR checklist |
