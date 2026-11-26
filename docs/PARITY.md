# Warden Feature Parity Matrix (`baseline-v1` vs `overhaul/v2`)

> Every row from the Core Functionality Contract (`docs/ANALYSIS.md`) plus manifest/data compatibility gates.
> Parity is achieved only when every row is `PASS` with automated test proof (or recorded manual verification where hardware-bound).

## 1. Core Functionality Contract Parity

| ID | Feature / Behavior | Old (`baseline-v1`) | New (`overhaul/v2`) | Test That Proves It | Status |
|---|---|---|---|---|---|
| C1 | First-launch disclaimer dialog (`app_prefs.agreed_to_terms`) | Shows non-cancelable dialog; "I Agree" persists & loads fragment; "Exit" finishes | Pending | TBD (Phase 1c) | PENDING |
| C2 | Accessibility permission check & Settings deep-link | Checks `ENABLED_ACCESSIBILITY_SERVICES`; shows/hides banner; launches Settings with fallback | Pending | TBD (Phase 1c) | PENDING |
| C3 | Master blocker toggle (ON direct; OFF gated by AI pause dialog) | Switch ON enables; Switch OFF reverts & opens `PAUSE_BLOCKER` dialog | Pending | TBD (Phase 1c) | PENDING |
| C4 | Blocked keywords CRUD + URL normalization + `"sex"` warning + password-gated delete | Lowercase/trim/dedup, URL prefix strip, `"sex"` confirm, chip count, password gate | Pending | TBD (Phase 1c) | PENDING |
| C5 | Whitelisted keywords CRUD + URL normalization + password-gated delete | Lowercase/trim/dedup, URL prefix strip, chip count, password gate | Pending | TBD (Phase 1c) | PENDING |
| C6 | Keyword matching engine (whitelist masking, word-boundary regex, 7-word context, tree scan) | Masks whitelisted phrases with spaces; longest-first regex `(?<!\w)(?:...)+(?!\w)`; ±7 words context | Pending | TBD (Phase 1c) | PENDING |
| C7 | Blocking execution & grace period (0s instant, >0s delayed re-check, cancel on exit, ignore checks) | Respects `isActive`, `blockerDisabledUntil`, self-pkg, `ignoredApps`, `temporaryIgnoredApps`; `GLOBAL_ACTION_HOME` | Pending | TBD (Phase 1c) | PENDING |
| C8 | Grace period slider (`0..5`, `"Off"` / `"Ns"`, default `2`) | Persists `ignoreGracePeriodSeconds` | Pending | TBD (Phase 1c) | PENDING |
| C9 | Self-protection & Anti-Uninstall (Settings/Admin/Accessibility guard, 180s grace, DeviceAdmin toggle & receiver) | Blocks Settings/Force Stop/Clear Data/Uninstall/Accessibility disable & Device Admin deactivate outside 180s window; password-gated disable | Pending | TBD (Phase 1c) | PENDING |
| C10 | Password protection (strength rule ≥6 + letter + digit, SHA-256 hex compatibility, change/remove) | SHA-256 hex stored in `passwordHash`; gates destructive edits | Pending | TBD (Phase 1c) | PENDING |
| C11 | AI Gatekeeper API key & model discovery | Masks key as `***********`; `GET v1beta/models` filtered by `generateContent`; persists list & selection | Pending | TBD (Phase 1c) | PENDING |
| C12 | AI Gatekeeper local justification validation | Bypassed for `isRemoval`; else ≥7 sentences, ≥5 words/sentence, >50% distinct words | Pending | TBD (Phase 1c) | PENDING |
| C13 | AI Gatekeeper exemption / removal / pause / extension + multimodal screenshot + `noCount` | Structured JSON decision; updates `temporaryIgnoredApps`/`ignoredApps`/`isActive`/`noCount` | Pending | TBD (Phase 1c) | PENDING |
| C14 | Active ignored apps list, live 1s countdown, auto-expiry, decrease-only slider, extend trigger | Shows permanent + temp apps; prunes expired; decrease slider `0..remainingMinutes` | Pending | TBD (Phase 1c) | PENDING |
| C15 | Settings JSON export (resetting anti-uninstall, optional secret exclusion) & merge-only import | `getSettingsJson` / `importSettingsJson` merges `isActive` (OR) and keyword/whitelist/ignored lists (union) | Pending | TBD (Phase 1c) | PENDING |
| C16 | Crash logging (`filesDir/crash_log.txt`) + delegation to default handler | Writes timestamp/device/stacktrace; delegates to prior handler | Pending | TBD (Phase 1c) | PENDING |
| C17 | Upgrade data compatibility (`filesDir/datastore/settings.json` & `app_prefs`) | Exact JSON schema & SharedPreferences keys preserved | Pending | TBD (Phase 1c) | PENDING |

## 2. Manifest, Permissions & Exported Surface Audit

| Surface Item | `baseline-v1` | `overhaul/v2` | Justification |
|---|---|---|---|
| `INTERNET` permission | Present | Kept | Required for Gemini AI Gatekeeper API requests |
| `QUERY_ALL_PACKAGES` permission | Present | Kept | Required to enumerate launchable apps in AI exemption app selector |
| `CAMERA` permission + 6 hardware features | Merged (unused ZXing) | **Removed** | Unintended transitive artifact from unused `zxing-android-embedded` |
| `moe.shizuku.manager.permission.API_V23` | Merged (unused Shizuku) | **Removed** | Unintended transitive artifact from unused `dev.rikka.shizuku` |
| `FragmentActivity` (exported launcher) | Present | Kept | Main launcher activity (preserving component name for launcher shortcuts) |
| `SelectAppsActivity` (not exported) | Present (dead) | **Removed** | Never referenced or launched anywhere in `baseline-v1` |
| `WardenService` (exported, `BIND_ACCESSIBILITY_SERVICE`) | Present | Kept | Core AccessibilityService (preserving component name so OS accessibility grant survives upgrade!) |
| `WardenDeviceAdminReceiver` (exported, `BIND_DEVICE_ADMIN`) | Present | Kept | Core DeviceAdminReceiver (preserving component name so active Device Admin survives upgrade!) |
| `CaptureActivity` (ZXing) & `MultiInstanceInvalidationService` (Room) | Merged (unused) | **Removed** | Unintended transitive artifacts from unused dependencies |

> **CRITICAL UPGRADE INVARIANT:** The manifest component names `com.warden.app.ui.activity.FragmentActivity`, `com.warden.app.services.WardenService`, and `com.warden.app.receivers.WardenDeviceAdminReceiver` MUST NOT be renamed or moved, or upgrading from `baseline-v1` would revoke the user's Accessibility Service grant, Device Administrator registration, and launcher shortcut!

## 3. Data Migration Verification
- [ ] Verified deserializing a real `baseline-v1` `datastore/settings.json` file (including partial/legacy JSON missing newer fields like `whitelistedKeywords`, `antiUninstallEnabled`, `deviceAdminActivationRequestedAt`) in `overhaul/v2`.
- [ ] Verified existing SHA-256 `passwordHash` values produced by `baseline-v1` validate identically in `overhaul/v2`.
- [ ] Verified `SharedPreferences("app_prefs")["agreed_to_terms"]` is preserved.
- [ ] On-device upgrade install (`adb install -r` v2 over v1): **BLOCKED** — no Android device/emulator attached (to be documented in `docs/BLOCKERS.md` if still unavailable at merge gate).
