# ADR-0002: Replace `MultiProcessDataStoreFactory` + Gson with Atomic `org.json` `SettingsRepository`

- **Status:** Accepted
- **Date:** 2026-11-26

## Context
`baseline-v1` used `androidx.datastore:datastore` (`MultiProcessDataStoreFactory`) paired with `com.google.code.gson:gson` to read/write `filesDir/datastore/settings.json`. However, Warden declares no `android:process` attribute — UI and `WardenService` execute in a single Linux process. `MultiProcessDataStoreFactory` bundled 4 native `.so` binaries (`libdatastore_shared_counter.so` for 4 ABIs), broke local JVM Robolectric tests with `UnsatisfiedLinkError`, and Gson bypassed Kotlin null-safety (triggering 8 compiler warnings and requiring reflection keep rules under R8).

## Decision
Define a storage-agnostic `SettingsRepository` interface (`core/data/SettingsRepository.kt`) and implement `JsonSettingsRepository` using AndroidX `AtomicFile`, a Kotlin `Mutex`, `MutableStateFlow<Settings>`, and platform `org.json` (`SettingsJsonCodec`) reading and writing the exact same `filesDir/datastore/settings.json` path and JSON field names.

## Consequences
- **Positive:** Eliminated all 4 native `.so` libraries (0 native libs in APK), eliminated Gson and reflection keep rules, preserved 100% upgrade compatibility with `baseline-v1` disk/export JSON, enabled fast JVM unit tests, and kept settings reactive across UI and `WardenService` without a `BroadcastReceiver`.
- **Negative:** If a future feature ever runs in a separate `android:process`, an IPC or multi-process storage implementation must be swapped behind `SettingsRepository`.
