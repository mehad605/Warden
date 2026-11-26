# `data` Package

Pure Kotlin value models persisted to `filesDir/datastore/settings.json`:

- `data/models/Settings.kt` — Root persisted state snapshot (keywords, grace period, password hash, Gemini settings, anti-uninstall state, temporary exemptions).
- `data/models/KeywordBlocker.kt` — Keyword blocker configuration (`blockedKeywords`, `whitelistedKeywords`, `ignoredApps`, `isActive`).

All fields must keep backward-compatible defaults so existing `baseline-v1` user files decode cleanly on upgrade.
