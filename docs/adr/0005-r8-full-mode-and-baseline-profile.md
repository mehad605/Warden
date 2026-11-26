# ADR-0005: R8 Full Mode, Resource Shrinking, WebP Icons, and ART Baseline Profile

- **Status:** Accepted
- **Date:** 2026-11-26

## Context
`baseline-v1` shipped with `isMinifyEnabled = false`, no `isShrinkResources`, no `resourceConfigurations`, 649 KB of uncompressed launcher PNGs across 5 densities, and no ART baseline profile — producing a **7.51 MB** APK with **95,072** DEX method references across 2 DEX files. On the target Realme Narzo 20 (Helio G85, 4 GB RAM, Android 11), smaller DEX and AOT-compiled startup paths directly reduce memory pressure and cold-start latency.

## Decision
1. Enable `android.enableR8.fullMode=true`, `isMinifyEnabled = true`, and `isShrinkResources = true` for release builds with minimal, verified keep rules (`app/proguard-rules.pro` strips `Log.*` and `Throwable.printStackTrace()` and needs zero model `-keep` rules because JSON is reflection-free).
2. Restrict `resourceConfigurations += listOf("en")` to strip unused library translations.
3. Convert launcher PNGs to quality-90 WebP (`649 KB -> 27 KB`).
4. Ship `app/src/main/baseline-prof.txt` covering `Warden`, `FragmentActivity`, `WardenService`, `BlockerCoordinator`, and `JsonSettingsRepository`, while removing unused `EmojiCompatInitializer` and `ProcessLifecycleInitializer` from `InitializationProvider`.

## Consequences
- **Positive:** Release APK dropped from **7.51 MB → 1.59 MB (-78.8%)**, download size **6.36 MB → 1.12 MB (-82.4%)**, DEX references **95,072 → 12,644 (-86.7%)**, and ART pre-compiles startup + accessibility hot paths on install.
- **Negative:** Adding a new language later requires adding its locale code to `resourceConfigurations` in `app/build.gradle.kts`.
