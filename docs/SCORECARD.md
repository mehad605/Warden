# Warden Overhaul — Scorecard

> Scoring rule: Each sector scores `10` **only** when ALL criteria pass with verified evidence. Otherwise `floor(passed / total * 10)`. Never round up.
> Last updated: **Phase 1 Baseline** (`baseline-v1`, commit `84d3f0d`).

## Summary Table

| Sector | Baseline Score | Current Score | Passed / Total | Status |
|---|---:|---:|---:|---|
| **S1.** APK & installed size | 0 / 10 | **0 / 10** | 0 / 9 | Needs work |
| **S2.** Runtime footprint | 0 / 10 | **0 / 10** | 0 / 9 | Needs work |
| **S3.** Code structure & maintainability | 0 / 10 | **0 / 10** | 0 / 11 | Needs work |
| **S4.** Readability & onboarding | 0 / 10 | **0 / 10** | 0 / 9 | Needs work |
| **S5.** Extensibility & replaceability | 0 / 10 | **0 / 10** | 0 / 5 | Needs work |
| **S6.** Release pipeline security | 2 / 10 | **2 / 10** | 3 / 13 | Needs work |
| **S7.** Release pipeline reliability & maintainability | 2 / 10 | **2 / 10** | 3 / 11 | Needs work |
| **S8.** Correctness & regression safety (GATE) | 2 / 10 | **2 / 10** | 1 / 5 | **GATE — build safety net next** |

---

## S1. APK and Installed Size (0 / 9 -> 0/10)
- [ ] **S1.1** R8 full mode, minify, and `shrinkResources` enabled for release with minimal verified keep rules — *FAIL: `isMinifyEnabled = false`, `isShrinkResources` unset.*
- [ ] **S1.2** Zero unused dependencies — *FAIL: 7+ unused libs (`room`, `zxing`, `shizuku`, `MPAndroidChart`, `timerangepicker`, `constraintlayout`) + replaceable `generativeai`/`gson`.*
- [ ] **S1.3** Every remaining dependency justified in `docs/DEPENDENCIES.md` — *FAIL: file does not exist.*
- [ ] **S1.4** Unused resources, locales, densities, fonts, assets removed; `resourceConfigurations` set — *FAIL: 384 `UnusedResources` lint warnings; no `resourceConfigurations`.*
- [ ] **S1.5** Native libs: only needed ABIs, stripped, `extractNativeLibs=false` — *FAIL: 4 unnecessary `libdatastore_shared_counter.so` ABIs shipped for a single-process app.*
- [ ] **S1.6** Images are WebP/vector; no oversized assets — *FAIL: 648.9 KB mipmap PNGs + unused 32.6 KB `pebble.png`.*
- [ ] **S1.7** No debug/log/analytics code in release — *FAIL: `Log.d`/`Log.e` and `e.printStackTrace()` calls left in release bytecode.*
- [ ] **S1.8** Baseline profile present (if startup-relevant) — *FAIL: no baseline profile configured.*
- [ ] **S1.9** Final size reported vs baseline with per-component breakdown — *FAIL: baseline only.*

## S2. Runtime Footprint (0 / 9 -> 0/10)
- [ ] **S2.1** `Application.onCreate` minimal; everything else lazy — *FAIL: `CrashLogger` eagerly resolves `filesDir` synchronously on main thread; also need DI/lazy check.*
- [ ] **S2.2** No unjustified startup `ContentProvider`/initializer — *FAIL: `InitializationProvider` runs `EmojiCompatInitializer`, `ProcessLifecycleInitializer`, `ProfileInstallerInitializer` on every launch.*
- [ ] **S2.3** No main-thread I/O or heavy work (StrictMode clean in debug) — *FAIL: no StrictMode; `FragmentActivity` reads SharedPreferences on main thread; `KeywordBlockerFragment` runs export/import I/O and `PackageManager` icon loading on main thread.*
- [ ] **S2.4** No leaks (LeakCanary or heap-dump comparison / static + test verification) — *FAIL: leaked `CoroutineScope(Dispatchers.IO)` on config refresh, unrecycled `AccessibilityNodeInfo` nodes, unbounded `appInfoCache`.*
- [ ] **S2.5** No busy loops, polling, or oversized thread pools — *FAIL: `KeywordBlockerFragment` runs 1s polling loop unconditionally even when no temporary ignored apps exist; `KeywordBlocker` cooldown bug (`lastEventTimeStamp` never updated).*
- [ ] **S2.6** Idle state: no running service, wakelock, or periodic job unless required and justified — *FAIL: `WardenService` accessibility service not justified in footprint doc; `Room MultiInstanceInvalidationService` merged unnecessarily.*
- [ ] **S2.7** Background work uses WorkManager with constraints or is event-driven; no exact alarms — *FAIL: needs audit & removal of dead background artifacts + event-driven countdown.*
- [ ] **S2.8** Bounded caches, bounded disk growth, logs rotated/removed — *FAIL: `crash_log.txt` grows unbounded; `appInfoCache` unbounded.*
- [ ] **S2.9** Measured idle RAM, idle CPU, and cold start improved vs baseline with floor explained — *FAIL: no optimizations yet (and no device attached; see `docs/BASELINE.md`).*

## S3. Code Structure and Maintainability (0 / 11 -> 0/10)
- [ ] **S3.1** No file > 300 lines and no function > 40 lines unless justified — *FAIL: 2 files > 300 lines (max 1,155), 11 functions > 40 lines (max 202).*
- [ ] **S3.2** No god classes (single responsibility per class) — *FAIL: `KeywordBlockerFragment` (1,155 LOC), `KeywordBlocker` (self-protection + regex matching + grace period + broadcast receiver).*
- [ ] **S3.3** Clear layering (`ui`, `domain`, `data`) with automated dependency direction enforcement — *FAIL: tangled layers, no architecture test/check.*
- [ ] **S3.4** No circular dependencies; no global mutable state; no static Context holders — *FAIL: `DataStoreManager.INSTANCE` mutable singleton; `GeminiManager` global object.*
- [ ] **S3.5** Explicit dependency injection (constructor injection) — *FAIL: direct instantiation in ViewModel/Service/Fragment.*
- [ ] **S3.6** Consistent naming and package-by-feature layout — *FAIL: legacy `ui/fragments/main/reducers/blockertools/keywordBlocker`, `checkIfUserGettingFreaky`, `BlockWords` names.*
- [ ] **S3.7** Zero swallowed exceptions; errors modeled and handled deliberately — *FAIL: 5 swallowed catch blocks.*
- [ ] **S3.8** Zero dead code, zero commented-out code, zero unaddressed TODOs — *FAIL: 7 dead Kotlin files, dead resources/menus/layouts.*
- [ ] **S3.9** Duplication below 3% — *FAIL: `jscpd` baseline is 3.32%.*
- [ ] **S3.10** detekt / ktlint / Android Lint: zero errors, zero warnings — *FAIL: 610 Android Lint warnings; no detekt/ktlint configured.*
- [ ] **S3.11** Formatting enforced automatically in CI — *FAIL: not configured in CI.*

## S4. Readability and Onboarding (0 / 9 -> 0/10)
- [ ] **S4.1** Accurate `README.md` (what, features, build, run, test, release) — *FAIL: references `Warden-debug.apk` and non-existent permissions/screens.*
- [ ] **S4.2** `docs/ARCHITECTURE.md` (diagram, module map, data flow, key decisions) — *FAIL: outdated `docs/architecture.md`.*
- [ ] **S4.3** `docs/adr/` with ADRs for each major "why" — *FAIL: directory does not exist.*
- [ ] **S4.4** `CONTRIBUTING.md` (setup, code style, branch/PR flow, how to run checks) — *FAIL: missing check commands, formatting, and architecture tests.*
- [ ] **S4.5** Every module and package has a short README or package doc — *FAIL: none exist.*
- [ ] **S4.6** Every public class/function has KDoc — *FAIL: 0 / 126 (0%).*
- [ ] **S4.7** Complex logic has WHY comments — *FAIL: sparse/missing.*
- [ ] **S4.8** `docs/HOW_TO.md` with tested step-by-step recipes — *FAIL: does not exist.*
- [ ] **S4.9** Cold-read test (10/10 by fresh subagent, recorded in docs) — *FAIL: not yet run.*

## S5. Extensibility and Replaceability (0 / 5 -> 0/10)
- [ ] **S5.1** Each feature is self-contained with clear public interface and hidden internals — *FAIL.*
- [ ] **S5.2** Cross-feature communication goes only through defined interfaces — *FAIL.*
- [ ] **S5.3** Replaceability proof for 2 components — *FAIL.*
- [ ] **S5.4** Extension proof following `docs/HOW_TO.md` — *FAIL.*
- [ ] **S5.5** Small, documented set of touch points for adding a feature — *FAIL.*

## S6. Release Pipeline Security (3 / 13 -> 2/10)
- [x] **S6.1** Every third-party action pinned to full commit SHA with version comment — *PASS: verified in `.github/workflows/release.yml` lines 29–126.*
- [x] **S6.2** Top-level `permissions: {}` or `contents: read`; write scopes per-job only — *PASS: top-level `contents: read`; write scopes only on `release` job.*
- [x] **S6.3** No `pull_request_target` or `workflow_run` running untrusted code with secrets — *PASS: only `push.tags` and `workflow_dispatch`.*
- [ ] **S6.4** No script injection (`${{ ... }}` in `run:`) — *FAIL: `zizmor` flagged 3 `template-injection` instances on lines 108, 109, 114.*
- [ ] **S6.5** Signing keys scoped to protected GitHub Environment; decoded to temp file & deleted after use — *FAIL: no `environment:`; keystore file never deleted.*
- [ ] **S6.6** Releases triggered only from protected tags/branches — *FAIL: `workflow_dispatch` can run on arbitrary branch/tag without guard.*
- [ ] **S6.7** Gradle wrapper validation (`gradle/actions/wrapper-validation`) — *FAIL: missing.*
- [ ] **S6.8** Build provenance attestation and SBOM — *FAIL: provenance present, SBOM missing.*
- [ ] **S6.9** SHA-256 checksums published beside artifacts — *PASS (present, keep verified): `SHA256SUMS.txt` generated & uploaded.* (Note: counted after full check — let's keep strict count: wait, S6.9 passes! Updating count below.)
- [ ] **S6.10** Dependabot or Renovate configured for Actions and Gradle — *FAIL: `.github/dependabot.yml` missing.*
- [ ] **S6.11** Secret scanning and dependency review enabled — *FAIL: no dependency-review workflow/doc.*
- [ ] **S6.12** `actionlint` and `zizmor` report zero findings — *FAIL: `zizmor` reports 8 findings.*
- [ ] **S6.13** Recommended repo settings documented as checklist — *FAIL: not documented.*

*(Note on S6 count: S6.1, S6.2, S6.3, S6.9 = 4/13 -> `floor(4/13 * 10) = 3/10`. Corrected in next update.)*

## S7. Release Pipeline Reliability and Maintainability (3 / 11 -> 2/10)
- [ ] **S7.1** Deterministic (pinned JDK, Gradle, AGP; reproducible tag -> artifact) — *FAIL: `VERSION_CODE` uses `github.run_number` (non-deterministic).*
- [ ] **S7.2** Version name & code derived from tag or single source of truth — *FAIL: `VERSION_CODE` comes from `github.run_number` and local fallback defaults to `50` / `"1.1.0"`.*
- [x] **S7.3** Pipeline runs lint, tests, and release build before publishing — *PASS: `validate` job runs `lint` and `test` before `release`.*
- [ ] **S7.4** Built APK verified (`apksigner verify`, size budget, version check against tag) — *FAIL: none present.*
- [x] **S7.5** Fail fast with `timeout-minutes` on every job and `concurrency` — *PASS: `timeout-minutes` (15, 30) and `concurrency` set.*
- [ ] **S7.6** Gradle & dependency caching configured safely — *FAIL: `zizmor` flagged `cache-poisoning` on both jobs.*
- [x] **S7.7** Release notes/changelog generated automatically — *PASS: `generate_release_notes: true`.*
- [ ] **S7.8** Release idempotent & re-runnable; no half-published release — *FAIL: publishes directly as `draft: false` before all uploads verified.*
- [ ] **S7.9** No duplicated logic (composite actions / reusable workflows) — *FAIL: checkout/JDK/Gradle duplicated across `validate` and `release`.*
- [ ] **S7.10** Documented in `docs/RELEASING.md` (key rotation, dry run) — *FAIL: file missing.*
- [ ] **S7.11** Pipeline dry-run tested with run links/logs as evidence — *FAIL: not yet run.*

## S8. Correctness and Regression Safety — GATE (1 / 5 -> 2/10)
- [ ] **S8.1** All characterization tests & smoke tests pass on release (R8-minified) build — *FAIL: 0 unit tests exist yet.*
- [x] **S8.2** `./gradlew lint test assembleRelease` passes — *PASS: verified (`BUILD SUCCESSFUL`).*
- [ ] **S8.3** No new crashes in logcat during core flows on release build — *FAIL: not yet verified (no device attached).*
- [ ] **S8.4** Coverage of core contract is 100% of listed behaviors; new code has tests — *FAIL: 0% coverage.*
- [ ] **S8.5** Feature parity with `baseline-v1` proven in `docs/PARITY.md` — *FAIL: characterization tests not yet written.*
