# Warden Dependencies Justification

Every runtime dependency in `app/build.gradle.kts` must earn its place in the APK.
All transitive libraries are minimized by **R8 full mode** (`android.enableR8.fullMode=true`,
`isMinifyEnabled = true`, `isShrinkResources = true`).

## Direct Runtime Dependencies (`implementation`)

| Dependency | Version | Why It Exists | Why Platform API Cannot Replace It | Minified Cost (Post-R8) |
|---|---|---|---|---|
| `androidx.core:core-ktx` | `1.13.1` | `AtomicFile` (crash-safe settings writes), `WindowInsetsCompat`, `ContextCompat`, `TextViewCompat`, and Kotlin extensions | Platform `AtomicFile` / insets APIs vary across API 26–34 and lack edge-to-edge compat helpers | Shared base required by AppCompat & Material |
| `androidx.appcompat:appcompat` | `1.7.0` | `AppCompatActivity`, DayNight theme switching, Material 3 base theme compatibility across API 26–34 | Platform `Activity` lacks `Theme.Material3.DayNight`, consistent AlertDialog/Toolbar theming, and API 26–30 backports | Included in core AndroidX UI footprint |
| `com.google.android.material:material` | `1.12.0` | Material 3 UI components used in the settings dashboard (`MaterialCardView`, `MaterialSwitch`, `ChipGroup`/`Chip`, `Slider`, `TextInputLayout`, `MaterialAlertDialogBuilder`, `MaterialToolbar`, `MaterialButton`, `CircularProgressIndicator`) | Android framework widgets lack Material 3 components, chip clouds, range sliders, and tonal/outlined buttons | Primary UI toolkit cost (heavily tree-shaken by R8) |
| `androidx.activity:activity` | `1.9.2` | `enableEdgeToEdge()` and Activity Result Contracts (`CreateDocument`, `OpenDocument`, `GetContent`) | Platform `startActivityForResult` is deprecated and lacks lifecycle-safe result contracts and edge-to-edge compat on API 26–34 | Tiny (~few KB post-R8; also transitive from AppCompat/Fragment) |
| `androidx.fragment:fragment-ktx` | `1.8.9` | `Fragment`, `FragmentContainerView`, and `by activityViewModels()` delegate for configuration-change-safe UI state | Platform `android.app.Fragment` was deprecated in API 28 and lacks `LifecycleOwner`/`ViewModelStoreOwner` integration | Small (~tens of KB post-R8) |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | `2.10.0` | `ViewModel` and `viewModelScope` coroutine lifecycle management surviving screen rotations | No platform equivalent for configuration-change-retaining state holders with structured coroutine cancellation | Tiny (~few KB post-R8) |

## Removed Dependencies (vs. `baseline-v1`)

| Removed Dependency | Reason Removed | Savings Impact |
|---|---|---|
| `androidx.room:room-runtime`, `room-ktx`, `ksp(room-compiler)` | 0 usages in codebase; also injected `MultiInstanceInvalidationService` | Removed unused service & DEX/build overhead |
| `com.journeyapps:zxing-android-embedded` | 0 usages; injected `CAMERA` permission, 6 hardware features, `CaptureActivity`, and `res/E5.ogg` | Removed unwanted permissions & ~1,907 DEX methods |
| `dev.rikka.shizuku:api`, `provider` | 0 usages; injected `moe.shizuku.manager.permission.API_V23` | Removed unwanted permission & metadata |
| `com.github.PhilJay:MPAndroidChart` | 0 usages | Removed ~2,087 DEX methods |
| `nl.joery.timerangepicker:timerangepicker` | 0 usages | Removed unused UI library & resources |
| `androidx.constraintlayout:constraintlayout` | 0 usages across all layouts | Removed ~3,826 DEX methods |
| `androidx.datastore:datastore` (`MultiProcessDataStoreFactory`) | Single-process app; replaced by `AtomicFile` + `org.json` on identical `datastore/settings.json` path | Eliminated all 4 native `libdatastore_shared_counter.so` binaries (0 native `.so` libs) |
| `com.google.code.gson:gson` | Replaced by platform `org.json` (`SettingsJsonCodec`) | Zero reflection, zero R8 keep rules |
| `com.google.ai.client.generativeai:generativeai` | Replaced by platform `HttpURLConnection` + `org.json` (`HttpGeminiGatekeeperClient`) | Removed Ktor + OkHttp + `publicsuffixes.gz` + `kotlinx.serialization` (~16,500 DEX methods, -1.46 MB pre-R8) |

## Test-Only Dependencies (Zero APK Impact)

- `junit:junit:4.13.2` — JVM unit testing framework
- `org.robolectric:robolectric:4.13` — Local API 30 Android framework simulation for characterization & UI parity tests
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1` — Deterministic coroutine scheduler testing
- `androidx.test:core:1.6.1`, `androidx.test.ext:junit:1.2.1`, `androidx.test.espresso:espresso-core:3.6.1` — AndroidX test context & instrumented smoke tests
