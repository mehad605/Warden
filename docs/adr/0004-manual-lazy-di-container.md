# ADR-0004: Lightweight Lazy Manual DI (`AppContainer`) Over Dagger/Hilt/Koin

- **Status:** Accepted
- **Date:** 2026-11-26

## Context
`baseline-v1` instantiated `DataStoreManager` via a global mutable `@Volatile var INSTANCE` singleton, called `object GeminiManager` directly from UI fragments, and lacked constructor injection, making components untestable and tightly coupled. We needed clean dependency injection without inflating APK size, build times, or startup RAM on a 4 GB RAM device.

## Decision
Adopt an explicit, lazy manual DI container (`core/di/AppContainer.kt`) exposed via `AppContainerProvider` on `Warden` (`Application`), passing dependencies via constructors and `ViewModelProvider.Factory`. Avoid Dagger/Hilt (annotation processing/KSP overhead) and Koin (runtime reflection/service-locator overhead).

## Consequences
- **Positive:** Zero extra libraries, zero reflection, zero startup overhead (all singletons are `by lazy`), zero circular package dependencies (`AppContainerProvider` decouples features from `Warden`), and 100% constructor-injectable testability.
- **Negative:** New app-wide dependencies must be wired manually in `AppContainer.kt` (1–4 lines per dependency).
