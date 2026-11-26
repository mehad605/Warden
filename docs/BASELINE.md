# Warden — Phase 1 Baseline Measurements (`baseline-v1`)

> Measured on tag `baseline-v1` (commit `84d3f0d`) on Linux with OpenJDK `21.0.13.1`, Android SDK `compileSdk=34`, `build-tools/35.0.0`, `apkanalyzer`, `actionlint v1.7.12`, `zizmor v1.33.1`, and `jscpd v5.4.3`.
>
> **Environment disclosure:** No physical Android device or emulator is attached in this environment (`adb devices` returned empty; T3 Device hub is disabled). Therefore, all static/build/JVM metrics are **VERIFIED (measured)**, while on-device runtime metrics are marked **UNAVAILABLE (no device attached)** and runtime estimates are explicitly labeled **ESTIMATED**.

---

## 1. Build, APK, and Dependency Metrics (VERIFIED)

Command: `./gradlew assembleRelease lint test --no-daemon` -> **BUILD SUCCESSFUL** (88 tasks).
Artifact: `app/build/outputs/apk/release/Warden-release.apk` (`com.warden.app`, `versionCode=50`, `versionName=1.1.0`, unsigned/debug-signed locally, `isMinifyEnabled=false`).

| Metric | Baseline (`baseline-v1`) | Command / Source | Status |
|---|---:|---|---|
| Release APK file size (universal) | **7,511,385 bytes (7.16 MiB)** | `apkanalyzer apk file-size` | VERIFIED |
| Release APK download size | **6,356,795 bytes (6.06 MiB)** | `apkanalyzer apk download-size` | VERIFIED |
| Uncompressed APK content size | **17,084,080 bytes (16.29 MiB)** | `unzip -l` total | VERIFIED |
| DEX file count | **2** (`classes.dex` 9.39 MB, `classes2.dex` 5.57 MB) | `apkanalyzer dex references` | VERIFIED |
| DEX method references | **95,072** (`65,005` + `30,067`) | `apkanalyzer dex references` | VERIFIED |
| DEX defined methods | **80,088** (93,369 total refs in packages dump) | `apkanalyzer dex packages --defined-only` | VERIFIED |
| `resources.arsc` uncompressed | **1,181,852 bytes (1.13 MiB)** | `unzip -l` | VERIFIED |
| Native `.so` libraries | **4 ABIs** (`libdatastore_shared_counter.so`: arm64-v8a 10.4 KB, x86_64 9.4 KB, armeabi-v7a 8.4 KB, x86 8.0 KB) | `unzip -l` | VERIFIED |
| Raster PNGs in `app/src/main/res` | **681,485 bytes** (5 mipmap PNGs = 648.9 KB + `pebble.png` = 32.6 KB) | Python file-size scan | VERIFIED |
| `releaseRuntimeClasspath` dependencies | **224** unique resolved artifacts (19 top-level) | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` | VERIFIED |
| `extractNativeLibs` in merged manifest | `false` | `apkanalyzer manifest print` | VERIFIED |
| Startup `ContentProvider`s in merged manifest | 1 (`InitializationProvider` with 3 initializers: `EmojiCompatInitializer`, `ProcessLifecycleInitializer`, `ProfileInstallerInitializer`) | `apkanalyzer manifest print` | VERIFIED |
| Unwanted merged manifest components | `CaptureActivity` (ZXing), `CAMERA` permission + 6 hardware features, `moe.shizuku.manager.permission.API_V23`, `Room MultiInstanceInvalidationService` | `apkanalyzer manifest print` | VERIFIED |

### Top DEX Method Consumers (`apkanalyzer dex packages --defined-only`)
| Package | Defined Methods | Notes |
|---|---:|---|
| `androidx` (total) | 28,143 | Includes unused `constraintlayout` (3,826) & `room` |
| `com.google.android.material` | 10,602 | Unminified MD3 library |
| `kotlin` | 10,121 | Unminified Kotlin stdlib |
| `io.ktor` | 9,033 | Pulled in solely by `generativeai` SDK |
| `kotlinx` (coroutines + serialization) | 7,749 | `kotlinx.serialization` (2,738) from `generativeai` |
| `okhttp3` | 2,761 | + 41.4 KB `publicsuffixes.gz`, pulled in by `generativeai` |
| `com.github.mikephil.charting` | 2,087 | **100% UNUSED** (`MPAndroidChart`) |
| `com.google.ai.client.generativeai` | 1,923 | Replaceable with `HttpURLConnection` + `org.json` |
| `com.google.zxing` | 1,907 | **100% UNUSED** (+ 6.4 KB `res/E5.ogg` beep) |

### Device Runtime Metrics (UNAVAILABLE — No Device/Emulator Attached)
| Metric | Baseline (`baseline-v1`) | Status |
|---|---|---|
| Installed size (`dumpsys package` / `du -sh`) | Unavailable (est. ~20–25 MB with AOT/dexopt of 15 MB raw DEX) | UNAVAILABLE / ESTIMATED |
| Cold start (`am start -W`, median of 10) | Unavailable | UNAVAILABLE |
| Idle RAM / Active RAM PSS (`dumpsys meminfo`) | Unavailable | UNAVAILABLE |
| Idle CPU (`top` / `dumpsys cpuinfo`) | Unavailable | UNAVAILABLE |
| Background work (`jobscheduler`, `alarm`, `batterystats`) | Static check: 0 Jobs, 0 Alarms, 0 WakeLocks; 1 `AccessibilityService` (`WardenService`) + leaked IO coroutines on config refresh | STATICALLY VERIFIED |
| Jank (`dumpsys gfxinfo framestats`) | Unavailable | UNAVAILABLE |

---

## 2. Code-Health Baselines (VERIFIED)

### Size & Complexity (`app/src/main/java/**/*.kt`)
- **Total main Kotlin files:** 22 (2,901 lines)
- **Files > 300 lines:** 2
  1. `KeywordBlockerFragment.kt`: **1,155 lines**
  2. `KeywordBlocker.kt`: **399 lines**
- **Dead / unused Kotlin files (verified by full codebase symbol search):** 7 (`Constants.kt`, `FocusBlockMode.kt`, `AppFilter.kt`, `ColorUtils.kt`, `PackageFinderTools.kt`, `TimeTools.kt`, `SelectAppsActivity.kt`) + near-dead `BaseBlocker.kt`.
- **Total functions:** 137 | **Functions > 40 lines:** 11
- **Cyclomatic Complexity (approximate static branch count):** Max **48**, Average **3.04**, Functions > 10: **5**

#### Top 10 Largest Functions
| Function | File | Lines | Est. CC |
|---|---|---:|---:|
| `showAiChatDialog` | `KeywordBlockerFragment.kt:754` | 202 | 37 |
| `checkIfUserGettingFreaky` | `KeywordBlocker.kt:94` | 195 | 48 |
| `setupListeners` | `KeywordBlockerFragment.kt:174` | 194 | 21 |
| `observeViewModel` | `KeywordBlockerFragment.kt:428` | 99 | 7 |
| `getDominantColor` | `ColorUtils.kt:11` | 71 | 13 |
| `updateIgnoredAppsList` | `KeywordBlockerFragment.kt:1030` | 69 | 7 |
| `requestExemption` | `GeminiManager.kt:81` | 66 | 5 |
| `showDecreaseTimeDialog` | `KeywordBlockerFragment.kt:1100` | 51 | 5 |
| `requestPauseBlocker` | `GeminiManager.kt:148` | 48 | 5 |
| `requestExtension` | `GeminiManager.kt:197` | 46 | 5 |

#### Classes with > 10 Public/Non-Private Methods
- `KeywordBlockerViewModel`: **21** public methods
- `DataStoreManager`: **16** public methods
- `KeywordBlockerFragment`: **10** non-private methods (33 total functions, 1,155 LOC god class)

#### Smells & Static Counters
| Metric | Baseline Count | Notes |
|---|---:|---|
| Global mutable singletons | 1 | `DataStoreManager.INSTANCE` (`@Volatile var`), plus `object GeminiManager`, `object PermissionUtils` |
| Static `Context` references | 0 | (Note: `CrashLogger` stores `Context`, `DataStoreManager` stores `Context` field unused after init) |
| `lateinit var` usages | **7** | `KeywordBlocker` (4), `SelectAppsActivity` (2), `WardenService` (1) |
| `!!` non-null assertions | **8** | `KeywordBlocker.kt` (2), `KeywordBlockerFragment.kt` (6) |
| Swallowed exceptions (empty or `printStackTrace`-only catch) | **5** | `WardenService`, `KeywordBlocker`, `CrashLogger`, `DataStore`, `KeywordBlockerFragment` |
| `TODO` / `FIXME` | 0 | |
| Commented-out code lines | 0 | |
| Dependency direction violations | Multiple | UI (`KeywordBlockerFragment`) directly calls network (`GeminiManager`), OS policy (`DevicePolicyManager`), `PackageManager`, and file I/O (`ContentResolver`); `ViewModel` directly depends on concrete `DataStoreManager` and `blockers.KeywordBlocker` |
| Code duplication (`npx jscpd app/src/main/java`) | **3.32%** | 7 exact clones, 95 duplicated lines |
| Android Lint (`./gradlew lint`) | **0 errors, 610 warnings** | `UnusedResources` (384), `HardcodedText` (55), `SetTextI18n` (48), `GradleDependency` (39), `Typos` (33), `ContentDescription` (16), `UnusedAttribute` (6), `Overdraw` (4), `AndroidGradlePluginVersion` (3), `TypographyEllipsis` (3), `DefaultLocale` (2), `DisableBaselineAlignment` (2), `MonochromeLauncherIcon` (2), `UseTomlInstead` (2), `RtlSymmetry` (2), plus 9 single-occurrence warnings. `abortOnError = false` in Gradle! |
| Kotlin/Javac compiler warnings | **15** | 9 Kotlin warnings (`AppFilter.kt`, `DataStore.kt`), 6 Javac source/target 8 warnings |
| Unit tests (`app/src/test`) | **0 tests** | `:app:testDebugUnitTest NO-SOURCE` (0% coverage; ALL 17 core contract flows untested) |
| Instrumented tests (`app/src/androidTest`) | **1 broken test** | `ExampleInstrumentedTest.kt` still asserts `com.blockwords.app` |
| KDoc coverage on public declarations | **0 / 126 (0.0%)** | Zero KDoc comments in the codebase |
| Docs inventory | Present (outdated) | `README.md` (references `Warden-debug.apk`, "Display Over Other Apps" which is not in manifest), `CONTRIBUTING.md`, `docs/{README,architecture,how-it-works,ai-gatekeeper,anti-uninstall,configuration}.md` (describes substring matching instead of regex, omits whitelist). Missing: `docs/adr/`, `docs/HOW_TO.md`, `docs/DEPENDENCIES.md`, `docs/RELEASING.md`, package docs. |

---

## 3. Release Pipeline Baseline (VERIFIED)

Target: `.github/workflows/release.yml`

### `actionlint` v1.7.12 Output
- **0 errors** (note: `shellcheck` and `pyflakes` not installed in PATH).

### `zizmor` v1.33.1 Output (`uvx zizmor --offline .github/workflows/`)
**8 active findings** (2 high, 2 medium, 3 low, 1 info):
1. `[HIGH] cache-poisoning` (line 38): `gradle/actions/setup-gradle` in `validate` job omits `cache-disabled` / safe caching controls in a release workflow.
2. `[HIGH] cache-poisoning` (line 70): `gradle/actions/setup-gradle` in `release` job omits `cache-disabled` / safe caching controls.
3. `[MEDIUM] artipacked` (line 28): `actions/checkout` in `validate` job missing `persist-credentials: false`.
4. `[MEDIUM] artipacked` (line 58): `actions/checkout` in `release` job missing `persist-credentials: false`.
5. `[LOW] template-injection` (line 108): `${{ env.CLEAN_VERSION }}` interpolated directly inside `run:` instead of shell `$CLEAN_VERSION`.
6. `[LOW] template-injection` (line 109): `${{ env.VERSION_CODE }}` interpolated directly inside `run:` instead of shell `$VERSION_CODE`.
7. `[LOW] template-injection` (line 114): `${{ env.VERSION }}` interpolated directly inside `run:` instead of shell `$VERSION`.
8. `[INFO] superfluous-actions` (line 126): `softprops/action-gh-release` replaceable with `gh release`.

### Pipeline Inventory Summary
- **Secrets used:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (not scoped to a GitHub `environment:`; decoded keystore file is not deleted after build).
- **Actions pinned:** All 5 third-party actions are SHA-pinned (`actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `timheuer/base64-to-file`, `actions/attest-build-provenance`, `softprops/action-gh-release`).
- **Permissions:** Top-level `contents: read`; `release` job adds `contents: write`, `id-token: write`, `attestations: write`.
- **Triggers:** `push.tags: ['v*']`, `workflow_dispatch`.
- **Signing:** `timheuer/base64-to-file` writes `signing-key.jks`, passed via `KEYSTORE_PATH` env var; Gradle falls back to debug signing if `KEYSTORE_PATH` is unset.
- **Published artifacts:** `Warden-${VERSION}.apk`, `SHA256SUMS.txt`, and build provenance attestation. Missing: SBOM, APK verification (`apksigner verify`, size budget, version check), Gradle wrapper validation, Dependabot, composite actions, deterministic `VERSION_CODE`.
