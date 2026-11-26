# How Warden Works

This document explains the internals of Warden's blocking engine — from the moment an accessibility event fires to the moment the device is sent home.

---

## Overview

Warden runs as an Android **Accessibility Service** (`WardenService`), receiving `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` callbacks for the active window.

```
Android fires AccessibilityEvent
       │
       ▼
WardenService.onAccessibilityEvent()          (services/WardenService.kt)
       │
       ▼
BlockerCoordinator.onAccessibilityEvent()     (feature/blocker/service/BlockerCoordinator.kt)
       │
       ├──► SelfProtectionEvaluator           (feature/blocker/domain/SelfProtectionEvaluator.kt)
       │      ├── Warden App Info (Force Stop / Uninstall / Clear Data) ──► pressHome()
       │      └── Device Admin Deactivation (outside 180 s window)      ──► pressHome()
       │
       └──► BlockingDecisionEngine            (feature/blocker/domain/BlockingDecisionEngine.kt)
              ├── Disabled / paused / ignored app / self ──► cancel grace timer, skip
              └── AccessibilityNodeScanner               (feature/blocker/service/)
                     └── KeywordMatcher (regex + whitelist masking)
                            ├── No match        ──► cancel grace timer
                            ├── Grace == 0 s    ──► pressHome() immediately
                            └── Grace > 0 s     ──► schedule GracePeriodController, re-scan, then pressHome()
```

---

## Accessibility Service Pipeline

### Registration & Lifecycle

`WardenService` (`com.warden.app.services.WardenService`) is registered in `AndroidManifest.xml` with `res/xml/blockwords_service_config.xml` (`canRetrieveWindowContent="true"`). Its class FQCN is kept stable across upgrades so Android never revokes the user's granted accessibility permission.

On `onServiceConnected()`, `WardenService` creates a `BlockerCoordinator` wired from `AppContainer` (`SettingsRepository`, `Clock`, and `performGlobalAction(GLOBAL_ACTION_HOME)`) and subscribes to `SettingsRepository.settings` (`StateFlow<Settings>`). On `onDestroy()`, it cancels its `CoroutineScope`, cancels any pending `GracePeriodController` timer, and releases all references — leaving zero background threads or receivers behind.

---

## Keyword Detection & Whitelist Masking

### View-Tree Traversal

`AccessibilityNodeScanner` traverses `rootInActiveWindow` with a depth-first scan inspecting both `node.text` and `node.contentDescription`. Every child `AccessibilityNodeInfo` obtained via `getChild(i)` is recycled immediately in a `finally` block (with root managed cleanly by the caller) to prevent native IPC/Binder memory leaks on low-RAM devices.

### Matching Algorithm (`KeywordMatcher`)

1. **Whitelist masking:** Before checking blocked keywords, every configured whitelist phrase (case-insensitive, `Locale.ROOT`) is masked out of the scanned text with spaces of equal length so character indices remain intact. For example, if `"shorts"` is blocked and `"youtube shorts tutorial"` is whitelisted, the masked text will not trigger a match.
2. **Whole-word alternation regex:** All blocked keywords are lowercased, deduplicated, sorted longest-first, escaped with `Regex.escape`, and compiled once per settings update into a single word-boundary alternation regex: `(?<!\w)(?:$alternation)+(?!\w)`. Compiling once per config change keeps per-event allocations near zero.

---

## Grace Period Mechanism (`GracePeriodController`)

A configurable grace period (default **2 seconds**) prevents false positives from transient text.

- **Grace > 0 s:** When a blocked keyword is detected, a toast informs the user and `GracePeriodController` schedules a single delayed re-check on the main `Handler`. When it fires, `BlockerCoordinator` re-reads `rootInActiveWindow`; only if a blocked keyword is still present in the same foreground app does it call `pressHome()`. Navigating away or closing the app cancels the pending callback immediately.
- **Grace == 0 s:** `pressHome()` fires immediately on first detection.

---

## Exemptions & Full Blocker Pause

Evaluated purely inside `BlockingDecisionEngine`:

- **Self-skip:** Events from `com.warden.app` are ignored by keyword scanning.
- **Permanent ignore list:** Packages in `keywordBlockerConfig.ignoredApps` are skipped immediately.
- **Temporary ignore list:** Packages in `temporaryIgnoredApps: Map<String, Long>` are skipped while `clock.currentTimeMillis() < expiryMillis`. Once expired, scanning resumes automatically on the next event.
- **Global pause:** While `clock.currentTimeMillis() < blockerDisabledUntil`, all keyword scanning is skipped (self-protection remains active).

---

## Self-Protection (`SelfProtectionEvaluator`)

Before keyword scanning runs, `BlockerCoordinator` passes the window's package, class name, and visible text to `SelfProtectionEvaluator` (ignoring `com.warden.app` and safe browser/social/messaging apps):

1. **App Info protection:** If the screen references `"warden"` alongside `"force stop"`, `"kill"`, `"uninstall"`, or `"clear data"`, `pressHome()` triggers immediately.
2. **Accessibility revocation protection:** If the screen references `"warden"` alongside `"warden needs access to accessibility data"`, `"stop warden"`, or `"turn off warden"`, `pressHome()` triggers immediately.
3. **Device Admin deactivation protection:** If `antiUninstallEnabled` is true, the screen is a Device Admin deactivation screen referencing `"warden"` (class or text containing device-admin/deactivate cues), and the current time is outside the **180-second (3-minute) activation grace window** (`now - deviceAdminActivationRequestedAt >= 180_000`), `pressHome()` triggers immediately.

---

## Real-Time Settings Sync (`StateFlow`)

All configuration is persisted in `filesDir/datastore/settings.json` using `AtomicFile` + `org.json` via `JsonSettingsRepository` (behind the `SettingsRepository` interface).

`SettingsRepository` exposes a hot `StateFlow<Settings>`. Both `BlockerViewModel` (UI) and `WardenService` (Accessibility Service) observe the exact same singleton repository inside `AppContainer`. Updates take effect immediately in memory and on disk — **no exported `BroadcastReceiver` is needed or registered**.
