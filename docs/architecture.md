# Architecture

This document provides a component-level overview of the Warden codebase.

---

## Table of Contents

- [Module Structure](#module-structure)
- [Component Map](#component-map)
- [Data Flow](#data-flow)
- [Package Layout](#package-layout)
- [Key Design Decisions](#key-design-decisions)

---

## Module Structure

Warden is a single-module Android app. There is no multi-module Gradle setup — the entire application lives in `:app`.

```
Warden/
├── app/
│   ├── src/main/
│   │   ├── java/com/warden/app/      ← All Kotlin source
│   │   ├── res/                       ← Layouts, drawables, strings, XML configs
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/                              ← Documentation (this folder)
├── .github/workflows/release.yml     ← CI/CD
├── build.gradle.kts                  ← Root build file
└── README.md
```

---

## Component Map

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer                                  │
│                                                                  │
│  FragmentActivity ──► KeywordBlockerFragment                     │
│                              │                                   │
│                       KeywordBlockerViewModel                    │
│                              │                                   │
│                         DataStoreManager ◄──────────────────┐   │
└──────────────────────────────┼──────────────────────────────┘   │
                               │ Flow<Settings>                    │
┌──────────────────────────────▼──────────────────────────────┐   │
│                     Service Layer                            │   │
│                                                              │   │
│  WardenService (AccessibilityService)                        │   │
│       │                                                      │   │
│       └──► KeywordBlocker                                    │   │
│                 │                                            │   │
│                 ├── checkIfUserGettingFreaky()  ← Self-guard │   │
│                 ├── scanNodeForBlockedKeyword() ← Keyword scan│  │
│                 └── pressHome() via BaseBlockingService       │   │
└──────────────────────────────────────────────────────────────┘   │
                                                                    │
┌──────────────────────────────────────────────────────────────┐   │
│                    Data Layer                                │   │
│                                                              │   │
│  DataStoreManager ──► Jetpack DataStore (Settings)           │───┘
│         │                                                    │
│         └──► Settings (data class, single source of truth)  │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    AI Layer                                  │
│                                                              │
│  GeminiManager                                               │
│       ├── requestExemption()   → app whitelist               │
│       ├── requestPauseBlocker() → full blocker pause         │
│       └── requestExtension()  → extend active exemption      │
│                                                              │
│  All calls → generativelanguage.googleapis.com (HTTPS)       │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                Security Layer                                │
│                                                              │
│  WardenDeviceAdminReceiver ← DeviceAdminReceiver             │
│  device_admin_rules.xml   ← Policy declaration (empty)      │
│                                                              │
│  Self-protection logic lives inside KeywordBlocker           │
│  (same accessibility event stream, no separate component)    │
└──────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### Settings Update → Service Reacts

```
User changes setting in UI
          │
          ▼
KeywordBlockerViewModel.save*()
          │
          ▼
DataStoreManager.updateSettings()
          │
          ▼
Jetpack DataStore (on-disk)
          │
          ▼
DataStoreManager.settings Flow emits new value
          │
          ├──► KeywordBlockerFragment observes → updates UI
          │
          └──► KeywordBlocker.setupBlocker() via collectLatest
                        │
                        └──► In-memory blockedKeyword, ignoredApps, etc. updated
```

### Accessibility Event → Block Decision

```
Android system fires AccessibilityEvent
          │
          ▼
WardenService.onAccessibilityEvent()
          │
          ▼
KeywordBlocker.checkIfUserGettingFreaky()
          │
          ├──► [Self-guard check] → pressHome() if targeting Warden
          │
          ├──► [Blocker disabled?] → return early
          │
          ├──► [App in ignore list?] → return early
          │
          ▼
scanNodeForBlockedKeyword(rootInActiveWindow)
          │
          ├──► null → no action
          │
          └──► keyword found
                    │
                    ├── grace > 0 → postDelayed → re-check → pressHome()
                    │
                    └── grace = 0 → pressHome() immediately
```

### AI Exemption Request

```
User opens AI chat dialog
          │
          ▼
GeminiManager.validateUserText() → local validation
          │
          ▼ (if valid)
GeminiManager.request*() → Gemini API (HTTPS, multimodal optional)
          │
          ▼
GeminiResult parsed from JSON response
          │
          ├── approved = false → noCount++ → show rejection message
          │
          └── approved = true
                    │
                    ├── exemption → temporaryIgnoredApps[pkg] = expiry
                    ├── pause    → blockerDisabledUntil = expiry
                    └── extension → extend existing expiry
                              │
                              ▼
                    DataStoreManager.updateSettings() → DataStore
```

---

## Package Layout

```
com.warden.app
├── Warden.kt                    ← Application subclass (CrashLogger init)
├── Constants.kt                 ← App-wide constants
├── CrashLogger.kt               ← Uncaught exception handler → on-device log file
│
├── blockers/
│   ├── BaseBlocker.kt           ← Abstract base (future blocker types)
│   └── KeywordBlocker.kt        ← Core blocking engine
│
├── data/
│   └── models/
│       ├── Settings.kt          ← Single source of truth for all config
│       ├── KeywordBlocker.kt    ← Keyword config data class
│       └── FocusBlockMode.kt    ← Enum: how focus blocking behaves
│
├── receivers/
│   └── WardenDeviceAdminReceiver.kt ← Device Admin lifecycle callbacks
│
├── services/
│   ├── BaseBlockingService.kt   ← AccessibilityService base (pressHome, DataStore)
│   └── WardenService.kt         ← Wires blockers to accessibility events
│
├── ui/
│   ├── activity/
│   │   ├── FragmentActivity.kt  ← Single activity host
│   │   └── SelectAppsActivity.kt ← App picker for ignore lists
│   └── fragments/main/reducers/blockertools/keywordBlocker/
│       ├── KeywordBlockerFragment.kt  ← Settings UI + AI chat dialogs
│       └── KeywordBlockerViewModel.kt ← Settings state + AI request orchestration
│
└── utils/
    ├── AppFilter.kt             ← App list filtering logic
    ├── ColorUtils.kt            ← Palette helpers for UI
    ├── DataStore.kt             ← DataStoreManager + GsonSerializer
    ├── GeminiManager.kt         ← All Gemini API calls + system prompts
    ├── PackageFinderTools.kt    ← Installed app discovery helpers
    ├── PermissionUtils.kt       ← Permission check/request helpers
    └── TimeTools.kt             ← Duration formatting utilities
```

---

## Key Design Decisions

### Why Accessibility Service (not VPN / UsageStatsManager)?

| Approach | Pros | Cons |
|---|---|---|
| **Accessibility Service** (Warden) | Can read exact on-screen text; works for content within apps, not just app launches | Requires explicit user consent; can be revoked from Settings |
| VPN-based DNS blocking | Can block domains network-wide | Cannot see content within apps; doesn't work for keyword-level blocking |
| UsageStatsManager | Can detect app opens | Polling-based, delayed; cannot read content inside apps |

Keyword blocking inside app content (e.g., a news section inside a browser, or a feed in a social app) is only possible with an Accessibility Service.

### Why `performGlobalAction(GLOBAL_ACTION_HOME)` (not BACK)?

The HOME action drops the user to the launcher, making it significantly harder to re-enter the blocked content in a single tap. `GLOBAL_ACTION_BACK` only moves one screen back within the same app, which is insufficient when the user is already on the distracting page.

### Why Jetpack DataStore (not SharedPreferences)?

DataStore provides type-safe, coroutine-native access with structured data and proper Flow-based reactive updates. This is critical because the accessibility service and the UI both need to observe settings changes in real time without polling.

### Why Local Validation Before the AI Call?

The `GeminiManager.validateUserText()` check runs entirely on-device before any network call. This:
- Prevents abuse (spamming the API with one-word "justifications")
- Saves API quota
- Provides instant user feedback without a network round-trip
