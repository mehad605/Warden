# AI Gatekeeper

Warden integrates Google Gemini as an adversarial AI gatekeeper. Its default stance is **NO** — requiring a concrete, verifiable justification before granting temporary exemptions or pauses.

---

## Architecture & Transport

The AI Gatekeeper lives in `com.warden.app.feature.gatekeeper`:
- **Domain contract (`domain/`):** `GatekeeperClient`, `GatekeeperRequest`, `GatekeeperVerdict`, `JustificationValidator`, `GatekeeperPromptBuilder` (pure Kotlin, zero Android imports).
- **HTTP transport (`data/`):** `HttpGeminiGatekeeperClient` calls the Gemini REST API directly over Android's built-in `HttpURLConnection` + `org.json` (no third-party SDK or OkHttp/Ktor dependency). See [ADR 0003](adr/0003-platform-http-gemini-gatekeeper.md).
- **UI & Image Encoding (`ui/`):** `GatekeeperDialogController`, `GatekeeperDialogViewBinder`, and `BitmapImageEncoder` (downscales optional screenshots to max 1024 px and JPEG 80% before base64 encoding).

Because all callers depend only on `GatekeeperClient`, the backend can be replaced in two files (see [HOW_TO.md](HOW_TO.md#recipe-4-replace-the-network--ai-layer)).

---

## Setup & API Key

1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Enter the key in Warden's Gemini configuration section.
3. Warden calls `GET https://generativelanguage.googleapis.com/v1beta/models?key={apiKey}`, filters models supporting `generateContent`, and lets you pick one.

Your API key is stored locally in `filesDir/datastore/settings.json` and is sent only to `generativelanguage.googleapis.com`.

---

## Four Gatekeeper Request Types (`GatekeeperRequest`)

Domain requests are modeled as the sealed class `GatekeeperRequest` (`feature/gatekeeper/domain/GatekeeperModels.kt`), triggered via `GatekeeperDialogMode` (`TEMPORARY_IGNORE`, `PAUSE_BLOCKER`, `EXTEND_IGNORE`):

1. **App Exemption (`GatekeeperRequest.AppExemption`):** Temporarily exempts one app for **5–60 minutes** (`temporaryIgnoredApps[pkg] = now + duration`). Default stance: strict **NO**.
2. **Full Blocker Pause (`GatekeeperRequest.PauseBlocker`):** Pauses keyword scanning. Requires concrete proof (an email address, URL, or attached screenshot) in addition to justification; on approval, `KeywordBlockerFragment.applyGatekeeperApproval()` dispatches `BlockerAction.SetBlockerActive(false)` (preserving `baseline-v1` parity, while `BlockingDecisionEngine` also honors any persisted `blockerDisabledUntil` timestamp).
3. **Duration Extension (`GatekeeperRequest.ExtendExemption`):** Adds **5–60 minutes** to an active temporary exemption.
4. **Re-enable Blocking (`GatekeeperRequest.RemoveFromIgnore`):** Removes an app from the ignore list. Reversed stance — default **YES** with `durationMinutes = 0` (bypasses minimum-sentence validation).

---

## Local Pre-Call Validation (`JustificationValidator`)

Before any network call is sent, `JustificationValidator.validate(text, isRemoval)` enforces (when `isRemoval == false`):
- Minimum **7 sentences**
- Minimum **5 words per sentence**
- No repetitive / list-like sentences (> 50% duplicate words)

Invalid inputs are rejected immediately with inline feedback and consume zero API quota.

---

## Credibility Score (`noCount`)

Every rejected request increments `noCount` via `BlockerAction.IncrementRejectionCount`. The count is embedded into `GatekeeperPromptBuilder` so repeated bypass attempts make future approval progressively harder.

---

## Response Schema

`HttpGeminiGatekeeperClient` sets `responseMimeType = "application/json"` and parses the response into `GatekeeperVerdict`:

```kotlin
data class GatekeeperVerdict(
    val approved: Boolean,
    val durationMinutes: Int,
    val reasoning: String,
    val botResponse: String,
)
```

---

## Privacy

- Text justifications and optional compressed screenshots go directly from the device to Google's Gemini endpoint over HTTPS.
- No Warden backend or telemetry service exists.
- Chat transcripts are kept only in memory for the lifetime of the dialog.
