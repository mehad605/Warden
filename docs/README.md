# Warden Documentation

Welcome to the Warden technical documentation.

---

## Core Architecture & Developer Guides

| Document | What you'll learn |
|---|---|
| [Architecture](ARCHITECTURE.md) | Layered architecture, package map, data flows, runtime footprint, and upgrade invariants |
| [How-To Recipes](HOW_TO.md) | Step-by-step recipes for adding a setting, feature, screen, dependency, or replacing the AI backend |
| [Architecture Decision Records](adr/) | Why key choices were made (AtomicFile JSON, platform HTTP, manual DI, R8/no-JNI, regex/whitelist) |
| [Dependencies](DEPENDENCIES.md) | Every production dependency, its justification, and post-R8 size budget |
| [Releasing](RELEASING.md) | Deterministic versioning, signing setup, dry runs, key rotation, and rollback |
| [GitHub Repo Settings](GITHUB_REPO_SETTINGS.md) | Recommended branch/tag protections, environment reviewers, and security settings |

## Feature & User Guides

| Document | What you'll learn |
|---|---|
| [How It Works](how-it-works.md) | Accessibility pipeline, regex + whitelist matching, grace period, self-protection, `StateFlow` settings sync |
| [AI Gatekeeper](ai-gatekeeper.md) | Adversarial Gemini adjudication over `HttpURLConnection`, validation rules, screenshot proof, `noCount` |
| [Anti-Uninstall](anti-uninstall.md) | Device Administrator integration, accessibility deactivation guard, 180-second grace window |
| [Configuration](configuration.md) | All persisted settings, defaults, password rules, and merge-safe export/import |

## Verification & Audit Records

| Document | What you'll learn |
|---|---|
| [Scorecard](SCORECARD.md) | Full S1–S8 rubric and verified evidence |
| [Parity Matrix](PARITY.md) | 17/17 Core Functionality Contract verification (`C1`–`C17`) |
| [Cold-Read Test](COLD_READ_TEST.md) | Blind onboarding verification by an independent reader |
| [Extension Proof](EXTENSION_PROOF.md) | Verified walkthrough of adding and removing a feature using `HOW_TO.md` |
| [Results](RESULTS.md) | Baseline vs. final metrics, key changes, and rollback guide |
| [Blockers](BLOCKERS.md) | Transparent log of hardware- and GitHub-admin-blocked criteria |

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for local setup, code style checks, and PR guidelines.
