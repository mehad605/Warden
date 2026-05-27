# How Warden Works

This document explains the internals of Warden's blocking engine — from the moment an accessibility event fires to the moment the device is sent home.

---

## Table of Contents

- [Overview](#overview)
- [Accessibility Service Pipeline](#accessibility-service-pipeline)
- [Keyword Detection Algorithm](#keyword-detection-algorithm)
- [Grace Period Mechanism](#grace-period-mechanism)
- [Home-Press (Minimization)](#home-press-minimization)
- [Temporary Ignore & App Whitelisting](#temporary-ignore--app-whitelisting)
- [Self-Protection: Blocking Settings Access](#self-protection-blocking-settings-access)
- [Settings Sync via DataStore](#settings-sync-via-datastore)
- [Broadcast-Based Config Refresh](#broadcast-based-config-refresh)

---

## Overview

Warden runs as an Android **Accessibility Service** (`WardenService`), which gives it the ability to receive a callback for every `AccessibilityEvent` emitted by the system — including screen content changes, window state transitions, and UI hierarchy updates.

```
User opens app
       │
       ▼
  Android fires TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED
       │
       ▼
  WardenService.onAccessibilityEvent()
       │
       ▼
  KeywordBlocker.checkIfUserGettingFreaky()
       │
       ├──► Self-protection check (is this a Settings screen targeting Warden?)
       │
       └──► Keyword scan of entire view hierarchy
                  │
                  ├── No keyword found → return (do nothing)
                  │
                  └── Keyword found
                            │
                            ├── Grace period > 0 → wait, re-check, then pressHome()
                            │
                            └── Grace period = 0 → pressHome() immediately
```

---

## Accessibility Service Pipeline

### Registration

The service is registered in `AndroidManifest.xml` as an `AccessibilityService` and configured via `res/xml/blockwords_service_config.xml`, which declares:

- **Event types listened to:** `typeWindowContentChanged`, `typeWindowStateChanged`
- **`canRetrieveWindowContent: true`** — grants access to the full view hierarchy tree

Because Android requires explicit user consent for accessibility services, Warden cannot activate itself; the user must enable it in `Settings → Accessibility`.

### Service Class Hierarchy

```
AccessibilityService  (Android SDK)
        │
BaseBlockingService   (provides DataStore, pressHome/pressBack helpers, delay util)
        │
WardenService         (wires KeywordBlocker to accessibility events)
```

`BaseBlockingService` is intentionally thin — it only exposes the helpers that any future blocking module might need, keeping `WardenService` clean.

### Event Filtering

Not every event triggers a full scan. Only events with type flags matching `TYPE_WINDOW_CONTENT_CHANGED | TYPE_WINDOW_STATE_CHANGED` are processed. Within that, a cooldown (`refreshCooldown = 2000 ms` by default) prevents redundant scans on rapid-fire events from the same session.

---

## Keyword Detection Algorithm

### Entry Point

`KeywordBlocker.checkIfUserGettingFreaky(event)` is called on every qualifying event. It:

1. Reads `rootInActiveWindow` to obtain the root `AccessibilityNodeInfo` of the currently visible window.
2. Checks whether the active package is Warden's own (`com.warden.app`) — self events are skipped.
3. Checks the temporary and permanent ignore lists.
4. Calls `scanNodeForBlockedKeyword(rootNode)` to search the full view tree.

### Tree Traversal

`scanNodeForBlockedKeyword` performs a **recursive depth-first search** of the `AccessibilityNodeInfo` tree:

```kotlin
fun scanNodeForBlockedKeyword(node: AccessibilityNodeInfo?): String? {
    // Check node.text
    // Check node.contentDescription
    // Recurse into all child nodes
}
```

Both `text` and `contentDescription` of every node are checked. This catches:
- Visible text in `TextView`, `EditText`, feed titles, comments
- Accessibility labels on `ImageView` or custom views
- Content descriptions on buttons and icons

### Matching Logic

```kotlin
fun containsBlockedKeyword(text: String): String? {
    val lowerText = text.lowercase()
    for (keyword in blockedKeyword) {
        if (keyword.isNotBlank() && lowerText.contains(keyword.lowercase())) {
            return keyword
        }
    }
    return null
}
```

Matching is **case-insensitive substring containment**. If you block `"news"`, it will also match `"Breaking News"`, `"newsroom"`, etc.

---

## Grace Period Mechanism

A configurable grace period (default: **2 seconds**) prevents false positives from transient text (e.g., a keyboard suggestion or a loading placeholder that appears briefly).

**Flow with grace period > 0:**

1. Keyword detected → toast shown immediately ("Blocked word detected")
2. `handler.postDelayed(runnable, graceSeconds * 1000)` schedules the home press
3. When the delayed runnable fires, `rootInActiveWindow` is read again
4. If the same keyword is still on screen in the same app → `pressHome()` fires
5. If the user has already navigated away → the runnable is cancelled silently

**Flow with grace period = 0:**

The home press fires immediately on first detection with no re-check.

The `cancelGracePeriod()` helper removes any pending runnable when the user naturally leaves the app — preventing a spurious home press after they've already left.

---

## Home-Press (Minimization)

`BaseBlockingService.pressHome()` calls:

```kotlin
performGlobalAction(GLOBAL_ACTION_HOME)
```

This is an Android Accessibility API call that simulates pressing the physical Home button. It:
- Works across all Android launchers and OEM skins
- Requires no special permissions beyond the accessibility service itself
- Is immediate and cannot be intercepted by the foreground app

---

## Temporary Ignore & App Whitelisting

### Permanent Ignore List

Apps in `ignoredApps` (set via the UI's "Select Ignored Apps" option) are never scanned. The check happens before the tree traversal:

```kotlin
if (ignoredApps.contains(appPackage)) {
    cancelGracePeriod()
    return
}
```

### Temporary Exemption

When the AI gatekeeper approves an exemption for a specific app, it returns a `durationMinutes` value. The app's package name is stored in `temporaryIgnoredApps: Map<String, Long>` with an expiry timestamp:

```
temporaryIgnoredApps[packageName] = System.currentTimeMillis() + durationMinutes * 60_000
```

At each event, `System.currentTimeMillis()` is compared against the expiry. Once expired, the app is scanned again automatically — no user action needed.

### Pause All Scanning

When the AI gatekeeper approves a full blocker pause, `blockerDisabledUntil` is set to a future timestamp. The main check path has:

```kotlin
if (System.currentTimeMillis() < blockerDisabledUntil) {
    cancelGracePeriod()
    return
}
```

---

## Self-Protection: Blocking Settings Access

Warden uses its own accessibility event stream to detect when the user navigates to a system UI screen that targets Warden itself. This prevents bypassing the blocker by going to `Settings → Apps → Warden → Force Stop / Uninstall`.

### Detection Logic

On every qualifying event, `checkIfUserGettingFreaky` runs a second check **before** keyword scanning:

1. Collects all visible text strings from the screen into a flat list (`collectScreenTexts`)
2. Checks whether any string contains `"warden"` (case-insensitive) — identifying this as a screen about Warden
3. If it is about Warden **and** the screen contains `"force stop"`, `"uninstall"`, or `"clear data"` → `pressHome()` immediately
4. If Device Admin is enabled and the screen is a **Device Admin** deactivation screen (detected via `className` containing `"DeviceAdmin"` or text containing `"deactivate"`) → `pressHome()` immediately, unless the activation was triggered by Warden itself within the last 3 minutes

### Activation Grace Window

To allow the user to activate Device Admin through the system prompt (which itself shows "Warden" on screen), a 3-minute grace window is tracked via `deviceAdminActivationRequestedAt`. During this window, Device Admin screens are not blocked.

---

## Settings Sync via DataStore

All configuration is stored in **Jetpack DataStore** (Proto) via `DataStoreManager`. Settings are exposed as a `Flow<Settings>`, which `KeywordBlocker` subscribes to using `collectLatest`. Any settings change (new keyword added, grace period updated, etc.) propagates to the active blocker instance in real time without restarting the service.

The `Settings` data class is the single source of truth:

```kotlin
data class Settings(
    val keywordBlockerConfig: KeywordBlocker,  // keywords + ignored apps + active flag
    val passwordHash: String?,                 // BCrypt-equivalent hash of the UI password
    val geminiApiKey: String?,                 // stored locally, never transmitted by Warden
    val noCount: Int,                          // AI rejection count (credibility score)
    val temporaryIgnoredApps: Map<String, Long>,
    val blockerDisabledUntil: Long,
    val ignoreGracePeriodSeconds: Int,
    val selectedGeminiModel: String,
    val availableGeminiModels: List<String>,
    val antiUninstallEnabled: Boolean,
    val deviceAdminActivationRequestedAt: Long
)
```

---

## Broadcast-Based Config Refresh

When the UI updates settings (e.g., the user adds a keyword), it sends a local broadcast:

```
com.warden.app.refresh.keywordblocker.config
```

`KeywordBlocker` registers a `BroadcastReceiver` for this action and calls `setupBlocker()` on receipt, reloading config from DataStore. This ensures the running accessibility service picks up changes instantly without a service restart.
