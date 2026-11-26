# ADR-0001: Incremental Strangler Overhaul on `overhaul/v2`

- **Status:** Accepted
- **Date:** 2026-11-26

## Context
Warden `baseline-v1` had 22 Kotlin files (2,901 LOC), 1,155 lines inside a single `KeywordBlockerFragment`, 9 unused Gradle dependencies, 610 Android Lint warnings, and 0 unit tests. We needed to decide between a full from-scratch rewrite or an incremental strangler refactor.

## Decision
Use an **incremental strangler refactor** on a long-lived branch `overhaul/v2` while keeping `main` untouched at tag `baseline-v1`. Build new layered abstractions (`core/data`, `core/di`, `core/platform`, `feature/*`) alongside existing entry points, verify parity at every step with a Robolectric/JVM characterization suite, and delete legacy code only once completely replaced. Preserve manifest component FQCNs (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver`) and disk paths (`filesDir/datastore/settings.json`, `app_prefs`).

## Consequences
- **Positive:** Every commit compiles and passes characterization tests; upgrades from `baseline-v1` preserve Accessibility Service grants, Device Admin status, launcher shortcuts, and user settings without migration risk.
- **Negative:** Requires keeping the 3 manifest entry-point FQCNs fixed (`com.warden.app.ui.activity.FragmentActivity`, `com.warden.app.services.WardenService`, `com.warden.app.receivers.WardenDeviceAdminReceiver`) rather than relocating them into `feature/*`.
