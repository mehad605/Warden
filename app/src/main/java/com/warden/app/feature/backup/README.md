# `feature/backup` Package

Off-main-thread JSON settings backup and merge-safe restore:

- `ui/BackupDialogController.kt` — Coordinates Storage Access Framework (`CreateDocument` / `OpenDocument`) flows, prompts whether to include sensitive credentials (`passwordHash`, `geminiApiKey`) on export, and merges imported keywords, whitelists, and ignored apps via `SettingsJsonCodec` and `BlockerViewModel`.
