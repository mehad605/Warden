# Warden Overhaul — Final Results (`overhaul/v2`)

**Base Reference:** `baseline-v1` (`84d3f0d`, `main` untouched)  
**Overhaul Branch:** `overhaul/v2`  
**Target Device Constraints:** Realme Narzo 20 (MediaTek Helio G85, 4 GB RAM, Android 11 / API 30, aggressive OEM background killing), `minSdk = 26`, `targetSdk = 34`.

---

## 1. Final Scorecard Summary

| Sector | Baseline | Final | Passed / Total | Notes |
|---|---:|---:|---:|---|
| **S1.** APK & installed size | 0 / 10 | **10 / 10** | 9 / 9 | All verified |
| **S2.** Runtime footprint | 0 / 10 | **7 / 10** | 7 / 9 | 2 hardware-blocked (`S2.4`, `S2.9`) — [`BLOCKERS.md`](BLOCKERS.md) |
| **S3.** Code structure & maintainability | 0 / 10 | **10 / 10** | 11 / 11 | All verified |
| **S4.** Readability & onboarding | 0 / 10 | **10 / 10** | 9 / 9 | Cold-Read Test **10/10** ([`COLD_READ_TEST.md`](COLD_READ_TEST.md)) |
| **S5.** Extensibility & replaceability | 0 / 10 | **10 / 10** | 5 / 5 | 2-component Replaceability + [`EXTENSION_PROOF.md`](EXTENSION_PROOF.md) |
| **S6.** Release pipeline security | 3 / 10 | **8 / 10** | 11 / 13 | 2 repo-admin-blocked (`S6.5`, `S6.6`) — [`BLOCKERS.md`](BLOCKERS.md) |
| **S7.** Release pipeline reliability & maintainability | 2 / 10 | **10 / 10** | 11 / 11 | Live CI & dry-run release verified green |
| **S8.** Correctness & regression safety (GATE) | 2 / 10 | **8 / 10** | 4 / 5 | 1 hardware-blocked (`S8.3`) — [`BLOCKERS.md`](BLOCKERS.md) |

Full per-criterion evidence: [`docs/SCORECARD.md`](SCORECARD.md).

---

## 2. Baseline vs. Final Metrics

Every metric below is **VERIFIED** from tool output unless explicitly marked **BLOCKED (no device)**.

| Metric | `baseline-v1` (`84d3f0d`) | `overhaul/v2` (Final) | Change | Evidence |
|---|---:|---:|---:|---|
| **Release APK size** | 7,511,385 B (7.16 MiB) | **1,608,536 B (1.53 MiB)** | **-78.6%** | `apkanalyzer apk file-size` (`VERIFIED`) |
| **Release download size** | 6,356,795 B (6.06 MiB) | **1,124,927 B (1.07 MiB)** | **-82.3%** | `apkanalyzer apk download-size` (`VERIFIED`) |
| **DEX files / method refs** | 2 files / 95,072 refs | **1 file / 12,753 refs** | **-86.6%** | `apkanalyzer dex list` & `references` (`VERIFIED`) |
| **Native `.so` libraries** | 4 (`libdatastore_shared_counter.so`) | **0** | **-100%** | `unzip -Z1` / `verify_release_apk.sh` (`VERIFIED`) |
| **Launcher icon assets** | 649 KB (5 PNGs) | **27 KB (5 WebPs)** | **-95.8%** | `app/src/main/res/mipmap-*` (`VERIFIED`) |
| **Baseline profile in APK** | None | **1,773 B (`assets/dexopt/baseline.prof`)** | **Added** | `verify_release_apk.sh` (`VERIFIED`) |
| **Resolved runtime deps** | 224 (19 direct) | **133 (6 direct)** | **-40.6%** | `./gradlew :app:dependencies` (`VERIFIED`) |
| **Startup initializers** | 3 | **1** (`ProfileInstallerInitializer` only) | **-66.7%** | `AndroidManifest.xml` (`VERIFIED`) |
| **Max Kotlin file LOC** | 1,155 | **249** | **-78.4%** | `ArchitectureRulesTest` (`VERIFIED`) |
| **Max function LOC** | 202 | **36** | **-82.2%** | `detekt LongMethod` (`VERIFIED`) |
| **Max cyclomatic complexity** | 48 | **9** | **-81.3%** | `detekt CyclomaticComplexMethod` (`VERIFIED`) |
| **Code duplication (`jscpd`)** | 3.32% | **0.71%** | **-78.6%** | `npx jscpd app/src/main/java/` (`VERIFIED`) |
| **Android Lint warnings** | 610 | **0** (`warningsAsErrors = true`) | **-100%** | `./gradlew lint` (`VERIFIED`) |
| **detekt / ktlint issues** | Not configured | **0 / 0** | **Enforced** | `./gradlew ktlintCheck detekt` (`VERIFIED`) |
| **Public API KDoc coverage** | 0% (0 / 126) | **100%** (CI-enforced) | **+100%** | `ArchitectureRulesTest` (`VERIFIED`) |
| **Unit / Robolectric tests** | 0 (1 broken instrumented test) | **45 / 45 PASS** (debug + release) | **+45** | `./gradlew testDebugUnitTest testReleaseUnitTest` (`VERIFIED`) |
| **`zizmor` findings** | 8 active | **0** | **-100%** | `uvx zizmor --offline .github/` (`VERIFIED`) |
| **`actionlint` errors** | 0 | **0** | **Maintained** | `actionlint v1.7.12` (`VERIFIED`) |
| **On-device cold start / RAM / CPU / LeakCanary** | Unavailable (no device) | **BLOCKED (no device)** | N/A | See [`docs/BLOCKERS.md`](BLOCKERS.md) |

### Final Release APK Component Breakdown (`1,608,536 B`)
| Component | Raw Size | Compressed Size in APK |
|---|---:|---:|
| `classes.dex` (single DEX, R8 full mode) | 1,521,248 B | 738,900 B |
| `resources.arsc` (English-only, shrunk) | 478,436 B | 478,436 B |
| `res/` (WebP icons, flattened layouts, M3) | 553,573 B | 285,932 B |
| `AndroidManifest.xml` | 6,908 B | 1,932 B |
| `assets/dexopt/baseline.prof` | 1,773 B | 1,773 B |
| `META-INF/` + other | 1,328 B | 1,058 B |
| `lib/` (native `.so`) | **0 B** | **0 B** |

---

## 3. Feature Parity Summary

All **17 / 17** behaviors in the Core Functionality Contract (`C1`–`C17`) are **VERIFIED PASS** against `baseline-v1` with zero behavior regressions and preserved upgrade invariants (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver` FQCNs, `filesDir/datastore/settings.json` v1 schema, `SharedPreferences("app_prefs")["agreed_to_terms"]`, SHA-256 password hashes).

See [`docs/PARITY.md`](PARITY.md) for the complete 17-row test traceability matrix.

---

## 4. What Changed & What Was Rejected

### Key Changes by Sector
- **S1 (Size):** Removed 9 unused dependencies, 8 dead Kotlin files, 35 unused resources/junk files, and unwanted merged manifest entries (`CAMERA`, Shizuku, ZXing, Room service). Replaced `MultiProcessDataStoreFactory` + `generativeai` SDK + `gson` with platform `AtomicFile` + `HttpURLConnection` + `org.json`. Enabled R8 full mode, `shrinkResources`, English-only resources, WebP launcher icons, log stripping, and a static baseline profile (`app/src/main/baseline-prof.txt`).
- **S2 (Runtime):** Eliminated synchronous `Application.onCreate` disk I/O via lazy `AppContainer` and lazy rotating `CrashLogger` (128 KiB cap). Removed `EmojiCompatInitializer` and `ProcessLifecycleInitializer`. Moved SharedPreferences, JSON persistence, SAF backup I/O, and `PackageManager` icon lookups (with bounded `LruCache(64)`) off the main thread. Fixed `AccessibilityNodeInfo` / `AccessibilityEvent` recycling leaks, removed the exported refresh `BroadcastReceiver` in favor of in-process `StateFlow<Settings>`, fixed the event cooldown bug, and made the ignored-apps countdown strictly idle when empty.
- **S3/S5 (Structure & Extensibility):** Decomposed `KeywordBlocker` (399 LOC, CC 48) and `KeywordBlockerFragment` (1,155 LOC) into layered, package-by-feature modules (`core/{data,di,platform}`, `feature/{blocker,gatekeeper,security,backup}/`) with strict `ArchitectureRulesTest` boundary enforcement, `detekt` (`maxIssues: 0`), `ktlint`, and `warningsAsErrors` Android Lint. Proved 2-component replaceability (`SettingsRepository`, `GatekeeperClient`) and verified `docs/HOW_TO.md` Recipe 2 end-to-end (`EXTENSION_PROOF.md`).
- **S4 (Docs & Onboarding):** Rewrote `README.md`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `docs/HOW_TO.md`, 5 ADRs (`docs/adr/0001`–`0005`), 10 package `README.md`s, and updated feature guides; enforced 100% public KDoc in CI; passed a blind **10/10** Cold-Read Test (`COLD_READ_TEST.md`).
- **S6/S7 (CI & Release):** Added `version.properties`, deterministic SemVer `versionCode`, `:app:generateReleaseSbom` (CycloneDX 1.5), `scripts/verify_release_apk.sh`, shared composite action `.github/actions/setup-android-build` (`uses: $/...`), `.github/workflows/ci.yml` (with `dependency-review-action`), hardened `.github/workflows/release.yml` (SHA-pinned, `environment: release`, shell keystore decode + `if: always()` shred, dry-run mode, idempotent draft-then-publish, provenance + SBOM + SHA-256), `.github/dependabot.yml` (7-day cooldown), `CODEOWNERS`, `docs/RELEASING.md`, and `docs/GITHUB_REPO_SETTINGS.md`. Verified live on GitHub Actions:
  - `CI Verification`: [`#45852284519`](https://github.com/mehad605/Warden/actions/runs/45852284519) (**SUCCESS**)
  - `Build & Release Warden` (`dry_run: true`, `v2.0.0`): [`#45852288716`](https://github.com/mehad605/Warden/actions/runs/45852288716) (**SUCCESS**, uploaded `warden-release-dryrun-v2.0.0`)

### Tried & Rejected
- **Using `uses: ./.github/actions/...` vs `$/...`:** Initially used `./.github/actions/setup-android-build`, which `zizmor v1.33.1` flagged for workspace-relative action tampering (`self-repository`). Switched to GitHub's GA `$/` syntax (verified live on GitHub Actions!) with a narrow `.github/actionlint.yaml` rule for `rhysd/actionlint#780`.
- **Upgrading `targetSdk` to 35:** Rejected because Android 15 forces edge-to-edge layout changes that could alter UI behavior without user approval; documented `OldTargetApi` ignore in `app/lint.xml` for intentional API 34 pinning.
- **Full greenfield rewrite or Jetpack Compose migration:** Rejected in `docs/adr/0001-incremental-strangler-overhaul.md` — incremental strangler refactoring kept all 3 manifest FQCNs, saved data, and ViewBinding footprint minimal (Compose would add ~1–2 MB + startup overhead on a 4 GB RAM device).

---

## 5. Items Requiring User Action

1. **Review & Merge PR:** Review the PR `overhaul/v2 → main` (not merged automatically).
2. **Configure GitHub Repository Security Settings** ([`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md)):
   - Create protected GitHub Environment `release` with required reviewer `@mehad605` and move `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` into it (`S6.5`).
   - Enable Branch Ruleset on `main` (require PR + `CI Verification` + `Dependency Review`) and Tag Ruleset on `v*` restricted to repo admins (`S6.6`).
   - Optionally enable Dependabot security updates under **Settings → Code security**.
3. **On-Device Smoke & Profiling Pass on Realme Narzo 20 (API 30)** ([`docs/BLOCKERS.md`](BLOCKERS.md)):
   - Run `adb install -r` over an existing `baseline-v1` install to confirm zero-loss upgrade and clean `adb logcat` (`S8.3`).
   - Capture cold-start (`am start -W`), idle RAM/CPU (`dumpsys meminfo`/`cpuinfo`), and LeakCanary/30-min heap diff (`S2.4`, `S2.9`).

---

## 6. Rollback Plan

`main` remains untouched at tag `baseline-v1` (`84d3f0d`).

- **Before merge:** Close the PR and delete branch `overhaul/v2` — zero impact on `main`.
- **After merge:**
  ```bash
  git checkout main
  git revert --no-commit baseline-v1..HEAD
  git commit -m "revert: restore Warden to baseline-v1 (84d3f0d)"
  git push origin main
  ```
- **On-device downgrade note:** Because `overhaul/v2` uses `versionCode = 20000` (vs `50` on `baseline-v1`), Android requires `adb install -r -d` or export → uninstall → reinstall to downgrade an installed APK back to `baseline-v1`.

---

## 7. Remaining Opportunities & Tradeoffs

- **Material Components (`com.google.android.material:1.12.0`):** Remains the largest single contributor to `resources.arsc` (`478 KB`) and `classes.dex` (`739 KB` compressed), kept to preserve exact M3 visual parity. Replacing M3 widgets with bare platform widgets could save ~600 KB more, at the cost of redesigning the entire UI.
- **Encrypted API Key Storage:** `geminiApiKey` remains in `filesDir/datastore/settings.json` for `baseline-v1` parity (protected by Android app-private sandbox). Migrating to Android Keystore-backed encryption is a worthwhile future enhancement once approved.
- **Real-Device Benchmark Baseline Profile:** `app/src/main/baseline-prof.txt` is a static class-level profile; generating a Macrobenchmark-recorded profile on a physical API 30+ device would add method-level AOT hints.
