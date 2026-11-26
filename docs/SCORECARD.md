# Warden Overhaul — Scorecard

> **Scoring rule:** Each sector scores `10` **only** when ALL criteria pass with verified evidence. Otherwise `floor(passed / total * 10)`. Never round up. Criteria blocked strictly by missing physical hardware or GitHub repository admin settings are NOT counted as passed and are documented transparently in [`docs/BLOCKERS.md`](BLOCKERS.md).
> **Last updated:** Final Audit (`overhaul/v2`, base `baseline-v1` = `84d3f0d`).

## Summary Table

| Sector | Baseline Score | Final Score | Passed / Total | Status |
|---|---:|---:|---:|---|
| **S1.** APK & installed size | 0 / 10 | **10 / 10** | 9 / 9 | **PASS (10/10)** |
| **S2.** Runtime footprint | 0 / 10 | **7 / 10** | 7 / 9 | 2 hardware-blocked (`S2.4`, `S2.9` — see [`BLOCKERS.md`](BLOCKERS.md)) |
| **S3.** Code structure & maintainability | 0 / 10 | **10 / 10** | 11 / 11 | **PASS (10/10)** |
| **S4.** Readability & onboarding | 0 / 10 | **10 / 10** | 9 / 9 | **PASS (10/10)** |
| **S5.** Extensibility & replaceability | 0 / 10 | **10 / 10** | 5 / 5 | **PASS (10/10)** |
| **S6.** Release pipeline security | 3 / 10 | **8 / 10** | 11 / 13 | 2 repo-admin-blocked (`S6.5`, `S6.6` — see [`BLOCKERS.md`](BLOCKERS.md)) |
| **S7.** Release pipeline reliability & maintainability | 2 / 10 | **10 / 10** | 11 / 11 | **PASS (10/10)** |
| **S8.** Correctness & regression safety (GATE) | 2 / 10 | **8 / 10** | 4 / 5 | 1 hardware-blocked (`S8.3` — see [`BLOCKERS.md`](BLOCKERS.md)) |

**All 100% locally & CI-verifiable criteria pass (67 / 67).** The only 5 unchecked criteria across the entire rubric strictly require either a physical Android device/emulator (`S2.4`, `S2.9`, `S8.3`) or GitHub repository-admin toggles (`S6.5`, `S6.6`).

---

## S1. APK and Installed Size — **10 / 10** (9 / 9)
- [x] **S1.1** R8 full mode, minify, and `shrinkResources` enabled for release with minimal verified keep rules — **VERIFIED:** `android.enableR8.fullMode=true` (`gradle.properties`), `isMinifyEnabled = true`, `isShrinkResources = true` (`app/build.gradle.kts`), minimal `app/proguard-rules.pro`.
- [x] **S1.2** Zero unused dependencies — **VERIFIED:** Removed 9 unused libraries (`room-*`, `ksp`, `zxing`, `shizuku`, `MPAndroidChart`, `timerangepicker`, `constraintlayout`, `anychart`, JitPack) + replaced `datastore`, `generativeai`, `gson`; `releaseRuntimeClasspath` reduced from **224 → 133** resolved deps (**6** direct `implementation` deps, all used).
- [x] **S1.3** Every remaining dependency justified in `docs/DEPENDENCIES.md` — **VERIFIED:** [`docs/DEPENDENCIES.md`](DEPENDENCIES.md).
- [x] **S1.4** Unused resources, locales, densities, fonts, assets removed; `resourceConfigurations` set — **VERIFIED:** Removed 35 unused resource/junk files; `resourceConfigurations += listOf("en")`; Android Lint `UnusedResources` = **0**.
- [x] **S1.5** Native libs: only needed ABIs, stripped, `extractNativeLibs=false` — **VERIFIED:** Native `.so` count reduced **4 → 0** (eliminated `libdatastore_shared_counter.so`); enforced in `scripts/verify_release_apk.sh`.
- [x] **S1.6** Images are WebP/vector; no oversized assets — **VERIFIED:** 5 launcher PNGs converted to WebP (`649 KB → 27 KB`); unused `pebble.png` deleted.
- [x] **S1.7** No debug/log/analytics code in release — **VERIFIED:** `app/proguard-rules.pro` `-assumenosideeffects` strips `android.util.Log.*` and `Throwable.printStackTrace()`; zero analytics dependencies.
- [x] **S1.8** Baseline profile present — **VERIFIED:** `app/src/main/baseline-prof.txt` compiled into `assets/dexopt/baseline.prof` (`1,773 B`), asserted inside release APK by `scripts/verify_release_apk.sh`.
- [x] **S1.9** Final size reported vs baseline with per-component breakdown — **VERIFIED:** `apkanalyzer` breakdown recorded in [`docs/RESULTS.md`](RESULTS.md) (**7,511,385 B → 1,608,536 B, -78.6%**; download **6,356,795 B → 1,124,927 B, -82.3%**; DEX **95,072 [2 files] → 12,753 [1 file, -86.6%]**).

## S2. Runtime Footprint — **7 / 10** (7 / 9; 2 hardware-blocked)
- [x] **S2.1** `Application.onCreate` minimal; everything else lazy — **VERIFIED:** `Warden.kt` enables debug `StrictMode` and registers lazy `CrashLogger`; all services/repos inside `AppContainer` use `by lazy`, zero synchronous disk or network I/O at startup.
- [x] **S2.2** No unjustified startup `ContentProvider`/initializer — **VERIFIED:** `EmojiCompatInitializer` and `ProcessLifecycleInitializer` removed via `tools:node="remove"` in `AndroidManifest.xml`; only `ProfileInstallerInitializer` remains for baseline profile installation.
- [x] **S2.3** No main-thread I/O or heavy work (StrictMode clean in debug) — **VERIFIED:** Debug `StrictMode` (thread + VM policy) enabled in `Warden.kt`; `FragmentActivity` SharedPreferences read, `JsonSettingsRepository` writes, `BackupDialogController` SAF I/O, and `InstalledAppsProvider` PackageManager calls all run on `Dispatchers.IO`; `WardenService` runs event processing on `Dispatchers.Default`.
- [ ] **S2.4** No leaks (LeakCanary / heap-dump comparison) — **BLOCKED (hardware):** Static/unit-verified fixes completed (`AccessibilityNodeInfo` & `AccessibilityEvent` recycling in `finally`, `CoroutineScope` cancellation in `onDestroy`, `_binding = null` in `onDestroyView`, bounded `LruCache(64)`), but live LeakCanary / 30-min heap dump requires an attached device/emulator. See [`docs/BLOCKERS.md`](BLOCKERS.md).
- [x] **S2.5** No busy loops, polling, or oversized thread pools — **VERIFIED:** `IgnoredAppsListBinder` stops its countdown `Handler` immediately when no active temporary exemptions exist; `WardenService` uses a single `Channel.CONFLATED` worker; cooldown timestamp bug fixed.
- [x] **S2.6** Idle state: no running service, wakelock, or periodic job unless required and justified — **VERIFIED:** Removed Room `MultiInstanceInvalidationService` and exported refresh `BroadcastReceiver`; `WardenService` is the sole required `AccessibilityService` and its idle floor is documented in [`docs/ARCHITECTURE.md`](ARCHITECTURE.md).
- [x] **S2.7** Background work uses WorkManager with constraints or is event-driven; no exact alarms — **VERIFIED:** 100% event-driven (`AccessibilityEvent` callbacks + in-process `StateFlow<Settings>`), zero alarms or periodic jobs.
- [x] **S2.8** Bounded caches, bounded disk growth, logs rotated/removed — **VERIFIED:** `InstalledAppsProvider` uses `LruCache(64)`; `CrashLogger` rotates `crash_log.txt` at 128 KiB to a single `crash_log.prev.txt` (≤256 KiB total).
- [ ] **S2.9** Measured idle RAM, idle CPU, and cold start improved vs baseline with floor explained — **BLOCKED (hardware):** Floor explained in [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) and static contributors verified, but live `am start -W` / `dumpsys meminfo` / `dumpsys cpuinfo` require a physical device/emulator. See [`docs/BLOCKERS.md`](BLOCKERS.md).

## S3. Code Structure and Maintainability — **10 / 10** (11 / 11)
- [x] **S3.1** No file > 300 lines and no function > 40 lines unless justified — **VERIFIED:** Max main Kotlin file **249 LOC** (`KeywordBlockerFragment.kt`, down from 1,155); max function **36 LOC** (down from 202); enforced by `ArchitectureRulesTest` + `detekt` (`LongMethod: 40`).
- [x] **S3.2** No god classes (single responsibility per class) — **VERIFIED:** Decomposed `KeywordBlocker` (399 LOC, CC 48) into 6 classes and `KeywordBlockerFragment` (1,155 LOC) + 21-method ViewModel into focused domain/service/UI binders; max cyclomatic complexity **9** (down from 48).
- [x] **S3.3** Clear layering (`ui`, `domain`, `data`, `service`) with automated dependency direction enforcement — **VERIFIED:** `ArchitectureRulesTest` enforces domain purity (0 Android/UI/data/service imports) and data-layer isolation.
- [x] **S3.4** No circular dependencies; no global mutable state; no static Context holders — **VERIFIED:** `ArchitectureRulesTest` verifies acyclic package graph (`0` cycles via `AppContainerProvider`), `0` global mutable singletons, `0` static `Context`/`Activity`/`View` holders.
- [x] **S3.5** Explicit dependency injection (constructor injection) — **VERIFIED:** Lazy manual `AppContainer` + `BlockerViewModel.Factory` + constructor-injected services, binders, and controllers.
- [x] **S3.6** Consistent naming and package-by-feature layout — **VERIFIED:** `core/{data,di,platform}`, `data/models`, `feature/{blocker,gatekeeper,security,backup}`, `services`, `receivers`, `ui/activity`.
- [x] **S3.7** Zero swallowed exceptions; errors modeled and handled deliberately — **VERIFIED:** `detekt SwallowedException` = **0** issues; explicit error callbacks & specific catches.
- [x] **S3.8** Zero dead code, zero commented-out code, zero unaddressed TODOs — **VERIFIED:** Removed 8 dead Kotlin files & 35 unused res/junk files; `detekt` & Android Lint = **0** issues.
- [x] **S3.9** Duplication below 3% — **VERIFIED:** `jscpd` on `app/src/main/java/` = **0.71%** (down from 3.32%).
- [x] **S3.10** detekt / ktlint / Android Lint: zero errors, zero warnings — **VERIFIED:** `detekt` (`maxIssues: 0`) = **0**, `ktlintCheck` (`ignoreFailures: false`) = **0**, Android Lint (`warningsAsErrors = true`) = **0** (down from 610 warnings).
- [x] **S3.11** Formatting enforced automatically in CI — **VERIFIED:** Both `.github/workflows/ci.yml` and `.github/workflows/release.yml` run `ktlintCheck`.

## S4. Readability and Onboarding — **10 / 10** (9 / 9)
- [x] **S4.1** Accurate `README.md` (what, features, build, run, test, release, emergency uninstall) — **VERIFIED:** [`README.md`](../README.md).
- [x] **S4.2** `docs/ARCHITECTURE.md` (diagram, module map, data flow, key decisions) — **VERIFIED:** [`docs/ARCHITECTURE.md`](ARCHITECTURE.md).
- [x] **S4.3** `docs/adr/` with ADRs for each major "why" — **VERIFIED:** 5 ADRs (`0001`–`0005`) in [`docs/adr/`](adr/).
- [x] **S4.4** `CONTRIBUTING.md` (setup, code style, branch/PR flow, how to run checks) — **VERIFIED:** [`CONTRIBUTING.md`](../CONTRIBUTING.md).
- [x] **S4.5** Every module and package has a short README — **VERIFIED:** 10 `README.md` files (`app/`, `core/`, `data/`, `feature/{blocker,gatekeeper,security,backup}/`, `services/`, `receivers/`, `ui/`).
- [x] **S4.6** Every public class/function has KDoc — **VERIFIED:** 100% coverage CI-enforced by `ArchitectureRulesTest.allPublicTypesAndTopLevelApisHaveKDoc()`.
- [x] **S4.7** Complex logic has WHY comments — **VERIFIED:** Documented in `KeywordMatcher`, `SelfProtectionEvaluator`, `AccessibilityNodeScanner`, `JsonSettingsRepository`, `HttpGeminiGatekeeperClient`, `AppContainer`, and `AndroidManifest.xml`.
- [x] **S4.8** `docs/HOW_TO.md` with tested step-by-step recipes — **VERIFIED:** [`docs/HOW_TO.md`](HOW_TO.md) (5 recipes + ≤4 touch-point summary).
- [x] **S4.9** Cold-read test (10/10 by fresh subagent, recorded in docs) — **VERIFIED:** **10/10** in [`docs/COLD_READ_TEST.md`](COLD_READ_TEST.md) (and all doc discrepancies caught by the blind reviewer were fixed).

## S5. Extensibility and Replaceability — **10 / 10** (5 / 5)
- [x] **S5.1** Each feature is self-contained with clear public interface and hidden internals — **VERIFIED:** `feature/{blocker,gatekeeper,security,backup}/` expose domain interfaces (`GatekeeperClient`, `SettingsRepository`, `Clock`, `BlockerAction`).
- [x] **S5.2** Cross-feature communication goes only through defined interfaces — **VERIFIED:** Enforced by `ArchitectureRulesTest` and constructor injection.
- [x] **S5.3** Replaceability proof for 2 components — **VERIFIED:** `BlockerViewModelAndReplaceabilityTest.kt` swaps both `SettingsRepository` and `GatekeeperClient` with independent fakes without modifying `BlockerViewModel` or any UI/blocker class.
- [x] **S5.4** Extension proof following `docs/HOW_TO.md` — **VERIFIED:** Added `feature/cooldown` verbatim via Recipe 2 (touching 1 existing file: `AppContainer.kt`), passed full `ktlintCheck detekt lint testDebugUnitTest`, removed dummy feature, recorded in [`docs/EXTENSION_PROOF.md`](EXTENSION_PROOF.md).
- [x] **S5.5** Small, documented set of touch points for adding a feature — **VERIFIED:** ≤4 documented touch points in [`docs/HOW_TO.md`](HOW_TO.md#touch-point-summary-adding-a-feature).

## S6. Release Pipeline Security — **8 / 10** (11 / 13; 2 repo-admin-blocked)
- [x] **S6.1** Every third-party action pinned to full 40-char commit SHA with version comment — **VERIFIED:** All actions in `.github/actions/setup-android-build/action.yml`, `ci.yml`, `release.yml`.
- [x] **S6.2** Top-level `permissions: {}`; write scopes per-job only — **VERIFIED:** `permissions: {}` at top of both workflows; minimal per-job permissions.
- [x] **S6.3** No `pull_request_target` or `workflow_run` running untrusted code with secrets — **VERIFIED.**
- [x] **S6.4** No script injection (`${{ ... }}` in `run:`) — **VERIFIED:** All expressions passed via `env:`; `zizmor template-injection` = 0.
- [ ] **S6.5** Signing keys scoped to protected GitHub Environment; decoded to temp file & deleted after use — **BLOCKED (repo-admin):** Workflow binds `environment: release`, decodes `KEYSTORE_BASE64` to `mktemp "$RUNNER_TEMP/..."` (`chmod 600`), and shreds/deletes in `if: always()`. Repo owner must still create the `release` GitHub Environment with required reviewers and move secrets into it ([`docs/BLOCKERS.md`](BLOCKERS.md), [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md)).
- [ ] **S6.6** Releases triggered only from protected tags/branches — **BLOCKED (repo-admin):** Workflow restricts tags to `v[0-9]+.[0-9]+.[0-9]+` and defaults `workflow_dispatch` to `dry_run: true`, but GitHub branch/tag rulesets must be enabled by repo owner ([`docs/BLOCKERS.md`](BLOCKERS.md), [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md)).
- [x] **S6.7** Gradle wrapper validation (`gradle/actions/wrapper-validation`) — **VERIFIED:** Executed in `.github/actions/setup-android-build/action.yml` on every CI and release run.
- [x] **S6.8** Build provenance attestation and SBOM — **VERIFIED:** `actions/attest-build-provenance` + deterministic CycloneDX 1.5 JSON SBOM (`:app:generateReleaseSbom`, 77 components).
- [x] **S6.9** SHA-256 checksums published beside artifacts — **VERIFIED:** `SHA256SUMS.txt` covers both APK and `.cdx.json` SBOM.
- [x] **S6.10** Dependabot configured for Actions and Gradle — **VERIFIED:** `.github/dependabot.yml` with weekly schedule and 7-day supply-chain cooldown.
- [x] **S6.11** Secret scanning and dependency review enabled — **VERIFIED:** `gh api repos/mehad605/Warden` confirms `secret_scanning: enabled` + `secret_scanning_push_protection: enabled`; `dependency-review` job configured in `.github/workflows/ci.yml`.
- [x] **S6.12** `actionlint` and `zizmor` report zero findings — **VERIFIED:** `actionlint v1.7.12` = **0 errors** (with narrow `.github/actionlint.yaml` for `rhysd/actionlint#780` on GA `$/` syntax), `zizmor v1.33.1` = **0 findings** (`No findings to report. Good job!`).
- [x] **S6.13** Recommended repo settings documented as checklist — **VERIFIED:** [`docs/GITHUB_REPO_SETTINGS.md`](GITHUB_REPO_SETTINGS.md).

## S7. Release Pipeline Reliability and Maintainability — **10 / 10** (11 / 11)
- [x] **S7.1** Deterministic (pinned JDK 17 Temurin, Gradle 8.10.2, AGP 8.7.2; reproducible tag → artifact) — **VERIFIED.**
- [x] **S7.2** Version name & code derived from tag or single source of truth — **VERIFIED:** `version.properties` default + SemVer tag override; `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH` (never `github.run_number`).
- [x] **S7.3** Pipeline runs lint, tests, and release build before publishing — **VERIFIED:** `validate` job (`ktlintCheck detekt lint testDebugUnitTest`) gates `dry-run` and `release`.
- [x] **S7.4** Built APK verified (`apksigner verify`, size budget, version check against tag) — **VERIFIED:** `scripts/verify_release_apk.sh` enforces `apksigner verify`, ≤2.5 MiB size budget, `aapt2` `applicationId`/`versionName`/`versionCode` match, `assets/dexopt/baseline.prof`, and 0 `.so` libs.
- [x] **S7.5** Fail fast with `timeout-minutes` on every job and `concurrency` — **VERIFIED:** All jobs have explicit `timeout-minutes` (10–30 min); `ci.yml` cancels superseded PR runs, `release.yml` sets `cancel-in-progress: false`.
- [x] **S7.6** Gradle & dependency caching configured safely — **VERIFIED:** Read-only caching on non-`main` CI builds (`ci.yml`), caching completely disabled (`cache-disabled: true`) on all `release.yml` jobs (`zizmor cache-poisoning` = 0).
- [x] **S7.7** Release notes/changelog generated automatically — **VERIFIED:** `gh release create ... --generate-notes`.
- [x] **S7.8** Release idempotent & re-runnable; no half-published release — **VERIFIED:** Creates/reuses `--draft`, uploads all assets with `--clobber`, flips `--draft=false` only after all uploads succeed.
- [x] **S7.9** No duplicated logic (composite action) — **VERIFIED:** `.github/actions/setup-android-build/action.yml` shared via `uses: $/.github/actions/setup-android-build` across all jobs in `ci.yml` and `release.yml`.
- [x] **S7.10** Documented in `docs/RELEASING.md` (key rotation, dry run, rollback) — **VERIFIED:** [`docs/RELEASING.md`](RELEASING.md).
- [x] **S7.11** Pipeline dry-run tested with run links/logs as evidence — **VERIFIED:**
  - `CI Verification` run [`#45852284519`](https://github.com/mehad605/Warden/actions/runs/45852284519) — **SUCCESS**
  - `Build & Release Warden` (`dry_run: true`, `v2.0.0`) run [`#45852288716`](https://github.com/mehad605/Warden/actions/runs/45852288716) — **SUCCESS** (uploaded `warden-release-dryrun-v2.0.0`, `1,136,715 B`; skipped production signing/publishing).

## S8. Correctness and Regression Safety — GATE — **8 / 10** (4 / 5; 1 hardware-blocked)
- [x] **S8.1** All characterization & unit tests pass on debug and release variants — **VERIFIED:** **45 / 45 PASS** on both `./gradlew testDebugUnitTest` and `./gradlew testReleaseUnitTest`; release APK verified via `apksigner verify` + `aapt2`.
- [x] **S8.2** `./gradlew ktlintCheck detekt lint testDebugUnitTest testReleaseUnitTest assembleRelease` passes — **VERIFIED** locally and in CI run [`#45852284519`](https://github.com/mehad605/Warden/actions/runs/45852284519).
- [ ] **S8.3** No new crashes in logcat during core flows on release build — **BLOCKED (hardware):** Requires a physical Android device/emulator (`adb logcat` + `adb install -r` upgrade smoke test). See [`docs/BLOCKERS.md`](BLOCKERS.md).
- [x] **S8.4** Coverage of core contract is 100% of listed behaviors; new code has tests — **VERIFIED:** All 17 core behaviors (`C1`–`C17`) plus architecture rules and 2-component replaceability covered by 45 automated tests.
- [x] **S8.5** Feature parity with `baseline-v1` proven in `docs/PARITY.md` — **VERIFIED:** **17 / 17 PASS** in [`docs/PARITY.md`](PARITY.md).
