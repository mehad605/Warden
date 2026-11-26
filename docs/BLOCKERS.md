# Warden Overhaul — Blockers & Unverified Criteria

This document records every scorecard criterion that **cannot be verified honestly without external resources** (a physical Android device / emulator or GitHub repository administrator settings). **Zero numbers were fabricated or estimated as verified.**

---

## 1. Hardware / Device Blockers (No Android Device or Emulator Attached)

**Environment state:** `adb devices` returned no attached devices, and T3 Device Hub access is disabled in this environment. All unit, Robolectric (API 30), static analysis, R8 build, `apkanalyzer`, `apksigner`, and `aapt2` checks ran and passed locally and in CI, but live on-device profiling requires a physical Realme Narzo 20 (Android 11 / API 30) or Android emulator.

| Criterion | What IS Verified | What Remains Blocked | How to Unblock & Verify |
|---|---|---|---|
| **S2.4** No leaks (LeakCanary / heap-dump comparison) | Fixed all statically & test-verifiable leaks: child & root `AccessibilityNodeInfo` + copied `AccessibilityEvent` recycling in `finally`, `CoroutineScope` cancellation in `WardenService.onDestroy`, ViewBinding cleared in `onDestroyView`, bounded `LruCache(64)` icon cache, removed leaked refresh `BroadcastReceiver`. | Live LeakCanary / 30-minute idle heap dump comparison on a running device. | Install debug build on device/emulator, run 20 UI open/close cycles + 30 min idle with Accessibility Service active, and capture `adb shell dumpsys meminfo com.warden.app` + heap diff. |
| **S2.9** Measured idle RAM, idle CPU, and cold start vs baseline | Runtime footprint floor documented in [`docs/ARCHITECTURE.md`](ARCHITECTURE.md); static contributors verified (`0` native `.so` vs `4`, `1` DEX / `12,753` refs vs `2` DEX / `95,072` refs, `2` Startup initializers removed, baseline profile embedded, zero main-thread I/O). | Live on-device `adb shell am start -W`, `dumpsys meminfo`, and `dumpsys cpuinfo` measurements comparing `baseline-v1` vs `overhaul/v2`. | Sideload `baseline-v1` and `overhaul/v2` release APKs on Realme Narzo 20 (API 30) and record median of 5 cold starts + idle PSS/CPU after 2 minutes. |
| **S8.3** No new crashes in logcat during core flows on release build *(and live upgrade install test over `baseline-v1`)* | All **45/45** Robolectric/unit tests pass on both `testDebugUnitTest` and `testReleaseUnitTest`; all 3 manifest FQCNs (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver`), `filesDir/datastore/settings.json` v1 schema, `SharedPreferences("app_prefs")["agreed_to_terms"]`, and SHA-256 hashes verified bit-compatible; `apksigner verify` passes on the R8-minified APK. | Live `adb install -r` upgrade over a `baseline-v1` install with existing settings + live `adb logcat` smoke pass on the R8-minified APK. | Install `baseline-v1` APK, configure keywords/password/API key, run `adb install -r Warden-release.apk`, exercise `C1`–`C17`, and confirm zero crashes in `adb logcat`. |

---

## 2. GitHub Repository Administration Blockers

**Environment state:** Checked read-only via `gh api`: `secret_scanning` and `secret_scanning_push_protection` are already enabled on `mehad605/Warden`, while GitHub Environments (`release`), branch protection on `main`, and tag protection rulesets (`v*`) are currently unconfigured (`0` environments, `0` rulesets). Modifying repository-level admin settings is intentionally left for the repository owner.

| Criterion | What IS Verified | What Remains Blocked | How to Unblock & Verify |
|---|---|---|---|
| **S6.5** Signing keys scoped to protected GitHub Environment | `.github/workflows/release.yml` binds the `release` job to `environment: release`, decodes `KEYSTORE_BASE64` into a `0600` temp file under `$RUNNER_TEMP`, and shreds/deletes it in an `if: always()` step. | Creating the `release` Environment in GitHub Settings with `@mehad605` as required reviewer and moving the 4 signing secrets into it. | Follow Section 1 of [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md). |
| **S6.6** Releases triggered only from protected tags/branches | `.github/workflows/release.yml` restricts `push.tags` to strict SemVer `v[0-9]+.[0-9]+.[0-9]+` and validates tag syntax before building; `dry_run` defaults to `true` on `workflow_dispatch`. | Enabling GitHub Branch Ruleset on `main` and Tag Ruleset on `v*` restricted to repository admins. | Follow Sections 2–3 of [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md). |
