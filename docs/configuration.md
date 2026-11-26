# Configuration Reference

A complete reference for every persisted setting in `Settings` (`filesDir/datastore/settings.json`).

---

## Keyword Blocker

### `keywordBlockerConfig.isActive` (`Boolean`, default: `false`)
Toggles keyword scanning on or off. Self-protection remains active while the Accessibility Service is enabled.

### `keywordBlockerConfig.blockedKeywords` (`List<String>`, default: `[]`)
Normalized lowercase words or phrases. Compiled into a single case-insensitive whole-word alternation regex (`(?<!\w)(?:...)+(?!\w)`) and matched against visible `text` and `contentDescription` nodes in non-ignored apps.

### `keywordBlockerConfig.whitelistedKeywords` (`List<String>`, default: `[]`)
Phrases masked out of scanned text *before* blocked keywords are checked. Use this to prevent false positives (e.g., block `"shorts"` while whitelisting `"cargo shorts"`).

### `keywordBlockerConfig.ignoredApps` (`List<String>`, default: `[]`)
Package names permanently exempt from keyword scanning. Managed through the dashboard's ignored-apps list and AI Gatekeeper dialogs.

---

## Grace Period

### `ignoreGracePeriodSeconds` (`Int`, default: `2`)
Seconds Warden waits after detecting a blocked keyword before re-scanning and pressing Home.
- `0` — immediate Home press on first detection.
- `2` (default) — 2-second re-check window to tolerate transient UI elements.

---

## Password Protection

### `passwordHash` (`String?`, default: `null`)
Lowercase SHA-256 hex digest of the user's configuration password (`PasswordPolicy`). Gates sensitive settings changes (removing keywords, changing grace period, disabling anti-uninstall, resetting `noCount`, modifying API key).

Password validation rules (`PasswordPolicy`):
- Minimum **6 characters**
- At least **1 letter**
- At least **1 digit**

Warden stores only the hash locally and cannot recover forgotten passwords.

---

## AI Gatekeeper (Gemini)

### `geminiApiKey` (`String?`, default: `null`)
Google Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey). Stored locally in `settings.json` and sent only to `https://generativelanguage.googleapis.com` over HTTPS (`HttpGeminiGatekeeperClient`).

### `selectedGeminiModel` (`String`, default: `""`, runtime fallback `"models/gemini-1.5-flash"`)
Active Gemini model used for gatekeeper evaluations. When empty, `BlockerViewModel` and `HttpGeminiGatekeeperClient` fall back to `"models/gemini-1.5-flash"`.

### `availableGeminiModels` (`List<String>`, default: `[]`)
Cached list of `generateContent`-capable models fetched when the user saves or refreshes their API key.

### `noCount` (`Int`, default: `0`)
Cumulative count of AI rejections. Sent in every evaluation prompt as credibility context; resettable from settings (password-gated when a password is set).

### `temporaryIgnoredApps` (`Map<String, Long>`, default: `{}`)
Map of `packageName -> expiryEpochMillis` for AI-approved temporary app exemptions (5–60 minutes).

### `blockerDisabledUntil` (`Long`, default: `0L`)
Epoch timestamp (ms) until which global keyword scanning is paused following an AI-approved full pause (5–15 minutes).

---

## Anti-Uninstall Protection

### `antiUninstallEnabled` (`Boolean`, default: `false`)
Enables Device Administrator registration (`WardenDeviceAdminReceiver`) and the accessibility-based deactivation guard. See [Anti-Uninstall](anti-uninstall.md).

### `deviceAdminActivationRequestedAt` (`Long`, default: `0L`)
Timestamp (ms) when Warden opened the system Device Admin consent prompt, granting a 180-second grace window so self-protection does not block activation.

---

## Export & Import (Backup)

Handled off the main thread by `BackupDialogController` using Android's Storage Access Framework (`CreateDocument` / `OpenDocument`) and `SettingsJsonCodec`.

### Export
- Exports keywords, whitelist, ignored apps, grace period, and model preferences via `SettingsJsonCodec.prepareForExport()`.
- **Anti-uninstall reset:** Export always resets `antiUninstallEnabled = false` and `deviceAdminActivationRequestedAt = 0L` so importing on another device never claims active Device Admin before system registration.
- **Secrets opt-in:** Exporting `passwordHash` and `geminiApiKey` is optional — the user explicitly chooses whether to include sensitive credentials before writing the JSON file.

### Import (Merge-Only)
- Importing a backup **merges** keywords, whitelisted phrases, and ignored apps into existing settings rather than silently wiping existing rules, and safely restores optional fields using `SettingsJsonCodec` defaults for any missing keys.
