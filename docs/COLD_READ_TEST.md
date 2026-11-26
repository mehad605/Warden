# Cold-Read Onboarding Test (S4.9)

**Status:** VERIFIED PASS (**10 / 10**)  
**Date:** 2026-11-26  
**Reviewer:** Independent read-only `research` subagent (`89a34435-9aac-491d-aaff-39cc01fb97c0`) given **zero prior conversation context** and instructed to answer 10 concrete onboarding questions using only the repository's documentation and source files.

---

## Graded Q&A

| # | Topic | Reviewer Summary (Verified Against Code) | Grade |
|---|---|---|---|
| 1 | Architecture & Layering | Correctly identified single `:app` module, Android entry points, `AppContainer`/`AppContainerProvider`, 4 feature packages (`blocker`, `gatekeeper`, `security`, `backup`), inward dependency direction, and `ArchitectureRulesTest.kt` enforcement. | PASS (1/1) |
| 2 | Keyword Matching & Whitelist Masking | Correctly traced `WardenService` (`Channel.CONFLATED` on `Dispatchers.Default`) -> `BlockerCoordinator` -> `AccessibilityNodeScanner` -> `KeywordMatcher` (equal-length space whitelist masking + single combined alternation regex `(?<!\w)(?:...)+(?!\w)` + ±7-word context) and exact `finally` recycling of child/root nodes and events. | PASS (1/1) |
| 3 | Self-Protection & Activation Window | Correctly identified `SelfProtectionEvaluator` safe-package bypass, all 3 guarded screens (Device Admin deactivation, Accessibility revocation, App Info Force Stop/Kill/Clear Data/Uninstall), and the 180,000 ms grace window recorded by `BlockerAction.RecordAdminActivationRequest`. | PASS (1/1) |
| 4 | AI Gatekeeper Transport & Validation | Correctly identified `HttpGeminiGatekeeperClient` (`HttpURLConnection` + `org.json`, 15 s timeouts), `JustificationValidator` (≥7 sentences, ≥5 words/sentence, ≤50% duplicate words, bypassed for removals), and all 4 `GatekeeperRequest` sealed subclasses. | PASS (1/1) |
| 5 | Settings Persistence & Upgrade Safety | Correctly identified `filesDir/datastore/settings.json`, `JsonSettingsRepository` (`AtomicFile` + `Mutex` + `MutableStateFlow` + `SettingsJsonCodec`, 0 `.so` libs), and all 3 immutable manifest FQCNs (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver`). | PASS (1/1) |
| 6 | How to Add a Setting | Correctly listed all 5 steps from `docs/HOW_TO.md` Recipe 1 (`Settings.kt`, `SettingsJsonCodec.kt`, `BlockerAction.kt`, UI/strings, tests). | PASS (1/1) |
| 7 | How to Replace the AI Layer | Correctly identified `GatekeeperClient` and the exact 2 files (`feature/gatekeeper/data/<Impl>.kt` + `AppContainer.kt`) with zero UI/ViewModel edits. | PASS (1/1) |
| 8 | Startup & Idle Footprint | Correctly identified `Warden.onCreate()` (debug `StrictMode` + lazy `CrashLogger`), the 2 removed Startup initializers (`EmojiCompatInitializer`, `ProcessLifecycleInitializer`) leaving only `ProfileInstallerInitializer`, and single-process `StateFlow<Settings>` sync with no `BroadcastReceiver`. | PASS (1/1) |
| 9 | Why Manual DI | Correctly cited `docs/adr/0004-manual-lazy-di-container.md` (zero KSP/reflection overhead, lazy startup, testability) and `AppContainerProvider` breaking the root-package cycle. | PASS (1/1) |
| 10 | Quality Gates | Correctly identified `./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease`. | PASS (1/1) |

**Total Score:** **10 / 10**

---

## Documentation Discrepancies Caught & Fixed From Cold-Read Feedback

The independent reviewer also audited docs against code and flagged subtle mismatches, **all of which were fixed immediately**:
1. Fixed ADR link in `docs/ai-gatekeeper.md` (`0002-...` -> `adr/0003-platform-http-gemini-gatekeeper.md`).
2. Aligned symbol names in `docs/ai-gatekeeper.md`, `docs/anti-uninstall.md`, and `feature/gatekeeper/README.md` (`GatekeeperRequest` / `GatekeeperDialogMode`, `BlockerAction.IncrementRejectionCount`, `BlockerAction.RecordAdminActivationRequest`).
3. Aligned regex description (`(?<!\w)(?:...)+(?!\w)`), `isActive` default (`false`), `selectedGeminiModel` default (`""` -> fallback `"models/gemini-1.5-flash"`), export anti-uninstall clearing, and `PAUSE_BLOCKER` behavior in `docs/how-it-works.md`, `docs/configuration.md`, and `docs/ai-gatekeeper.md`.
4. Added Accessibility revocation guard to `docs/how-it-works.md` and `docs/anti-uninstall.md`.
5. Fixed `targetSdk = 34` in `app/README.md`.
6. Added `#emergency-uninstall` section to `README.md`.
7. Remaining forward links (`docs/RELEASING.md`, `docs/GITHUB_REPO_SETTINGS.md`, `docs/RESULTS.md`) are completed in Iteration 10 and the final report commit.
