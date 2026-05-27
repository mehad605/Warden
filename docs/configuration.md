# Configuration Reference

A complete reference for every configurable option in Warden, what it does, and recommended values.

---

## Table of Contents

- [Keyword Blocker](#keyword-blocker)
- [Grace Period](#grace-period)
- [Password Protection](#password-protection)
- [AI Gatekeeper (Gemini)](#ai-gatekeeper-gemini)
- [Anti-Uninstall Protection](#anti-uninstall-protection)
- [App Ignore List](#app-ignore-list)
- [Export & Import](#export--import)

---

## Keyword Blocker

### Enable / Disable

**Setting:** `keywordBlockerConfig.isActive`

Toggles all keyword scanning on or off. When disabled, the accessibility service remains running but the keyword check is skipped entirely. Other features (self-protection, usage tracking) are unaffected.

**Recommended:** Always keep enabled when you want active focus protection.

---

### Blocked Keywords

**Setting:** `keywordBlockerConfig.blockedKeywords` (List)

A list of words or phrases. If any of these appear anywhere in the text content or content descriptions of any visible UI node, the app is minimized.

**Tips:**
- Keep keywords specific enough to avoid false positives (e.g., `"instagram"` rather than `"gram"`)
- Keyword matching is **case-insensitive substring** — `"news"` blocks `"Breaking News"`, `"newsroom"`, etc.
- You can block phrases, not just single words (e.g., `"sports scores"`)
- There is no limit on the number of keywords

**Recommended starting keywords:** `instagram`, `tiktok`, `reels`, `shorts`, `reddit`, `twitter`

---

## Grace Period

**Setting:** `ignoreGracePeriodSeconds` (Int, default: `2`)

Number of seconds Warden waits after detecting a keyword before pressing Home. During this window, it re-checks whether the keyword is still on screen in the same app. If the user has already navigated away, the action is cancelled.

| Value | Behavior |
|---|---|
| `0` | Instant block — fires on first detection, no re-check |
| `2` (default) | 2-second delay before home press |
| Higher values | More lenient — good if you have many false positives |

**Recommended:** `2` for most users. Set to `0` if you want zero-tolerance.

---

## Password Protection

**Setting:** `passwordHash` (String, nullable)

A password that gates access to:
- Adding or removing blocked keywords
- Changing the grace period
- Disabling the blocker
- Toggling Anti-Uninstall

Passwords must meet the following requirements:
- Minimum 6 characters
- Must contain at least one letter
- Must contain at least one number

The hash is stored locally. Warden does not offer password recovery — if you forget your password, the only option is to uninstall the app (after deactivating Device Admin if enabled).

**Recommended:** Set a strong password immediately after initial setup.

---

## AI Gatekeeper (Gemini)

### API Key

**Setting:** `geminiApiKey` (String, nullable)

Your Google Gemini API key. Obtain one free at [Google AI Studio](https://aistudio.google.com/app/apikey).

Stored on-device in DataStore. Never transmitted except directly to `generativelanguage.googleapis.com`.

AI features are disabled if no key is set.

---

### Selected Model

**Setting:** `selectedGeminiModel` (String)

Which Gemini model handles your gatekeeper decisions. Options are fetched live from the API when you enter your key.

| Model | Speed | Quality | Notes |
|---|---|---|---|
| `gemini-1.5-flash` | Fast | Good | Recommended for most users |
| `gemini-1.5-pro` | Slower | Excellent | Better nuanced judgment |
| `gemini-2.0-flash` | Very fast | Good | If available in your region |

---

### No Count (Credibility Score)

**Setting:** `noCount` (Int, default: `0`)

Tracks how many times the AI gatekeeper has rejected your requests. This value is included in every AI prompt so the model can factor in your history of bypass attempts. Higher scores make future approvals harder to obtain.

Can be reset manually in settings (requires password).

---

## Anti-Uninstall Protection

**Setting:** `antiUninstallEnabled` (Boolean, default: `false`)

When enabled:
1. Warden registers as a **Device Administrator** — preventing uninstallation from the standard app info screen
2. The accessibility service monitors for Device Admin deactivation screens and navigates away from them
3. Force Stop and Clear Data actions targeting Warden are intercepted and blocked

See [Anti-Uninstall Documentation](anti-uninstall.md) for full details.

**Recommended:** Enable after initial setup is complete and you're satisfied with your configuration.

---

## App Ignore List

**Setting:** `keywordBlockerConfig.ignoredApps` (List)

Apps in this list are permanently exempt from keyword scanning. All events from these packages are skipped before any text processing occurs.

**When to use:**
- Apps where keyword blocking causes false positives (e.g., your note-taking app where you write about blocked topics)
- Communication apps you genuinely need unrestricted (note: you can use the AI exemption instead for temporary access)
- System apps that display overlapping content

**Temporary ignore** (via AI exemption) is tracked separately in `temporaryIgnoredApps` and expires automatically.

---

## Export & Import

Warden can export and restore the full `Settings` object as a JSON file.

### Export

Serializes the current `Settings` data class to JSON and writes it to a file in your Downloads folder. Useful before a factory reset or device migration.

> ⚠️ The exported file contains your **Gemini API key** in plaintext. Store it securely.

### Import

Reads a previously exported JSON file and overwrites the current settings. All fields are restored, including keywords, passwords, and the API key.

> ⚠️ Importing will overwrite all current settings. There is no undo.
