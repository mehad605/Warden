# AI Gatekeeper

Warden integrates Google Gemini as an adversarial AI gatekeeper. Its role is the opposite of a helpful assistant — its default answer is **NO**, and it requires genuinely compelling justification before granting any exception to the blocker.

---

## Table of Contents

- [Overview](#overview)
- [Setup & API Key](#setup--api-key)
- [Three Gatekeeper Actions](#three-gatekeeper-actions)
  - [1. App Exemption](#1-app-exemption)
  - [2. Full Blocker Pause](#2-full-blocker-pause)
  - [3. Duration Extension](#3-duration-extension)
  - [4. Remove from Ignore List (Reverse)](#4-remove-from-ignore-list-reverse)
- [Input Validation Before AI Call](#input-validation-before-ai-call)
- [Screenshot Proof](#screenshot-proof)
- [Credibility Score (noCount)](#credibility-score-nocount)
- [Model Selection](#model-selection)
- [Response Structure](#response-structure)
- [Privacy](#privacy)

---

## Overview

Every time you want to relax a restriction, you must open a chat dialog and write a justification. Warden sends your text (and optionally a screenshot) to Gemini with a strict system prompt that instructs the model to behave as an anti-procrastination enforcer. The model returns a structured JSON decision.

This design exploits the psychological friction of having to articulate your reason to an impartial judge. Even if the AI approves, writing the justification takes time and forces reflection.

---

## Setup & API Key

1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. In Warden, go to **Keyword Blocker → Configuration** and paste your key.
3. Warden fetches the list of available Gemini models from the API and lets you choose one.

Your API key is stored in DataStore on-device and is only ever sent to `generativelanguage.googleapis.com` — it is never routed through any Warden server.

---

## Three Gatekeeper Actions

### 1. App Exemption

**Trigger:** You want to temporarily whitelist a specific app (e.g. allow a browser without keyword blocking for a limited time).

**System prompt stance:** `"Your default response must be NO."` — the gatekeeper is instructed to refuse whitelisting of browsers, social apps, messengers, and downloaders unless the justification represents an absolute work or study emergency, with convincing logic and/or image proof.

**If approved:**
- `durationMinutes` is granted between **5 and 60 minutes**
- The app is added to `temporaryIgnoredApps` with an expiry timestamp
- Keyword scanning resumes automatically once expired

**If rejected:**
- The user sees the model's explanation in the chat bubble
- `noCount` (the credibility score) is incremented

---

### 2. Full Blocker Pause

**Trigger:** You want to disable all keyword scanning entirely for a short window.

**System prompt stance:** Even stricter than app exemption. The gatekeeper **must reject** the request unless the user provides at least one of:
- An **email address** in the message body
- A **URL / link** in the message body
- An **attached screenshot** as evidence

If none of these three proof types are present, the model must reject regardless of the quality of the written justification.

**If approved:**
- `durationMinutes` is granted between **5 and 15 minutes**
- `blockerDisabledUntil` is set to `currentTime + duration`
- All keyword scanning is suspended for all apps

---

### 3. Duration Extension

**Trigger:** You have an active temporary exemption for an app and want more time before it expires.

**System prompt stance:** Same adversarial default as app exemption. The gatekeeper must assess whether the original task is genuinely ongoing and the extension is proportionate.

**If approved:**
- `durationMinutes` is added to the existing expiry timestamp
- The extension is bounded to **5–60 minutes**

---

### 4. Remove from Ignore List (Reverse)

**Trigger:** You want to re-enable keyword blocking on an app that was previously in the permanent ignore list.

**System prompt stance:** Reversed — the default is **YES**. The user is trying to impose stricter controls on themselves, so the gatekeeper eagerly approves and returns `durationMinutes: 0` (permanent removal).

---

## Input Validation Before AI Call

To prevent gaming the system with meaningless filler text, Warden validates the user's written justification **before** sending it to Gemini:

| Rule | Detail |
|---|---|
| Minimum 7 sentences | The explanation must have enough depth to demonstrate real thought |
| Minimum 5 words per sentence | Single-word or two-word sentences are rejected |
| No repetitive sentences | Sentences with more than 50% duplicate words are flagged as "repetitive or list-like" |

Validation is done locally in `GeminiManager.validateUserText()` and surfaces an inline error to the user before any API call is made.

---

## Screenshot Proof

The chat dialog allows attaching a screenshot from the device gallery. When provided, the image is passed to Gemini as a multimodal input alongside the text:

```kotlin
model.generateContent(content {
    image(bitmap)
    text(userPrompt)
})
```

Submitting a screenshot as evidence significantly increases the probability of approval for borderline cases, since the model can visually verify the claimed context (e.g., an assignment deadline, a work email, a lecture slide).

---

## Credibility Score (noCount)

Each AI rejection increments `noCount`, which is persisted in DataStore and passed to the model as part of every subsequent prompt:

```
"User's historical rejection count: $noCount."
```

The model is instructed to treat a high rejection count as evidence of a pattern of procrastination-driven bypass attempts. Practically, the more rejections you accumulate, the harder it becomes to get future approvals.

`noCount` resets only when cleared explicitly in settings.

---

## Model Selection

Warden fetches the list of available Gemini models dynamically from the API at configuration time:

```
GET https://generativelanguage.googleapis.com/v1beta/models?key={apiKey}
```

Only models that support `generateContent` are shown. You can switch models at any time — for example, between `gemini-1.5-flash` (fast, cheap, good for most decisions) and `gemini-1.5-pro` (slower, more nuanced reasoning).

---

## Response Structure

The model is instructed to respond **only with raw JSON** (no markdown wrappers). Warden parses the response into:

```kotlin
data class GeminiResult(
    val approved: Boolean,       // Whether the request is granted
    val durationMinutes: Int,    // How long the exemption lasts (0 = permanent)
    val reasoning: String,       // Model's internal logic (logged, not shown to user)
    val botResponse: String      // Message displayed in the chat bubble
)
```

The `responseMimeType = "application/json"` generation config hint is set to encourage well-formed JSON output.

---

## Privacy

- Your written justification text and optional screenshot are sent directly to the **Google Gemini API** over HTTPS.
- No Warden server, proxy, or third party receives any of this data.
- Warden does not log, store, or cache the content of your AI conversations beyond the chat session.
- Google's data handling for the Gemini API is governed by [Google's Privacy Policy](https://policies.google.com/privacy).
