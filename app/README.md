# `:app` Module

The single Android application module for Warden (`com.warden.app`, `minSdk = 26`, `targetSdk = 34`).

- **Composition Root:** `Warden.kt` + `core/di/AppContainer.kt` (lazy manual DI, zero reflection).
- **Build & Checks:** `./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease`.
- **Release Optimization:** R8 full mode + resource shrinking + English-only resources + static baseline profile (`src/main/baseline-prof.txt`).
- See [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full package map and layering rules.
