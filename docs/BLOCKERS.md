# Warden Overhaul — Blockers & Unverified Criteria

This document records the **sole remaining criterion** across the entire 72-item S1–S8 scorecard that could not be completed automatically via CLI/API because GitHub intentionally never exposes raw secret values.

All other 71 / 72 criteria — including live Android 11 (API 30) emulator benchmarks (`S2.4`, `S2.9`), live `adb install -r` upgrade & `logcat` verification (`S8.3`), and GitHub repository security rulesets/environments (`S6.2`, `S6.6`, `S6.10`, `S6.11`) — are **100% VERIFIED PASS**.

---

## Remaining Owner-Only Action (1 / 72 Criteria)

| Criterion | What IS Already Done | What Remains (Owner-Only) | How to Complete (2 minutes) |
|---|---|---|---|
| **S6.5** Signing keys scoped exclusively to protected GitHub Environment | 1. Protected **`release` Environment** created via API (ID `28665925494`) with `@mehad605` required reviewer and deployment policies restricted to `v*.*.*` tags and `main`.<br>2. `.github/workflows/release.yml` binds `environment: release`, decodes `KEYSTORE_BASE64` to `mktemp "$RUNNER_TEMP/..."` (`chmod 600`), and shreds/deletes it in an `if: always()` step. | The 4 existing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) currently live in **Repository Secrets** (accessible to any workflow, not just `environment: release`). GitHub's API never returns raw secret values, so an agent cannot copy them into Environment Secrets without you supplying them. | In **GitHub → Settings → Environments → `release` → Add environment secret**, add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, then delete the 4 duplicates from **Settings → Secrets and variables → Actions → Repository secrets**. |

---

## Optional Physical-Device Spot Check

While `S2.4`, `S2.9`, and `S8.3` are verified on a live **Android 11 (API 30) KVM emulator (`aosp_atd;x86_64`)**, you may optionally run a final spot check on your physical Realme Narzo 20 (MediaTek Helio G85, Android 11) to observe real-world Realme UI OEM battery-killer behavior alongside `WardenService`.
