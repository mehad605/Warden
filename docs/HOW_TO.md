# Warden Developer How-To Recipes

Tested, step-by-step recipes for extending or replacing parts of Warden without breaking layering, tests, or upgrade compatibility.

---

## Touch-Point Summary (Adding a Feature)

Adding a self-contained feature requires touching **at most 4 documented places**:
1. **New feature package:** `app/src/main/java/com/warden/app/feature/<name>/{domain,data,ui}/` (public interface + hidden internals).
2. **Data model & codec (only if persisted):** `data/models/Settings.kt` + `core/data/SettingsJsonCodec.kt`.
3. **Composition root:** `core/di/AppContainer.kt` (lazy `val` wiring the implementation behind its domain interface).
4. **UI wiring (if user-facing):** `feature/blocker/ui/BlockerAction.kt` + `DashboardControllers.kt` / `KeywordBlockerFragment.kt` (or host activity for a new screen).

---

## Recipe 1: Add a New Setting

Example: adding a boolean setting `strictExactMatch: Boolean = false`.

1. **Add field with default to `Settings`** in `app/src/main/java/com/warden/app/data/models/Settings.kt`:
   ```kotlin
   val strictExactMatch: Boolean = false,
   ```
   *(Always give new fields a default value so existing `baseline-v1` `settings.json` files decode cleanly.)*
2. **Add JSON key & encode/decode logic** in `app/src/main/java/com/warden/app/core/data/SettingsJsonCodec.kt`:
   - Define `private const val KEY_STRICT_EXACT_MATCH = "strictExactMatch"`.
   - Write `put(KEY_STRICT_EXACT_MATCH, settings.strictExactMatch)` in `encode(...)`.
   - Read `strictExactMatch = root.optBoolean(KEY_STRICT_EXACT_MATCH, false)` in `decodeOrNull(...)`.
3. **Add a self-reducing `BlockerAction`** in `app/src/main/java/com/warden/app/feature/blocker/ui/BlockerAction.kt`:
   ```kotlin
   /** Toggles strict exact-match mode. */
   data class SetStrictExactMatch(val enabled: Boolean) : BlockerAction() {
       override fun applyTo(current: Settings, nowMs: Long): Settings =
           current.copy(strictExactMatch = enabled)
   }
   ```
4. **Wire UI (if visible):** Add strings to `app/src/main/res/values/strings.xml`, add the control to `fragment_keyword_blocker.xml`, bind its listener to `viewModel.dispatch(BlockerAction.SetStrictExactMatch(checked))`, and render it in `renderSettings(settings)`.
5. **Add unit tests** in `SettingsPersistenceCharacterizationTest.kt` (JSON round-trip + missing-field default) and `BlockerViewModelAndReplaceabilityTest.kt` (action dispatch), then run:
   ```bash
   ./gradlew ktlintCheck detekt lint testDebugUnitTest
   ```

---

## Recipe 2: Add a Self-Contained Feature

Example: adding a `feature/cooldown` feature that computes a cooldown window.

1. **Create package & pure domain boundary:**
   - `app/src/main/java/com/warden/app/feature/cooldown/domain/CooldownPolicy.kt` — public `interface` (and pure Kotlin models/logic; **no** `android.*` imports).
   - `app/src/main/java/com/warden/app/feature/cooldown/data/DefaultCooldownPolicy.kt` — concrete implementation (inject `Clock` from `core/platform/Clock.kt` using `currentTimeMillis()` / `uptimeMillis()`).
   - `app/src/main/java/com/warden/app/feature/cooldown/README.md` — 3–5 line package overview.
2. **Wire in `AppContainer`** (`app/src/main/java/com/warden/app/core/di/AppContainer.kt`):
   ```kotlin
   /** Evaluates feature cooldown intervals. */
   val cooldownPolicy: CooldownPolicy by lazy { DefaultCooldownPolicy(clock) }
   ```
3. **Consume ONLY the domain interface** in the caller (e.g., pass `container.cooldownPolicy` into a ViewModel factory or service coordinator constructor). Never import `feature/cooldown/data` from another feature!
4. **Add unit tests** in `app/src/test/java/com/warden/app/` testing the pure domain contract and verify:
   ```bash
   ./gradlew ktlintFormat && ./gradlew ktlintCheck detekt lint testDebugUnitTest
   ```
   `ArchitectureRulesTest` automatically verifies zero layering violations, zero cycles, file size ≤ 300 lines, and zero `!!`/`lateinit`.

---

## Recipe 3: Add a New Screen

1. **Create a layout XML** in `app/src/main/res/layout/fragment_<name>.xml` using Material 3 theme attributes (`?attr/colorSurfaceContainer`, etc.), string resources in `res/values/strings.xml`, and `contentDescription` on all `ImageView`s.
2. **Create a Fragment** in `app/src/main/java/com/warden/app/feature/<feature>/ui/<Name>Fragment.kt` using ViewBinding (`_binding` nulled in `onDestroyView()`), obtaining `AppContainer` via `(requireContext().applicationContext as AppContainerProvider).container`.
3. **Navigate from `FragmentActivity` or `KeywordBlockerFragment`** using `parentFragmentManager.beginTransaction().replace(R.id.fragment_holder, MyFeatureFragment()).addToBackStack(null).commit()`.
4. **Do NOT add a new launcher Activity** or rename `FragmentActivity` (preserving launcher shortcuts). If a secondary `Activity` is strictly required, declare it with `android:exported="false"` in `AndroidManifest.xml`.
5. **Add Robolectric UI tests** verifying creation and key interactions on `@Config(sdk = [30])`.

---

## Recipe 4: Replace the Network / AI Layer

Because `feature/gatekeeper` hides HTTP/Gemini behind `GatekeeperClient`, replacing the AI backend touches **only 2 files**:

1. **Implement `GatekeeperClient`** (`app/src/main/java/com/warden/app/feature/gatekeeper/domain/GatekeeperClient.kt`) in a new class under `feature/gatekeeper/data/` (e.g., `OpenAiGatekeeperClient.kt` or `LocalRuleGatekeeperClient.kt`):
   ```kotlin
   class CustomGatekeeperClient : GatekeeperClient {
       override suspend fun fetchModels(apiKey: String): List<String> = ...
       override suspend fun evaluate(
           apiKey: String,
           modelName: String,
           request: GatekeeperRequest,
           image: EncodedImage?,
       ): GatekeeperVerdict = ...
   }
   ```
2. **Swap the single binding** in `app/src/main/java/com/warden/app/core/di/AppContainer.kt`:
   ```kotlin
   val gatekeeperClient: GatekeeperClient by lazy { CustomGatekeeperClient() }
   ```
3. Run `./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease`. Zero UI, ViewModel, or blocker files change! (See `BlockerViewModelAndReplaceabilityTest.kt` for a live proof.)

---

## Recipe 5: Add a Dependency

1. **First verify a platform API cannot do the job** (e.g., `org.json`, `HttpURLConnection`, `AtomicFile`, `LruCache`, `MessageDigest`). Adding a dependency that increases APK size or runtime RAM without clear net complexity reduction is rejected.
2. **Pin the exact version** in `gradle/libs.versions.toml` under `[versions]` and `[libraries]`. Never use dynamic `+` versions or hardcode coordinates in `app/build.gradle.kts`.
3. **Declare in `app/build.gradle.kts`** (`implementation(libs.my.lib)` or `testImplementation(...)`).
4. **Document the justification & post-R8 size cost** in `docs/DEPENDENCIES.md`.
5. **Measure impact before committing:**
   ```bash
   ./gradlew lint testDebugUnitTest assembleRelease
   apkanalyzer apk file-size app/build/outputs/apk/release/Warden-release.apk
   apkanalyzer manifest print app/build/outputs/apk/release/Warden-release.apk
   ```
   Verify no unwanted permissions, `ContentProvider` initializers, or native `.so` libraries were merged into the release APK.
