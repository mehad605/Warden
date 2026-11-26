# Recommended GitHub Repository Security Settings (S6.13)

Several supply-chain hardening controls live in GitHub repository administration rather than committed files. Configure these in **GitHub → Settings** for `mehad605/Warden`:

## 1. Protected `release` Environment (Required for S6.5)
Path: **Settings → Environments → New environment → `release`**
- [ ] Add **Required reviewers** (`@mehad605`) so production signing requires explicit approval.
- [ ] Restrict **Deployment branches and tags** to **Selected branches and tags** matching `v*.*.*` (and optionally `main` for manual dispatch).
- [ ] Move all 4 signing secrets from Repository Secrets into **Environment Secrets** on `release`:
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

## 2. Branch Protection / Ruleset on `main` (S6.6)
Path: **Settings → Rules → Rulesets → New branch ruleset** (target `main`):
- [ ] Restrict deletions and block force pushes.
- [ ] Require a pull request before merging (with **Code Owners** review via `.github/CODEOWNERS`).
- [ ] Require status checks to pass before merging:
  - `Format, Static Analysis, Lint, Test & Release Build` (`.github/workflows/ci.yml`)
  - `Dependency Review` (`.github/workflows/ci.yml`)
- [ ] Require signed commits (recommended).

## 3. Tag Protection Ruleset for Releases (S6.6)
Path: **Settings → Rules → Rulesets → New tag ruleset** (target `v*`):
- [ ] Restrict tag creation, updates, and deletion to repository administrators only.
- [ ] Prevents non-maintainers or compromised low-privilege tokens from triggering `.github/workflows/release.yml`.

## 4. Code Security & Secret Scanning (S6.11)
Path: **Settings → Code security**:
- [ ] Enable **Dependency graph**.
- [ ] Enable **Dependabot alerts** and **Dependabot security updates** (`.github/dependabot.yml` is already committed for weekly `github-actions` + `gradle` updates).
- [ ] Enable **Secret scanning** and **Push protection** (free for public repositories).

## 5. GitHub Actions General Permissions (S6.2)
Path: **Settings → Actions → General → Workflow permissions**:
- [ ] Set default `GITHUB_TOKEN` permissions to **Read repository contents and packages permissions**.
- [ ] Uncheck **Allow GitHub Actions to create and approve pull requests**.
