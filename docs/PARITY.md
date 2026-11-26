# Warden Feature Parity Matrix (`baseline-v1` vs `overhaul/v2`)

> Every row from the Core Functionality Contract (`docs/ANALYSIS.md`) plus manifest and data-migration gates.
> All 17 core contract items (C1–C17) are verified by automated JVM/Robolectric tests running on API 30.

## 1. Core Functionality Contract Parity (17 / 17 PASS)

| ID | Feature / Behavior | Old (`baseline-v1`) | New (`overhaul/v2`) | Test That Proves It | Status |
|---|---|---|---|---|---|
| C1 | First-launch disclaimer dialog (`app_prefs.agreed_to_terms`) | Non-cancelable dialog; "I Agree" persists & mounts fragment; "Exit" finishes | Identical behavior (with off-main-thread `SharedPreferences` read/write) | `FragmentActivityParityTest` (3 tests) + `AppCoreFlowsCharacterizationTest.c1_*` | **PASS** |
| C2 | Accessibility permission check & Settings deep-link | Checks `ENABLED_ACCESSIBILITY_SERVICES`; shows/hides banner; opens Settings with fallback | Identical via `AndroidAccessibilityPermissionChecker` | `AppCoreFlowsCharacterizationTest.c2_*` (2 tests) | **PASS** |
| C3 | Master blocker toggle (ON direct; OFF gated by AI pause dialog) | Switch ON enables; Switch OFF reverts & opens `PAUSE_BLOCKER` dialog | Identical via `KeywordBlockerFragment` + `BlockerAction.SetBlockerActive` | `BlockerViewModelAndReplaceabilityTest.c3_c4_c5_c8_*` | **PASS** |
| C4 | Blocked keywords CRUD + URL normalization + `"sex"` warning + password-gated delete | Lowercase/trim/dedup, URL prefix strip, `"sex"` confirm, chip count, password gate | Identical via `KeywordNormalizer` + `KeywordsSectionBinder` + `BlockerAction` | `AppCoreFlowsCharacterizationTest.c4_c5_*` + `BlockerViewModelAndReplaceabilityTest.c3_c4_c5_c8_*` | **PASS** |
| C5 | Whitelisted keywords CRUD + URL normalization + password-gated delete | Lowercase/trim/dedup, URL prefix strip, chip count, password gate | Identical via `KeywordNormalizer` + `KeywordsSectionBinder` + `BlockerAction` | `AppCoreFlowsCharacterizationTest.c4_c5_*` + `BlockerViewModelAndReplaceabilityTest.c3_c4_c5_c8_*` | **PASS** |
| C6 | Keyword matching engine (whitelist masking, word-boundary regex, ±7-word context, tree scan) | Masks whitelisted phrases with spaces; longest-first regex `(?<!\w)(?:...)+(?!\w)`; ±7 words context | Identical via `KeywordMatcher` + `AccessibilityNodeScanner` (with node recycling) | `KeywordBlockerEngineCharacterizationTest.c6_*` (4 tests) | **PASS** |
| C7 | Blocking execution & grace period (0s instant, >0s delayed re-check, cancel on exit, ignore checks) | Respects `isActive`, `blockerDisabledUntil`, self-pkg, `ignoredApps`, `temporaryIgnoredApps`; `GLOBAL_ACTION_HOME` | Identical via `BlockingDecisionEngine` + `BlockerCoordinator` + `GracePeriodController` (also fixed duplicate toast on 0s grace) | `KeywordBlockerEngineCharacterizationTest.c7_*` (3 tests) | **PASS** |
| C8 | Grace period slider (`0..5`, `"Off"` / `"Ns"`, default `2`) | Persists `ignoreGracePeriodSeconds` | Identical via `BlockerAction.SetGracePeriodSeconds` | `BlockerViewModelAndReplaceabilityTest.c3_c4_c5_c8_*` | **PASS** |
| C9 | Self-protection & Anti-Uninstall (Settings/Admin/Accessibility guard, 180s grace, DeviceAdmin toggle & receiver) | Blocks Settings/Force Stop/Clear Data/Uninstall/Accessibility disable & Device Admin deactivate outside 180s window; password-gated disable | Identical via `SelfProtectionEvaluator` + `AntiUninstallUiController` + `WardenDeviceAdminReceiver` | `KeywordBlockerEngineCharacterizationTest.c9_*` (3 tests) + `AppCoreFlowsCharacterizationTest.c9_*` + `BlockerViewModelAndReplaceabilityTest.c9_c10_*` | **PASS** |
| C10 | Password protection (strength rule ≥6 + letter + digit, SHA-256 hex compatibility, change/remove) | SHA-256 hex stored in `passwordHash`; gates destructive edits | Identical via `PasswordPolicy` (constant-time `MessageDigest.isEqual`) + `PasswordDialogController` | `AppCoreFlowsCharacterizationTest.c10_*` + `BlockerViewModelAndReplaceabilityTest.c9_c10_*` | **PASS** |
| C11 | AI Gatekeeper API key & model discovery | Masks key as `***********`; `GET v1beta/models` filtered by `generateContent`; persists list & selection | Identical via `HttpGeminiGatekeeperClient.fetchModels` + `GeminiSectionBinder` | `GeminiGatekeeperCharacterizationTest.c11_*` + `BlockerViewModelAndReplaceabilityTest.c11_c13_c14_*` | **PASS** |
| C12 | AI Gatekeeper local justification validation | Bypassed for `isRemoval`; else ≥7 sentences, ≥5 words/sentence, >50% distinct words | Identical via `JustificationValidator` | `GeminiGatekeeperCharacterizationTest.c12_*` (5 tests) | **PASS** |
| C13 | AI Gatekeeper exemption / removal / pause / extension + multimodal screenshot + `noCount` | Structured JSON decision; updates `temporaryIgnoredApps`/`ignoredApps`/`isActive`/`noCount` | Identical via `HttpGeminiGatekeeperClient` + `GatekeeperPromptBuilder` + `GatekeeperDialogController` + bounded JPEG `BitmapImageEncoder` | `GeminiGatekeeperCharacterizationTest.c13_*` (2 tests) + `BlockerViewModelAndReplaceabilityTest.c11_c13_c14_*` + `.c13_c15_*` | **PASS** |
| C14 | Active ignored apps list, live 1s countdown, auto-expiry, decrease-only slider, extend trigger | Shows permanent + temp apps; prunes expired; decrease slider `0..remainingMinutes` | Identical via `IgnoredAppsFormatter` + `IgnoredAppsListBinder` (countdown ticks only while temp exemptions exist) | `AppCoreFlowsCharacterizationTest.c14_*` + `BlockerViewModelAndReplaceabilityTest.c11_c13_c14_*` | **PASS** |
| C15 | Settings JSON export (resetting anti-uninstall, optional secret exclusion) & merge-only import | `getSettingsJson` / `importSettingsJson` merges `isActive` (OR) and keyword/whitelist/ignored lists (union) | Identical via `SettingsJsonCodec` + `JsonSettingsRepository` + off-main-thread `BackupDialogController` | `SettingsPersistenceCharacterizationTest.c15_*` (2 tests) + `BlockerViewModelAndReplaceabilityTest.c13_c15_*` | **PASS** |
| C16 | Crash logging (`filesDir/crash_log.txt`) + delegation to default handler | Writes timestamp/device/stacktrace; delegates to prior handler | Identical + bounded 128 KiB rotation (`crash_log.prev.txt`) & lazy `filesDir` resolution | `AppCoreFlowsCharacterizationTest.c16_*` (2 tests) | **PASS** |
| C17 | Upgrade data compatibility (`filesDir/datastore/settings.json` & `app_prefs`) | Exact JSON schema & SharedPreferences keys preserved | Identical path, exact field names, safe defaults for missing legacy fields | `SettingsPersistenceCharacterizationTest.c17_*` (3 tests) | **PASS** |

## 2. Manifest, Permissions & Exported Surface Audit

Verified via `apkanalyzer manifest print app/build/outputs/apk/release/Warden-release.apk`:

| Surface Item | `baseline-v1` | `overhaul/v2` | Justification |
|---|---|---|---|
| `INTERNET` permission | Present | Kept | Required for Gemini AI Gatekeeper API requests |
| `QUERY_ALL_PACKAGES` permission | Present | Kept | Required to enumerate launchable apps in AI exemption app selector |
| `CAMERA` permission + 6 hardware features | Merged (unused ZXing) | **Removed** | Unintended transitive artifact from unused `zxing-android-embedded` |
| `moe.shizuku.manager.permission.API_V23` | Merged (unused Shizuku) | **Removed** | Unintended transitive artifact from unused `dev.rikka.shizuku` |
| `FragmentActivity` (exported launcher) | Present | **Preserved verbatim** | Preserves launcher icon/shortcuts on upgrade |
| `SelectAppsActivity` (not exported) | Present (dead) | **Removed** | Never referenced or launched anywhere in `baseline-v1` |
| `WardenService` (exported, `BIND_ACCESSIBILITY_SERVICE`) | Present | **Preserved verbatim** | Preserves user's OS Accessibility Service permission grant on upgrade |
| `WardenDeviceAdminReceiver` (exported, `BIND_DEVICE_ADMIN`) | Present | **Preserved verbatim** | Preserves active Device Administrator registration on upgrade |
| `CaptureActivity` (ZXing) & `MultiInstanceInvalidationService` (Room) | Merged (unused) | **Removed** | Unintended transitive artifacts from unused dependencies |
| `InitializationProvider` (`EmojiCompatInitializer`, `ProcessLifecycleInitializer`) | Merged (3 initializers) | **Trimmed to 1** (`ProfileInstallerInitializer`) | Eliminates unnecessary startup overhead; `ProfileInstallerInitializer` installs `baseline-prof.txt` |

## 3. Data Migration Verification
- [x] **VERIFIED (automated):** `SettingsPersistenceCharacterizationTest.c17_readsLegacyBaselineV1SettingsJsonFromDiskIncludingMissingOptionalFields` loads a real `baseline-v1` `datastore/settings.json` file (including partial legacy JSON) via `JsonSettingsRepository`.
- [x] **VERIFIED (automated):** `AppCoreFlowsCharacterizationTest.c10_passwordPolicyStrengthAndSha256BaselineCompatibility` verifies exact SHA-256 hex digest parity (`"secret1"` → `5b11618c2e44027877d0cd0921ed166b9f176f50587fc91e7534dd2946db77d6`).
- [x] **VERIFIED (automated):** `FragmentActivityParityTest.c1_returningUserSkipsDisclaimerAndMountsDashboardDirectly` verifies existing `app_prefs.agreed_to_terms` survives untouched.
- [ ] **BLOCKED (hardware unavailable):** On-device `adb install -r` upgrade of `overhaul/v2` APK over `baseline-v1` APK (no physical Android device or emulator attached; logged in `docs/BLOCKERS.md`).
