# Releasing Warden

This runbook covers deterministic versioning, dry runs, publishing a signed release, rotating the signing keystore, and rolling back a release.

---

## 1. Versioning Rules

- **Tag format:** Strict SemVer `vMAJOR.MINOR.PATCH` (for example `v2.0.0`). Non-SemVer tags are rejected by both Gradle (`app/build.gradle.kts`) and `.github/workflows/release.yml`.
- **Single source of truth:** Local default builds read `version.properties` (`VERSION_NAME=2.0.0`). CI release builds pass `-PVERSION_NAME` derived from the tag.
- **Deterministic `versionCode`:**
  ```
  versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
  ```
  Examples: `v2.0.0 -> 20000`, `v2.1.3 -> 20103`. This guarantees strictly monotonic upgrades over `baseline-v1` (`versionCode = 50`) without depending on mutable CI run numbers.

---

## 2. Release Pipeline Overview

`.github/workflows/release.yml` runs three isolated stages:

1. **`validate`** (`permissions: { contents: read }`, no build cache): validates Gradle wrapper checksum, enforces SemVer tag format, runs `./gradlew ktlintCheck detekt lint testDebugUnitTest --no-daemon`.
2. **`dry-run`** (when triggered via `workflow_dispatch` with `dry_run: true`, default): builds the release APK + CycloneDX SBOM, runs `scripts/verify_release_apk.sh` (`apksigner verify`, 2.5 MiB size budget, exact `versionName`/`versionCode`, embedded `assets/dexopt/baseline.prof`, zero `.so` libs), generates `SHA256SUMS.txt`, and uploads workflow artifacts for 7 days — touching **zero** production secrets and creating **no** GitHub Release.
3. **`release`** (on SemVer tag push or `workflow_dispatch` with `dry_run: false`, gated by GitHub `environment: release`):
   - Decodes `KEYSTORE_BASE64` into a `0600` temp file under `$RUNNER_TEMP` and shreds/deletes it in an `if: always()` step.
   - Builds signed `Warden-vX.Y.Z.apk` and CycloneDX 1.5 SBOM `Warden-vX.Y.Z.cdx.json`.
   - Runs `scripts/verify_release_apk.sh` against the signed APK.
   - Generates `SHA256SUMS.txt` and attests build provenance (`actions/attest-build-provenance`).
   - Creates or reuses a **draft** GitHub Release with auto-generated notes, uploads all assets (`--clobber`), and flips `--draft=false` only after every upload succeeds.

---

## 3. Running a Dry Run

### Local Dry Run
```bash
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleRelease generateReleaseSbom \
  -PVERSION_NAME=2.0.0
bash scripts/verify_release_apk.sh app/build/outputs/apk/release/Warden-release.apk 2.0.0 20000
```

### CI Dry Run (Safe on Any Branch)
```bash
gh workflow run release.yml --ref overhaul/v2 -f version_tag=v2.0.0 -f dry_run=true
```

---

## 4. Publishing a Production Release

1. Ensure `main` is green in `CI Verification` and the GitHub `release` environment has the signing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) configured.
2. Update `version.properties` if bumping the default version and merge to `main`.
3. Create and push an annotated SemVer tag:
   ```bash
   git checkout main && git pull --ff-only
   git tag -a v2.0.0 -m "Release Warden v2.0.0"
   git push origin v2.0.0
   ```
4. Approve the `release` environment deployment gate in GitHub Actions (when required reviewers are configured).
5. Verify the published GitHub Release contains:
   - `Warden-v2.0.0.apk`
   - `Warden-v2.0.0.cdx.json`
   - `SHA256SUMS.txt`
   - Build provenance attestation (verifiable via `gh attestation verify Warden-v2.0.0.apk --owner mehad605`).

---

## 5. Signing Key Rotation

Because Android binds app identity to `(applicationId, signingCertificate)`, rotating a signing key for a sideloaded GitHub Release APK requires users to export settings, uninstall, and reinstall (unless using Android APK Signature Scheme v3 key rotation lineage):

1. Generate a new PKCS#12 keystore offline:
   ```bash
   keytool -genkeypair -v -storetype PKCS12 \
     -keystore warden-release-v2.p12 -alias warden \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Back up the keystore and passwords in an encrypted offline password manager.
3. Base64-encode cleanly (`base64 -w 0 warden-release-v2.p12`) and update the 4 secrets in **GitHub Settings → Environments → `release`** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Never store keystore files or passwords in the repository or workflow logs.
4. Run a dry-run first, then document the certificate SHA-256 fingerprint in the next release notes so users can verify the planned rotation.

---

## 6. Rollback

- **Unpublish/supersede a bad release:** Never force-move an already-published SemVer tag. Mark the broken GitHub Release as pre-release or add a warning note, revert the offending commit on `main`, and cut the next patch tag (e.g. `v2.0.1`).
- **Full repository rollback to pre-overhaul baseline:**
  ```bash
  git checkout baseline-v1
  # Or revert main to baseline-v1:
  git revert --no-commit baseline-v1..HEAD && git commit -m "revert: restore baseline-v1"
  ```
