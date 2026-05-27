# Contributing to Warden

Thank you for your interest in contributing. This document covers the development environment setup, code conventions, and the pull request process.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)
- [Security Disclosures](#security-disclosures)

---

## Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | API 34 |
| Gradle | Handled by wrapper (`./gradlew`) |

---

## Development Setup

```bash
# 1. Fork the repository, then clone your fork
git clone https://github.com/YOUR_USERNAME/Warden.git
cd Warden

# 2. Open in Android Studio
# File → Open → select the Warden directory

# 3. Let Gradle sync automatically, or run manually:
./gradlew build

# 4. Connect a physical device (API 26+) or start an emulator
# Note: The Accessibility Service does not function on all emulator configurations

# 5. Install and grant permissions
./gradlew installDebug
```

---

## Project Structure

See [Architecture](docs/architecture.md) for the full component map. Key files for contributors:

| File | Role |
|---|---|
| `KeywordBlocker.kt` | Core blocking logic — most feature work happens here |
| `WardenService.kt` | Accessibility event dispatcher |
| `GeminiManager.kt` | All AI gatekeeper prompts and API calls |
| `DataStore.kt` | Settings persistence layer |
| `KeywordBlockerFragment.kt` | Main settings UI |
| `KeywordBlockerViewModel.kt` | UI state and AI orchestration |

---

## Code Style

- **Language:** Kotlin only — no Java in new code
- **Formatting:** Follow standard Kotlin coding conventions (enforced by Android Studio's default formatter)
- **Naming:** Use descriptive names; avoid abbreviations unless they are widely understood (`pkg`, `vm`, `ctx`)
- **Comments:** Comment *why*, not *what*. The code should be self-explanatory for the *what*
- **Coroutines:** Use `Dispatchers.IO` for DataStore and network calls; `Dispatchers.Main` for UI updates
- **No hardcoded strings:** All user-visible text must go in `res/values/strings.xml`

---

## Submitting Changes

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes.** Keep commits focused — one logical change per commit.

3. **Verify the build passes:**
   ```bash
   ./gradlew compileDebugKotlin
   ./gradlew assembleDebug
   ```

4. **Open a pull request** against `main` with:
   - A clear title describing the change
   - A description of *why* the change is needed
   - Any relevant issue numbers (`Closes #123`)

5. A maintainer will review your PR. Please be responsive to feedback.

---

## Reporting Issues

Open a GitHub Issue and include:

- Android version and device model
- Steps to reproduce
- Expected vs. actual behavior
- Crash log if applicable (available in Warden's **System Diagnostics** screen)

---

## Security Disclosures

If you discover a security vulnerability (e.g., a way to bypass the blocker or extract the stored password hash), please **do not open a public issue**. Instead, contact the maintainers privately via GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) feature.
