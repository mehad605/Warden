# GitHub Repository Security Settings Status (S6.13)

Most supply-chain hardening controls for `mehad605/Warden` have **already been applied via GitHub API**, leaving only one secret-copy step that requires the repository owner (because GitHub's API never exposes existing secret values).

## 1. Protected `release` Environment (S6.5)
Path: **Settings → Environments → `release`** (ID `28665925494`)
- [x] Created `release` Environment with **Required reviewer** `@mehad605`.
- [x] Restricted **Deployment branches and tags** to `v*.*.*` (tags) and `main` (branch).
- [ ] **Owner action required:** Copy the 4 existing signing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) into **Environment Secrets** on `release`, then remove the duplicates from **Repository Secrets** so non-release workflows cannot read them.

## 2. Branch Ruleset on `main` (S6.6)
Path: **Settings → Rules → Rulesets → `Protect main`** (ID `28177034`)
- [x] Active ruleset blocking deletions and force pushes.
- [x] Requires a pull request before merging (dismisses stale reviews on push).
- [x] Requires status checks to pass before merging:
  - `Format, Static Analysis, Lint, Test & Release Build`
  - `Dependency Review`

## 3. Tag Ruleset for Releases (S6.6)
Path: **Settings → Rules → Rulesets → `Protect release tags (v*)`** (ID `28177036`)
- [x] Active tag ruleset targeting `refs/tags/v*` blocking creation, update, and deletion by non-administrators (repository admin bypass enabled).

## 4. Code Security & Secret Scanning (S6.10, S6.11)
Path: **Settings → Code security**
- [x] **Dependency graph / Vulnerability alerts** enabled (verified: `Dependency Review` passed on PR #1).
- [x] **Dependabot security updates** enabled (`.github/dependabot.yml` committed for weekly `github-actions` + `gradle` updates with 7-day cooldown).
- [x] **Secret scanning** and **Push protection** enabled.

## 5. GitHub Actions General Permissions (S6.2)
Path: **Settings → Actions → General → Workflow permissions**
- [x] Default `GITHUB_TOKEN` permissions set to **Read repository contents and packages permissions**.
- [x] **Allow GitHub Actions to create and approve pull requests** disabled.
