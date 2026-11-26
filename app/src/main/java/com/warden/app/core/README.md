# `core` Package

Shared infrastructure used across features without introducing feature-to-feature coupling:

- `core/data/` — `SettingsRepository` interface, `JsonSettingsRepository` (`AtomicFile` + `org.json` on `filesDir/datastore/settings.json`), and `SettingsJsonCodec`.
- `core/di/` — `AppContainer` (lazy manual DI composition root) and `AppContainerProvider` (breaks package cycles with the root `Warden` `Application` class).
- `core/platform/` — Thin Android/system wrappers: `Clock` / `SystemClockProvider`, `CrashLogger` (lazy, 128 KiB rotating), `AccessibilityPermissionChecker`, `DeviceAdminController`, and `InstalledAppsProvider` (bounded `LruCache(64)`).
