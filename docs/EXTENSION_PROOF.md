# Extension Proof (S5.4)

**Status:** VERIFIED PASS  
**Date:** 2026-11-26  
**Recipe Tested:** [`docs/HOW_TO.md`](HOW_TO.md#recipe-2-add-a-self-contained-feature) (Recipe 2: Add a Self-Contained Feature)

---

## Procedure Executed Verbatim

Following `docs/HOW_TO.md` Recipe 2 without any undocumented edits:

1. **Created new feature package** (`app/src/main/java/com/warden/app/feature/cooldown/`):
   - `domain/CooldownPolicy.kt` — pure Kotlin interface (`isReady(lastTriggerMs, windowMs)`), zero `android.*` imports, KDoc on all public declarations.
   - `data/DefaultCooldownPolicy.kt` — concrete implementation injecting `Clock` (`core/platform/Clock.kt`).
   - `README.md` — package overview.
2. **Wired one lazy binding in composition root** (`app/src/main/java/com/warden/app/core/di/AppContainer.kt`):
   - `val cooldownPolicy: CooldownPolicy by lazy { DefaultCooldownPolicy(clock) }`
   - **Existing production files touched:** **1** (`AppContainer.kt`, within the ≤4 touch-point budget).
3. **Added unit test** (`app/src/test/java/com/warden/app/CooldownPolicyExtensionProofTest.kt`) exercising `CooldownPolicy` with a fake `Clock`.
4. **Verified full quality gate:**
   ```bash
   ./gradlew ktlintCheck detekt lint testDebugUnitTest
   ```
   - **Result:** `BUILD SUCCESSFUL in 38s`
   - `ktlintCheck`: **0 issues**
   - `detekt`: **0 issues**
   - `lint` (`warningsAsErrors = true`): **0 errors, 0 warnings**
   - `testDebugUnitTest`: **PASS** (including `ArchitectureRulesTest` verifying domain purity, zero cycles, file size ≤300 LOC, zero `!!`/`lateinit`, and 100% public KDoc coverage, plus `CooldownPolicyExtensionProofTest`).
5. **Doc refinement discovered during proof:** Clarified in `docs/HOW_TO.md` Recipe 2 that `Clock` exposes `currentTimeMillis()` / `uptimeMillis()`.
6. **Cleaned up temporary proof files:** Deleted `feature/cooldown/`, `CooldownPolicyExtensionProofTest.kt`, and reverted the temporary `AppContainer.kt` binding so zero dummy code remains in production.

---

## Companion Replaceability Proof (S5.3)

See [`app/src/test/java/com/warden/app/BlockerViewModelAndReplaceabilityTest.kt`](../app/src/test/java/com/warden/app/BlockerViewModelAndReplaceabilityTest.kt), which continuously verifies in CI that two core components (`SettingsRepository` and `GatekeeperClient`) can be replaced with completely independent implementations without modifying `BlockerViewModel`, `BlockerCoordinator`, or any UI file.
