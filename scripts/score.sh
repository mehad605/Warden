#!/usr/bin/env bash
set -euo pipefail

REPORT_ONLY="false"
if [[ "${1:-}" == "--report-only" ]]; then
  REPORT_ONLY="true"
fi

TMP_LOG="$(mktemp)"
trap 'rm -f "$TMP_LOG"' EXIT

set +e
./gradlew :app:testDebugUnitTest \
  --tests "com.warden.app.BlockerScoreSuiteTest" \
  -Dscore.reportOnly="$REPORT_ONLY" \
  --rerun-tasks \
  --info >"$TMP_LOG" 2>&1
STATUS=$?
set -e

if grep -q "=== BLOCKER SCORE REPORT ===" "$TMP_LOG"; then
  awk '/=== BLOCKER SCORE REPORT ===/,/=== END SCORE REPORT ===/' "$TMP_LOG"
else
  tail -n 60 "$TMP_LOG"
fi
exit "$STATUS"
