# Warden Documentation

Welcome to the Warden documentation index.

---

## User & Feature Guides

| Document | What you'll learn |
|---|---|
| [How It Works](how-it-works.md) | Accessibility pipeline, regex + whitelist matching, grace period, self-protection, `StateFlow` settings sync |
| [AI Gatekeeper](ai-gatekeeper.md) | Adversarial Gemini adjudication, local validation rules, screenshot proof, credibility score (`noCount`) |
| [Anti-Uninstall](anti-uninstall.md) | Device Administrator lock, accessibility deactivation guard, 180-second activation grace window |
| [Configuration](configuration.md) | All persisted settings, defaults, password rules, and merge-safe export/import |

## Architecture & Developer Guides

| Document | What you'll learn |
|---|---|
| [Developer Guide](../DEVELOPERS.md) | Technical overview, build/test commands, quality gates, and upgrade invariants |
| [Architecture](ARCHITECTURE.md) | Layered architecture, package map, data flows, and runtime footprint |
| [How-To Recipes](HOW_TO.md) | Step-by-step recipes for adding a setting, feature, screen, dependency, or replacing the AI backend |
| [Architecture Decision Records](adr/) | Why major engineering choices were made |
| [Dependencies](DEPENDENCIES.md) | Every production dependency, its justification, and size budget |
| [Releasing](RELEASING.md) | Deterministic versioning, dry runs, signing key rotation, and rollback |
| [GitHub Repo Settings](GITHUB_REPO_SETTINGS.md) | Branch/tag rulesets, `release` environment, and security controls |

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for local setup, code style checks, and PR guidelines.
