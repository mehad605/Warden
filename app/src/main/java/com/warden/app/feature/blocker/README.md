# `feature/blocker` Package

Core keyword-blocking and self-protection feature, organized strictly by layer:

- `domain/` — Pure Kotlin (zero Android imports): `KeywordMatcher` (compiled word-boundary regex + whitelist masking), `SelfProtectionEvaluator` (App Info & Device Admin guard), `BlockingDecisionEngine`, `KeywordNormalizer`, `IgnoredAppsFormatter`.
- `service/` — Accessibility runtime wiring: `AccessibilityNodeScanner` (leak-safe DFS with node recycling), `GracePeriodController` (cancellable main-thread delay), and `BlockerCoordinator`.
- `ui/` — Dashboard UI & state: `BlockerViewModel`, self-reducing `BlockerAction`, `KeywordBlockerFragment`, `DashboardControllers`, and focused section binders (`KeywordsSectionBinder`, `GeminiSectionBinder`, `SecurityAndBackupSectionBinder`, `IgnoredAppsListBinder`).
