# `services` Package

Android `Service` entry points registered in `AndroidManifest.xml`:

- `WardenService.kt` — Thin `AccessibilityService` adapter that binds `AppContainer` to `BlockerCoordinator` on `onServiceConnected()`, forwards accessibility events, and cancels its scope + grace timer cleanly in `onDestroy()`.

**Upgrade Invariant:** Do not rename or move `com.warden.app.services.WardenService`; Android keys the user's Accessibility Service permission to this exact FQCN.
