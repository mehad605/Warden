# Warden Overhaul — Scorecard

> **Scoring rule:** Each sector scores `10` **only** when ALL criteria pass with verified evidence. Otherwise `floor(passed / total * 10)`. Never round up.
> **Last updated:** Final Audit (`overhaul/v2`, base `baseline-v1` = `84d3f0d`) — including live Android 11 (API 30) emulator benchmarks, in-place upgrade test, and configured GitHub repository security controls.

## Summary Table

| Sector | Baseline Score | Final Score | Passed / Total | Status |
|---|---:|---:|---:|---|
| **S1.** APK & installed size | 0 / 10 | **10 / 10** | 9 / 9 | **PASS (10/10)** |
| **S2.** Runtime footprint | 0 / 10 | **10 / 10** | 9 / 9 | **PASS (10/10)** |
| **S3.** Code structure & maintainability | 0 / 10 | **10 / 10** | 11 / 11 | **PASS (10/10)** |
| **S4.** Readability & onboarding | 0 / 10 | **10 / 10** | 9 / 9 | **PASS (10/10)** |
| **S5.** Extensibility & replaceability | 0 / 10 | **10 / 10** | 5 / 5 | **PASS (10/10)** |
| **S6.** Release pipeline security | 3 / 10 | **9 / 10** | 12 / 13 | 1 secret-copy step requires owner (`S6.5` — see [`BLOCKERS.md`](BLOCKERS.md)) |
| **S7.** Release pipeline reliability & maintainability | 2 / 10 | **10 / 10** | 11 / 11 | **PASS (10/10)** |
| **S8.** Correctness & regression safety (GATE) | 2 / 10 | **10 / 10** | 5 / 5 | **PASS (10/10)** |

**71 / 72 criteria PASS with verified evidence.** Seven of eight sectors score **10/10**; the single remaining item (`S6.5`) is copying the 4 existing signing secrets from Repository Secrets into the newly created `release` Environment Secrets (GitHub's API never exposes raw secret values).

---

## S1. APK and Installed Size — **10 / 10** (9 / 9)
- [x] **S1.1** R8 full mode, minify, and `shrinkResources` enabled for release with minimal verified keep rules — **VERIFIED:** `android.enableR8.fullMode=true`, `isMinifyEnabled = true`, `isShrinkResources = true`, minimal `app/proguard-rules.pro`.
- [x] **S1.2** Zero unused dependencies — **VERIFIED:** Removed 9 unused libraries + replaced `datastore`, `generativeai`, `gson`; `releaseRuntimeClasspath` reduced **224 → 133** resolved deps (**6** direct `implementation` deps, all used).
- [x] **S1.3** Every remaining dependency justified in `docs/DEPENDENCIES.md` — **VERIFIED:** [`docs/DEPENDENCIES.md`](DEPENDENCIES.md).
- [x] **S1.4** Unused resources, locales, densities, fonts, assets removed; `resourceConfigurations` set — **VERIFIED:** Removed 35 unused resource/junk files; `resourceConfigurations += listOf("en")`; Android Lint `UnusedResources` = **0**.
- [x] **S1.5** Native libs: only needed ABIs, stripped, `extractNativeLibs=false` — **VERIFIED:** Native `.so` count reduced **4 → 0**; enforced in `scripts/verify_release_apk.sh`.
- [x] **S1.6** Images are WebP/vector; no oversized assets — **VERIFIED:** 5 launcher PNGs converted to WebP (`649 KB → 27 KB`); unused `pebble.png` deleted.
- [x] **S1.7** No debug/log/analytics code in release — **VERIFIED:** `app/proguard-rules.pro` `-assumenosideeffects` strips `android.util.Log.*` and `Throwable.printStackTrace()`; zero analytics dependencies.
- [x] **S1.8** Baseline profile present — **VERIFIED:** `app/src/main/baseline-prof.txt` compiled into `assets/dexopt/baseline.prof` (`1,773 B`), asserted inside release APK by `scripts/verify_release_apk.sh`.
- [x] **S1.9** Final size reported vs baseline with per-component breakdown — **VERIFIED:** `apkanalyzer` breakdown in [`docs/RESULTS.md`](RESULTS.md) (**7,511,385 B → 1,608,536 B, -78.6%**; download **6,356,795 B → 1,124,927 B, -82.3%**; DEX **95,072 [2 files] → 12,753 [1 file, -86.6%]**).

## S2. Runtime Footprint — **10 / 10** (9 / 9)
- [x] **S2.1** `Application.onCreate` minimal; everything else lazy — **VERIFIED:** `Warden.kt` enables debug `StrictMode` and registers lazy `CrashLogger`; all `AppContainer` bindings are `by lazy`, zero sync disk/network I/O.
- [x] **S2.2** No unjustified startup `ContentProvider`/initializer — **VERIFIED:** `EmojiCompatInitializer` and `ProcessLifecycleInitializer` removed via `tools:node="remove"` in `AndroidManifest.xml`; only `ProfileInstallerInitializer` remains.
- [x] **S2.3** No main-thread I/O or heavy work (StrictMode clean in debug) — **VERIFIED:** Debug `StrictMode` (thread + VM policy) enabled in `Warden.kt`; SharedPreferences, JSON persistence, SAF backup I/O, and `PackageManager` icon lookups all run on `Dispatchers.IO`; `WardenService` processes events on `Dispatchers.Default`.
- [x] **S2.4** No leaks (heap/object comparison on live API 30 emulator + static fixes) — **VERIFIED:** Fixed child/root `AccessibilityNodeInfo` & `AccessibilityEvent` recycling in `finally`, `CoroutineScope` cancellation in `WardenService.onDestroy`, `_binding = null` in `onDestroyView`, bounded `LruCache(64)`. Verified on Android 11 (API 30) emulator across **15 Activity open/close cycles + Settings accessibility traversal + GC**: `Java Heap` dropped **3,260 KB → 2,336 KB**, `Activities: 0`, `ViewRootImpl: 0`, `Views: 0` (zero retained objects).
- [x] **S2.5** No busy loops, polling, or oversized thread pools — **VERIFIED:** `IgnoredAppsListBinder` stops countdown `Handler` when no active temp exemptions exist; `WardenService` uses single `Channel.CONFLATED` consumer; cooldown timestamp bug fixed.
- [x] **S2.6** Idle state: no running service, wakelock, or periodic job unless required and justified — **VERIFIED:** Removed Room `MultiInstanceInvalidationService` and exported refresh `BroadcastReceiver`; `WardenService` is the sole required `AccessibilityService` and its idle floor is documented in [`docs/ARCHITECTURE.md`](ARCHITECTURE.md).
- [x] **S2.7** Background work uses WorkManager with constraints or is event-driven; no exact alarms — **VERIFIED:** 100% event-driven (`AccessibilityEvent` callbacks + in-process `StateFlow<Settings>`), zero alarms or periodic jobs.
- [x] **S2.8** Bounded caches, bounded disk growth, logs rotated/removed — **VERIFIED:** `LruCache(64)` icon cache; `CrashLogger` rotates `crash_log.txt` at 128 KiB to a single `crash_log.prev.txt` (≤256 KiB total).
- [x] **S2.9** Measured idle RAM, idle CPU, and cold start improved vs baseline with floor explained — **VERIFIED on Android 11 (API 30) KVM emulator (`aosp_atd;x86_64`)**:
  - **Cold start (median of 5 `am start -W` runs):** `414 ms` (`469/497/406/414/402`) → **`154 ms`** (`154/143/182/149/164`) (**-62.8%**, 2.7× faster)
  - **Idle RAM (`dumpsys meminfo` TOTAL PSS w/ Accessibility active):** `45,470 KB` (44.4 MiB, 214 Views) → **`33,742 KB`** (33.0 MiB, 160 Views) (**-25.8% PSS**, **-25.2% Views**)
  - **Idle CPU (`/proc/<pid>/stat` over 15s idle):** **`0` ticks / 15s (0.0% CPU)**
  - Footprint floor explained in [`docs/ARCHITECTURE.md`](ARCHITECTURE.md).

## S3. Code Structure and Maintainability — **10 / 10** (11 / 11)
- [x] **S3.1** No file > 300 lines and no function > 40 lines — **VERIFIED:** Max file **249 LOC** (down from 1,155); max function **36 LOC** (down from 202); enforced by `ArchitectureRulesTest` + `detekt`.
- [x] **S3.2** No god classes — **VERIFIED:** Max cyclomatic complexity **9** (down from 48).
- [x] **S3.3** Clear layering (`ui`, `domain`, `data`, `service`) with automated dependency direction enforcement — **VERIFIED:** `ArchitectureRulesTest`.
- [x] **S3.4** No circular dependencies; no global mutable state; no static Context holders — **VERIFIED:** `ArchitectureRulesTest` (`0` cycles via `AppContainerProvider`).
- [x] **S3.5** Explicit dependency injection (constructor injection) — **VERIFIED:** Lazy manual `AppContainer` + `BlockerViewModel.Factory`.
- [x] **S3.6** Consistent naming and package-by-feature layout — **VERIFIED:** `core/`, `data/models/`, `feature/{blocker,gatekeeper,security,backup}/`.
- [x] **S3.7** Zero swallowed exceptions — **VERIFIED:** `detekt SwallowedException` = **0**.
- [x] **S3.8** Zero dead code, zero commented-out code, zero unaddressed TODOs — **VERIFIED.**
- [x] **S3.9** Duplication below 3% — **VERIFIED:** `jscpd` = **0.71%** (down from 3.32%).
- [x] **S3.10** detekt / ktlint / Android Lint: zero errors, zero warnings — **VERIFIED:** `0 / 0 / 0` (down from 610 lint warnings).
- [x] **S3.11** Formatting enforced automatically in CI — **VERIFIED:** `ci.yml` and `release.yml` run `ktlintCheck`.

## S4. Readability and Onboarding — **10 / 10** (9 / 9)
- [x] **S4.1** Accurate `README.md` — **VERIFIED:** [`README.md`](../README.md).
- [x] **S4.2** `docs/ARCHITECTURE.md` — **VERIFIED:** [`docs/ARCHITECTURE.md`](ARCHITECTURE.md).
- [x] **S4.3** `docs/adr/` with ADRs — **VERIFIED:** 5 ADRs (`0001`–`0005`).
- [x] **S4.4** `CONTRIBUTING.md` — **VERIFIED:** [`CONTRIBUTING.md`](../CONTRIBUTING.md).
- [x] **S4.5** Every module and package has a short README — **VERIFIED:** 10 package/module `README.md`s.
- [x] **S4.6** Every public class/function has KDoc — **VERIFIED:** 100% CI-enforced by `ArchitectureRulesTest`.
- [x] **S4.7** Complex logic has WHY comments — **VERIFIED.**
- [x] **S4.8** `docs/HOW_TO.md` with tested step-by-step recipes — **VERIFIED:** [`docs/HOW_TO.md`](HOW_TO.md).
- [x] **S4.9** Cold-read test (10/10 by fresh subagent, recorded in docs) — **VERIFIED:** **10/10** in [`docs/COLD_READ_TEST.md`](COLD_READ_TEST.md).

## S5. Extensibility and Replaceability — **10 / 10** (5 / 5)
- [x] **S5.1** Each feature is self-contained with clear public interface and hidden internals — **VERIFIED.**
- [x] **S5.2** Cross-feature communication goes only through defined interfaces — **VERIFIED.**
- [x] **S5.3** Replaceability proof for 2 components — **VERIFIED:** `BlockerViewModelAndReplaceabilityTest.kt` (`SettingsRepository` + `GatekeeperClient`).
- [x] **S5.4** Extension proof following `docs/HOW_TO.md` — **VERIFIED:** [`docs/EXTENSION_PROOF.md`](EXTENSION_PROOF.md) (1 existing file touched).
- [x] **S5.5** Small, documented set of touch points for adding a feature — **VERIFIED:** ≤4 touch points in [`docs/HOW_TO.md`](HOW_TO.md).

## S6. Release Pipeline Security — **9 / 10** (12 / 13)
- [x] **S6.1** Every third-party action pinned to full 40-char commit SHA with version comment — **VERIFIED.**
- [x] **S6.2** Top-level `permissions: {}`; write scopes per-job only; repo default `GITHUB_TOKEN` set to `read` — **VERIFIED** via `gh api`.
- [x] **S6.3** No `pull_request_target` or `workflow_run` running untrusted code with secrets — **VERIFIED.**
- [x] **S6.4** No script injection (`${{ ... }}` in `run:`) — **VERIFIED:** `zizmor template-injection` = 0.
- [ ] **S6.5** Signing keys scoped exclusively to protected GitHub Environment; decoded to temp file & deleted after use — **PARTIAL (1 owner step left):** Protected `release` Environment created via API (ID `28665925494`, `@mehad605` required reviewer, `v*.*.*` tag + `main` policy), workflow binds `environment: release`, decodes to `0600` temp file under `$RUNNER_TEMP`, and shreds in `if: always()`. Only moving the 4 existing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) from Repository Secrets into `release` Environment Secrets requires owner action because GitHub's API never exposes existing secret values. See [`docs/BLOCKERS.md`](BLOCKERS.md).
- [x] **S6.6** Releases triggered only from protected tags/branches — **VERIFIED:** Workflow enforces SemVer `v[0-9]+.[0-9]+.[0-9]+` & defaults `workflow_dispatch` to `dry_run: true`; active GitHub rulesets **`Protect main`** (ID `28177034`) and **`Protect release tags (v*)`** (ID `28177036`, admin-only) configured via API.
- [x] **S6.7** Gradle wrapper validation (`gradle/actions/wrapper-validation`) — **VERIFIED.**
- [x] **S6.8** Build provenance attestation and SBOM — **VERIFIED:** `actions/attest-build-provenance` + CycloneDX 1.5 SBOM (`:app:generateReleaseSbom`, 77 components).
- [x] **S6.9** SHA-256 checksums published beside artifacts — **VERIFIED:** `SHA256SUMS.txt`.
- [x] **S6.10** Dependabot configured for Actions and Gradle — **VERIFIED:** `.github/dependabot.yml` (weekly + 7-day cooldown) + Dependabot security updates enabled via API.
- [x] **S6.11** Secret scanning and dependency review enabled — **VERIFIED:** `secret_scanning` + `secret_scanning_push_protection` + Vulnerability Alerts (Dependency Graph) enabled; `Dependency Review` job **PASSED** on PR #1 (`#45854033895`).
- [x] **S6.12** `actionlint` and `zizmor` report zero findings — **VERIFIED:** `actionlint v1.7.12` = **0 errors**, `zizmor v1.33.1` = **0 findings**.
- [x] **S6.13** Recommended repo settings documented as checklist — **VERIFIED:** [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md).

## S7. Release Pipeline Reliability and Maintainability — **10 / 10** (11 / 11)
- [x] **S7.1** Deterministic (pinned JDK 17 Temurin, Gradle 8.10.2, AGP 8.7.2) — **VERIFIED.**
- [x] **S7.2** Version name & code derived from tag or single source of truth — **VERIFIED:** `version.properties` + `MAJOR * 10000 + MINOR * 100 + PATCH`.
- [x] **S7.3** Pipeline runs lint, tests, and release build before publishing — **VERIFIED.**
- [x] **S7.4** Built APK verified (`apksigner verify`, size budget, version check, baseline profile, 0 `.so`) — **VERIFIED:** `scripts/verify_release_apk.sh`.
- [x] **S7.5** Fail fast with `timeout-minutes` on every job and `concurrency` — **VERIFIED.**
- [x] **S7.6** Gradle & dependency caching configured safely — **VERIFIED:** `cache-disabled: true` on all release jobs (`zizmor cache-poisoning` = 0).
- [x] **S7.7** Release notes/changelog generated automatically — **VERIFIED:** `gh release create ... --generate-notes`.
- [x] **S7.8** Release idempotent & re-runnable; no half-published release — **VERIFIED:** draft → upload `--clobber` → publish.
- [x] **S7.9** No duplicated logic (composite action) — **VERIFIED:** `.github/actions/setup-android-build/action.yml` via `uses: $/...`.
- [x] **S7.10** Documented in `docs/RELEASING.md` — **VERIFIED:** [`docs/RELEASING.md`](RELEASING.md).
- [x] **S7.11** Pipeline dry-run tested with run links/logs as evidence — **VERIFIED:**
  - `CI Verification` (push): [`#45852284519`](https://github.com/mehad605/Warden/actions/runs/45852284519) — **SUCCESS**
  - `CI Verification` (PR #1 + `Dependency Review`): [`#45854033895`](https://github.com/mehad605/Warden/actions/runs/45854033895) — **SUCCESS**
  - `Build & Release Warden` (`dry_run: true`, `v2.0.0`): [`#45852288716`](https://github.com/mehad605/Warden/actions/runs/45852288716) — **SUCCESS** (`warden-release-dryrun-v2.0.0`, `1,136,715 B`).

## S8. Correctness and Regression Safety — GATE — **10 / 10** (5 / 5)
- [x] **S8.1** All characterization & smoke tests pass on debug and release variants — **VERIFIED:** **45 / 45 PASS** on `testDebugUnitTest` + `testReleaseUnitTest`; R8-minified APK verified via `apksigner verify`, `aapt2`, and live Android 11 (API 30) emulator execution.
- [x] **S8.2** `./gradlew ktlintCheck detekt lint testDebugUnitTest testReleaseUnitTest assembleRelease` passes — **VERIFIED** locally and in CI.
- [x] **S8.3** No new crashes in logcat during core flows on release build (including in-place upgrade over `baseline-v1`) — **VERIFIED on Android 11 (API 30) emulator:** `adb install -r` over `baseline-v1` succeeded with `settings.json` preserved verbatim, 15 Activity open/close cycles + Settings accessibility traversal completed with **0 fatal crashes in `adb logcat`** and **no `crash_log.txt` created**.
- [x] **S8.4** Coverage of core contract is 100% of listed behaviors; new code has tests — **VERIFIED:** 45 automated tests covering `C1`–`C17`, architecture rules, and 2-component replaceability.
- [x] **S8.5** Feature parity with `baseline-v1` proven in `docs/PARITY.md` — **VERIFIED:** **17 / 17 PASS** in [`docs/PARITY.md`](PARITY.md).
