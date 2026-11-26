# `feature/gatekeeper` Package

Adversarial AI Gatekeeper feature that adjudicates temporary exemptions, blocker pauses, and extensions:

- `domain/` — Pure Kotlin boundary: `GatekeeperClient` interface, `GatekeeperModels` (`GatekeeperRequest`, `GatekeeperVerdict`, `EncodedImage`), `JustificationValidator` (local pre-call validation), and `GatekeeperPromptBuilder`.
- `data/` — `HttpGeminiGatekeeperClient`: zero-SDK HTTPS transport over `HttpURLConnection` + `org.json`.
- `ui/` — `GatekeeperDialogController`, `GatekeeperDialogViewBinder`, and `BitmapImageEncoder` (bounded 1024 px JPEG encoder).

Other features depend only on `domain/` (and `ui/` dialog controller); swapping the AI backend touches only `data/` and `AppContainer`.
