# Warden — Phase 0 Codebase & System Analysis

> Generated on `baseline-v1` (commit `84d3f0d`) prior to any code modifications.
> All facts are directly verified from source files, build scripts, merged release manifest, and tool output.

---

## 1. Purpose, Target Users, and Core User Flows

**Purpose:** Warden (`com.warden.app`) is a local-first, self-enforcing Android digital-focus and keyword-blocking app. It continuously inspects visible UI text via an Android `AccessibilityService` and sends the user back to the Home screen (`GLOBAL_ACTION_HOME`) whenever a blocked keyword appears on screen. To resist impulsive bypasses, Warden adds a password lock for destructive settings edits, an adversarial Google Gemini AI Gatekeeper for requesting temporary exceptions or pausing the blocker, and Device Administrator + Accessibility self-protection against Force Stop, Clear Data, Accessibility revocation, and uninstallation.

**Target Users:** Android users (specifically targeting resource-constrained devices like Realme Narzo 20 — Helio G85, 4 GB RAM, Android 11 / API 30, aggressive OEM background killing) who want high-friction, low-overhead self-regulation without cloud accounts, telemetry, or subscriptions.

**Core User Flows:**
1. **Onboarding & Disclaimer:** First launch displays a mandatory, non-cancelable disclaimer dialog (`FragmentActivity`). Accepting persists `agreed_to_terms = true` in `app_prefs` SharedPreferences and mounts `KeywordBlockerFragment`.
2. **Permission Setup:** `KeywordBlockerFragment` checks Accessibility Service status on every `onResume()` and surfaces an error-styled banner linking directly to Warden's Accessibility Settings entry.
3. **Keyword & Whitelist Management:** Users add/remove blocked keywords and whitelisted exception phrases (chips), with URL prefix normalization and a confirmation prompt for the risky keyword `"sex"`.
4. **Real-Time Blocking & Grace Period:** `WardenService` streams `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` events through a conflated coroutine `Channel` to `KeywordBlocker`. After masking whitelisted substrings, it runs a word-boundary regex over all visible node `text` and `contentDescription` values, shows a context toast (7 words before/after), and triggers `GLOBAL_ACTION_HOME` immediately (grace = 0s) or after re-checking once the grace period (1–5s, default 2s) elapses.
5. **AI Gatekeeper (Gemini):** Users configure a Gemini API key and refresh/select a model. Turning off the blocker, temporarily ignoring an app, removing an app from the ignore list, or extending an active exemption opens the AI Gatekeeper dialog (`DialogAiChatBinding`), which enforces local text validation (≥7 sentences, ≥5 words/sentence, >50% unique words per sentence unless removing an ignored app), optionally attaches a screenshot, sends a strict JSON prompt to Gemini, updates `noCount` on rejection, or applies the granted exemption/pause on approval.
6. **Active Ignored Apps & Countdown:** Displays permanent and active temporary ignored apps with a 1-second countdown timer, automatic expiry cleanup, tap-to-decrease duration slider, and `+` button to request an AI extension.
7. **Security & Self-Protection:** Password protection (SHA-256 hash; ≥6 chars, letter + digit) gates removing keywords/whitelists, changing/removing the password, and disabling Anti-Uninstall. Anti-Uninstall registers `WardenDeviceAdminReceiver` and pairs it with Accessibility-based interception of Settings screens showing `"warden"` alongside deactivate/uninstall/force-stop/clear-data/accessibility-disable actions (with a 180-second grace window during activation).
8. **Backup & Restore:** Exports `Settings` as JSON (`Warden.json`) with optional password/API-key exclusion (always clearing anti-uninstall flags) and performs a merge-only import for blocker active state, blocked keywords, whitelisted keywords, and ignored apps.

---

## 2. Architecture As It Is Today

### Module Structure
Single-module Gradle project (`rootProject.name = "BlockWords"`, single module `:app`).

### Entry Points & Android Components (Verified via `apkanalyzer manifest print`)
| Component | Type | Exported | Purpose / Status |
|---|---|---|---|
| `com.warden.app.Warden` | `Application` | N/A | Installs `CrashLogger` as default `UncaughtExceptionHandler` in `onCreate()`. |
| `com.warden.app.ui.activity.FragmentActivity` | `Activity` (LAUNCHER) | `true` | Hosts disclaimer dialog and `KeywordBlockerFragment`. Has a permanently hidden `BottomNavigationView`. |
| `com.warden.app.ui.activity.SelectAppsActivity` | `Activity` | `false` | **Dead code** — never launched anywhere in the codebase. |
| `com.warden.app.services.WardenService` | `AccessibilityService` | `true` (`BIND_ACCESSIBILITY_SERVICE`) | Core background engine forwarding accessibility events to `KeywordBlocker`. |
| `com.warden.app.receivers.WardenDeviceAdminReceiver` | `DeviceAdminReceiver` | `true` (`BIND_DEVICE_ADMIN`) | Enables OS-level Device Admin uninstall prevention; shows enable/disable toasts. |
| `androidx.startup.InitializationProvider` | `ContentProvider` (merged) | `false` | Runs `EmojiCompatInitializer`, `ProcessLifecycleInitializer`, `ProfileInstallerInitializer` on every process start. |
| `androidx.room.MultiInstanceInvalidationService` | `Service` (merged) | `false` | **Unwanted artifact** from unused Room dependency. |
| `androidx.profileinstaller.ProfileInstallReceiver` | `BroadcastReceiver` (merged) | `true` (`DUMP`) | Merged via Jetpack startup/appcompat transitive deps. |
| `com.journeyapps.barcodescanner.CaptureActivity` | `Activity` (merged) | `false` | **Unwanted artifact** from unused ZXing dependency (also injects `CAMERA` permission + 6 hardware `<uses-feature>` tags!). |

**Workers:** None.
**Permissions in merged manifest:** `INTERNET`, `QUERY_ALL_PACKAGES`, `com.warden.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, plus unwanted merged permissions `moe.shizuku.manager.permission.API_V23` (unused Shizuku) and `android.permission.CAMERA` (unused ZXing).

### Current Package & Dependency Graph
```text
com.warden.app
├── Warden.kt                         ──► CrashLogger.kt
├── Constants.kt                      [DEAD]
├── CrashLogger.kt                    (unbounded append to filesDir/crash_log.txt)
├── blockers/
│   ├── BaseBlocker.kt                [NEAR-DEAD: methods never called]
│   └── KeywordBlocker.kt (399 LOC)   ──► BaseBlockingService, DataStoreManager (via service)
├── data/models/
│   ├── FocusBlockMode.kt             [DEAD]
│   ├── KeywordBlocker.kt             (data class)
│   └── Settings.kt                   (data class)
├── receivers/
│   └── WardenDeviceAdminReceiver.kt
├── services/
│   ├── BaseBlockingService.kt        ──► DataStoreManager
│   └── WardenService.kt              ──► KeywordBlocker, CrashLogger
├── ui/
│   ├── activity/
│   │   ├── FragmentActivity.kt       ──► KeywordBlockerFragment
│   │   └── SelectAppsActivity.kt     [DEAD] ──► AppFilter [DEAD]
│   └── fragments/main/reducers/blockertools/keywordBlocker/
│       ├── KeywordBlockerFragment.kt (1,155 LOC!) ──► ViewModel, GeminiManager, PermissionUtils, WardenService, WardenDeviceAdminReceiver
│       └── KeywordBlockerViewModel.kt (222 LOC)   ──► DataStoreManager, KeywordBlocker (constant)
└── utils/
    ├── AppFilter.kt                  [DEAD]
    ├── ColorUtils.kt                 [DEAD]
    ├── DataStore.kt                  (MultiProcessDataStoreFactory + GsonSerializer + singleton INSTANCE)
    ├── GeminiManager.kt              (object singleton; GenerativeModel SDK + HttpURLConnection + Gson)
    ├── PackageFinderTools.kt         [DEAD]
    ├── PermissionUtils.kt
    └── TimeTools.kt                  [DEAD]
```

### Data Flow & Architectural Flaws Found
1. **No layering or boundaries:** `KeywordBlockerFragment` (1,155 LOC) directly performs content-resolver file I/O on the main thread, calls `GeminiManager` directly, queries `PackageManager` directly, invokes `DevicePolicyManager` directly, validates password strength directly, and constructs 6 dialogs inline.
2. **DataStore & Broadcast redundancy + coroutine leak:** `DataStoreManager` uses `MultiProcessDataStoreFactory` (dragging in 4 native `libdatastore_shared_counter.so` binaries) even though Warden runs in a single process. Worse, `KeywordBlockerViewModel` sends a broadcast `INTENT_ACTION_REFRESH_CONFIG` on every setting write, and `KeywordBlocker.refreshReceiver` (registered with `RECEIVER_EXPORTED`!) calls `setupBlocker(service)` again, launching a **new uncancelled `CoroutineScope(Dispatchers.IO)` collector on every config update** — leaking coroutines and memory over time.
3. **AccessibilityNodeInfo memory leaks & hot-path allocations:**
   - `KeywordBlocker.scanNodeForBlockedKeyword()` never recycles child `AccessibilityNodeInfo` instances, and the `rootNode` obtained on line 230 and `currentRoot` on line 258 are never recycled.
   - `antiUninstallSafePackages` allocates a new 17-element `Set` on every accessibility event.
   - `lastEventTimeStamp` is initialized to `0L` and never updated, so `refreshCooldown` (2000ms) is completely ineffective.
   - `extractContext()` compiles `"\\s+".toRegex()` twice per match.
   - When `ignoreGracePeriodSeconds == 0`, the blocked toast is posted twice (once directly, once inside `pressHome`).
4. **Unbounded disk & memory growth:** `CrashLogger` appends to `crash_log.txt` indefinitely with no rotation or size cap; `KeywordBlockerFragment.appInfoCache` is an unbounded `MutableMap<String, Pair<String, Drawable>>`.
5. **Massive dependency & resource bloat:** 7 direct libraries (`room-runtime`, `room-ktx`, `room-compiler`, `zxing-android-embedded`, `shizuku:api`, `shizuku:provider`, `MPAndroidChart`, `timerangepicker`, `constraintlayout`) have **zero usages** in the app. `generativeai` (0.9.0) drags in Ktor + OkHttp + Kotlinx Serialization (~16,000+ DEX methods) solely for 3 JSON POST calls to Gemini REST endpoints while `fetchModelsList` already uses `HttpURLConnection`. `isMinifyEnabled = false` ships all 95,072 DEX references across 2 DEX files and 384 unused resources (including Curbox leftovers like `pebble.png` and `res/E5.ogg`).
6. **Tracked junk files in repo root:** `local` (copied `local.properties`), `tmp_layout.xml`, `ic_launcher_foreground.svg`, `skills-lock.json`.

---

## 3. Tech Stack & Versions

| Layer | Technology & Version |
|---|---|
| Language | Kotlin `1.9.24` (stdlib resolved to `2.0.21` via transitive dep), JVM target `1.8` |
| Build system | Gradle Wrapper `8.10.2`, Android Gradle Plugin `8.7.2`, KSP `1.9.24-1.0.20` |
| Android SDK | `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34` |
| UI Toolkit | Android Views + ViewBinding, Material Components `1.12.0`, AppCompat `1.7.0`, Fragment KTX `1.8.9`, Activity `1.9.2`, Lifecycle ViewModel KTX `2.10.0`, Downloadable font (`Plus Jakarta Sans` via GMS) |
| Persistence | Jetpack DataStore `1.2.1` (`MultiProcessDataStoreFactory`) + Gson `2.11.0` (`filesDir/datastore/settings.json`); `SharedPreferences("app_prefs")` for disclaimer |
| Network / AI | `com.google.ai.client.generativeai:generativeai:0.9.0` + `java.net.HttpURLConnection` |
| Database | Room `2.6.1` declared (runtime, ktx, ksp) — **UNUSED** |
| DI | None (direct instantiation & `object` singletons) |
| Image loading | Platform `ImageDecoder` (API 28+) / `MediaStore` fallback (API 26–27) |
| Unused libraries | `zxing-android-embedded:4.3.0`, `dev.rikka.shizuku:api/provider:13.1.5`, `MPAndroidChart:v3.1.0`, `timerangepicker:1.0.0`, `androidx.constraintlayout:2.1.4`, `anychart` in version catalog |
| Testing | JUnit `4.13.2`, AndroidX JUnit `1.2.1`, Espresso `3.6.1` — **0 unit tests**, 1 broken instrumented test (`com.blockwords.app`) |
| CI / Release | GitHub Actions (`.github/workflows/release.yml`), JDK 17 Temurin, `gradle/actions/setup-gradle@v3.5.0`, `timheuer/base64-to-file`, `actions/attest-build-provenance`, `softprops/action-gh-release` |

---

## 4. Core Functionality Contract (Regression Contract)

Every behavior below MUST keep working with 100% parity throughout the overhaul:

| ID | Behavior |
|---|---|
| **C1** | **First-launch disclaimer:** Non-cancelable Material dialog when `app_prefs.agreed_to_terms` is `false`; "I Agree" persists `true` and shows main UI; "Exit" calls `finish()`. |
| **C2** | **Accessibility permission status & deep-link:** Banner visible iff `WardenService` is not enabled in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; button opens Accessibility settings targeting `WardenService` with fallback. |
| **C3** | **Master blocker toggle:** Switching ON activates `keywordBlockerConfig.isActive`; attempting to switch OFF reverts toggle and opens AI Gatekeeper `PAUSE_BLOCKER` dialog. |
| **C4** | **Blocked keywords CRUD:** Add trimmed, lowercased, deduplicated non-blank keywords; strip `https://`/`http://`/`www.` from URLs with warning toast; prompt confirmation when adding `"sex"`; display chips & count; require password (if set) before removing a chip. |
| **C5** | **Whitelisted keywords CRUD:** Add trimmed, lowercased, deduplicated non-blank whitelist entries (with URL prefix stripping + warning toast); display chips & count; require password (if set) before removing a chip. |
| **C6** | **Keyword matching engine:** Case-insensitive whole-word regex `(?<!\w)(?:kw1|kw2|...)+(?!\w)` (longest-first); whitelisted phrases masked with equal-length spaces before matching; extracts context of up to 7 words before and 7 words after match; scans both `node.text` and `node.contentDescription` across the accessibility view tree. |
| **C7** | **Blocking execution & grace period:** Skips when inactive, during `blockerDisabledUntil`, in `com.warden.app`, in `ignoredApps`, or in unexpired `temporaryIgnoredApps`. When matched: displays toast with keyword + context; if `ignoreGracePeriodSeconds > 0`, waits N seconds then re-checks same app & same keyword before calling `GLOBAL_ACTION_HOME` (cancels if navigated away/cleared); if `0`, calls `GLOBAL_ACTION_HOME` immediately. |
| **C8** | **Grace period slider:** `0..5` seconds (default `2`), renders `"Off"` for `0` and `"Ns"` for `1..5`, persisted immediately. |
| **C9** | **Self-protection & Anti-Uninstall:** Intercepts non-safe-package screens containing `"warden"` alongside Force Stop / Kill / Clear Data / Uninstall / Accessibility disable prompts, and (when `antiUninstallEnabled` is true and outside the 180s activation window) Device Admin deactivation screens, triggering `GLOBAL_ACTION_HOME` + toast. Anti-Uninstall toggle syncs with `DevicePolicyManager.isAdminActive`, records activation timestamp before `ACTION_ADD_DEVICE_ADMIN`, and requires password (if set) or confirmation dialog to call `removeActiveAdmin`. `WardenDeviceAdminReceiver` toasts on enable/disable. |
| **C10** | **Password protection:** Enforces ≥6 chars with ≥1 letter and ≥1 digit; hashes with SHA-256 hex (backward compatible with existing hashes); gates keyword removal, whitelist removal, password change/removal, and disabling Anti-Uninstall. |
| **C11** | **AI Gatekeeper — API key & models:** Stores/clears API key (masks as `"***********"` in UI); fetches `generateContent`-capable models from Gemini `v1beta/models` endpoint; persists available & selected model. |
| **C12** | **AI Gatekeeper — Justification validation:** Skipped when `isRemoval == true`; otherwise requires ≥7 non-empty sentences (`[.!?]+`), ≥5 words/sentence, and `distinctLowerWords > words.size / 2`. |
| **C13** | **AI Gatekeeper — Exemption, Removal, Pause, Extension:** Supports optional screenshot attachment (`Bitmap`), passes `noCount` in prompts, expects structured JSON `{approved, durationMinutes, reasoning, botResponse}`. On approval: `TEMPORARY_IGNORE` adds `now + durationMinutes*60_000` (or removes from `ignoredApps` & `temporaryIgnoredApps` if `isRemoval`), `EXTEND_IGNORE` adds `durationMinutes*60_000` to current expiry, `PAUSE_BLOCKER` sets `isActive = false`. On rejection: increments `noCount`. |
| **C14** | **Active ignored apps list & controls:** Displays permanent (`"Permanent"`) and unexpired temporary (`"%dm %02ds"`) ignored apps with 1s live countdown and automatic expiry pruning; tapping time opens decrease-only slider (`0..remainingMinutes`); tapping `+` opens `EXTEND_IGNORE` AI dialog for that app. |
| **C15** | **Settings export & merge-only import:** Export serializes `Settings` JSON (always resetting `antiUninstallEnabled = false`, `deviceAdminActivationRequestedAt = 0L`, with user toggles for excluding `passwordHash`/`geminiApiKey`). Import safely parses JSON with null fallbacks and merges `keywordBlockerConfig` (`isActive = current || imported`, union-distinct keywords/whitelists/ignoredApps). |
| **C16** | **Crash logging:** Default `UncaughtExceptionHandler` writes crash details to `filesDir/crash_log.txt` and delegates to the system handler; `WardenService` logs non-fatal worker errors without crashing. |
| **C17** | **Upgrade data compatibility:** Existing `filesDir/datastore/settings.json` and `SharedPreferences("app_prefs")` data from `baseline-v1` loads cleanly in `overhaul/v2` without loss or migration failure. |

---

## 5. Current Release Workflow Inventory

**Workflow file:** [`.github/workflows/release.yml`](../.github/workflows/release.yml) (only workflow in repo).

- **Triggers:** `push.tags: ['v*']` and `workflow_dispatch` (with `version_number` input).
- **Concurrency:** `group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`.
- **Top-level permissions:** `contents: read`.
- **Job 1 (`validate`):** `ubuntu-latest`, `timeout-minutes: 15`. Checks out code (`actions/checkout@v4.2.2` SHA-pinned, missing `persist-credentials: false`), sets up JDK 17 Temurin, sets up Gradle (`gradle/actions/setup-gradle@v3.5.0` SHA-pinned, missing `wrapper-validation`), runs `./gradlew lint --no-daemon` and `./gradlew test --no-daemon`.
- **Job 2 (`release`):** `needs: validate`, `ubuntu-latest`, `timeout-minutes: 30`, `permissions: {contents: write, id-token: write, attestations: write}`. No GitHub `environment:` protection. Decodes `KEYSTORE_BASE64` via `timheuer/base64-to-file` (never deletes the temp file after build!), builds release APK using `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, sets `VERSION_CODE=${{ github.run_number }}` (not derived from tag / not deterministic across re-runs!), renames APK, generates `SHA256SUMS.txt`, runs `actions/attest-build-provenance`, and publishes via `softprops/action-gh-release`.
- **Notable gaps:**
  - No Gradle wrapper validation step (`gradle/actions/wrapper-validation`).
  - No APK verification (`apksigner verify`, size budget check, version/tag check).
  - No SBOM generation.
  - No protected GitHub `environment:` for signing secrets; decoded keystore file is not scrubbed/deleted in an `if: always()` step.
  - `zizmor` reports **8 findings** (2 high `cache-poisoning`, 2 medium `artipacked`, 3 low `template-injection` via `${{ env.* }}`, 1 info `superfluous-actions`).
  - Duplicated checkout/JDK/Gradle setup across jobs (no composite action).
  - No Dependabot/Renovate config; no PR CI validation workflow; no `docs/RELEASING.md`.
  - `VERSION_CODE` uses `github.run_number` (non-deterministic on reruns / forks).

---

## 6. Migration Strategy Assessment (Incremental vs. From-Scratch Rewrite)

**Decision:** **Incremental strangler refactor on `overhaul/v2`** (no from-scratch rewrite required).

**Justification:**
- Total main source is only 22 Kotlin files (2,901 LOC), of which 7 files are dead code.
- The persistence contract (`datastore/settings.json` + `app_prefs`) and Android manifest entry points (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver`) must preserve exact class/file compatibility for seamless upgrades from `baseline-v1`.
- Every hot spot (`KeywordBlockerFragment` 1,155 LOC, `KeywordBlocker` 399 LOC, `GeminiManager`, `DataStoreManager`, unused Gradle dependencies) can be decoupled cleanly behind explicit interfaces one logical commit at a time while keeping the app compiling and passing the characterization test suite on every commit.
- This decision will be formalized in `docs/adr/0001-incremental-strangler-overhaul.md`.
