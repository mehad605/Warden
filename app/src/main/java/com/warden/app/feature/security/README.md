# `feature/security` Package

Password gating and anti-uninstall Device Admin UI controls:

- `domain/` — `PasswordPolicy` (pure Kotlin validation: ≥6 chars, letter + digit, SHA-256 lowercase hex hashing compatible with `baseline-v1`).
- `ui/` — `PasswordDialogController` (set/verify/change password dialogs) and `AntiUninstallUiController` (coordinates Device Admin activation/deactivation with the 180-second grace window).
