# Contributing to Warden

Thank you for contributing to Warden. This guide explains local setup, code style, architectural boundaries, verification commands, and the pull request workflow.

---

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 (Temurin recommended; JDK 21 also supported) |
| Android SDK | `compileSdk = 34`, `minSdk = 26`, Build-Tools 34/35 |
| Gradle | Handled automatically by `./gradlew` (`8.10.2`) |

---

## Local Setup

```bash
git clone https://github.com/YOUR_USERNAME/Warden.git
cd Warden
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease
```

To install and auto-grant Accessibility on a connected debug device:

```bash
./gradlew installAndGrantAccessibilityDebug
```

---

## Architecture & Coding Standards

- **Strict layering:** `domain` packages must remain pure Kotlin (zero `android.*`, `androidx.*`, `ui`, `data`, or `service` imports). `data` never imports `ui` or `service`. Enforced automatically by `ArchitectureRulesTest`.
- **Size & complexity caps:** Max **300 lines per file**, max **40 lines per function**, max **cyclomatic complexity 10**, zero `!!`, zero `lateinit var`, zero global mutable singletons, zero swallowed exceptions. Enforced by `detekt` (`config/detekt/detekt.yml`) and `ArchitectureRulesTest`.
- **Formatting:** Enforced automatically by `ktlint` (`.editorconfig`). Run `./gradlew ktlintFormat` before committing.
- **Android Lint:** Runs with `abortOnError = true` and `warningsAsErrors = true` (`app/lint.xml`). No hardcoded UI strings — always add entries to `app/src/main/res/values/strings.xml`.
- **Footprint discipline:** Never add a runtime dependency without updating `docs/DEPENDENCIES.md` and proving a platform API cannot replace it. Keep `Warden.onCreate()` free of synchronous disk/network I/O.
- **Upgrade safety:** Never rename manifest component FQCNs (`FragmentActivity`, `WardenService`, `WardenDeviceAdminReceiver`) or break `filesDir/datastore/settings.json` JSON keys.
- **Documentation:** Every public class, interface, object, and function requires KDoc explaining *what* and *why*.

---

## Running All Checks Locally

```bash
# 1. Auto-format Kotlin & Kotlin DSL scripts
./gradlew ktlintFormat

# 2. Run the exact CI quality gate
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease

# 3. Optional: check code duplication (< 3%)
npx --yes jscpd app/src/main/java --silent --reporters console
```

---

## Branch & PR Workflow

1. Branch from `main`: `git checkout -b feat/short-description` (or `fix/...`, `refactor/...`, `opt/...`).
2. Keep commits small, single-purpose, and buildable (`type(scope): summary`).
3. Ensure `./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease` passes locally.
4. Open a pull request against `main` describing *why* the change is needed, test evidence, and any APK/runtime impact.
5. Wait for CI checks and maintainer review.

---

## Security Disclosures

Do **not** open public issues for security vulnerabilities or bypass exploits. Use GitHub's private vulnerability reporting feature instead.
