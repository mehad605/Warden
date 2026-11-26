#!/usr/bin/env bash
set -euo pipefail

# Verifies a built release APK before publishing (S7.4):
#   1. Valid APK signature (`apksigner verify`)
#   2. APK size within budget (default 2.5 MiB = 2,621,440 bytes)
#   3. Expected applicationId, versionName, and versionCode (if passed)
#   4. Embedded baseline profile (`assets/dexopt/baseline.prof`)
#   5. Zero native `.so` libraries

APK_PATH="${1:?Usage: verify_release_apk.sh <apk-path> [expected-version-name] [expected-version-code]}"
EXPECTED_VERSION_NAME="${2:-}"
EXPECTED_VERSION_CODE="${3:-}"
MAX_APK_BYTES="${MAX_APK_BYTES:-2621440}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: APK not found at $APK_PATH" >&2
  exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"

find_sdk_tool() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
    return 0
  fi
  local found
  found=$(find "$SDK_ROOT/build-tools" "$SDK_ROOT/cmdline-tools" -type f -name "$name" 2>/dev/null | sort -V | tail -n 1 || true)
  if [[ -n "$found" && -x "$found" ]]; then
    echo "$found"
    return 0
  fi
  echo "ERROR: Required Android SDK tool '$name' not found under $SDK_ROOT" >&2
  exit 1
}

APKSIGNER="$(find_sdk_tool apksigner)"
AAPT2="$(find_sdk_tool aapt2)"

echo "==> 1. Verifying APK signature with $APKSIGNER"
"$APKSIGNER" verify --verbose --print-certs "$APK_PATH"

echo "==> 2. Checking APK size budget (max ${MAX_APK_BYTES} bytes)"
APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH")
echo "APK size: ${APK_SIZE} bytes"
if (( APK_SIZE > MAX_APK_BYTES )); then
  echo "ERROR: APK size ${APK_SIZE} exceeds budget ${MAX_APK_BYTES} bytes" >&2
  exit 1
fi

echo "==> 3. Checking manifest metadata with $AAPT2"
BADGING="$("$AAPT2" dump badging "$APK_PATH")"
PKG_LINE="$(printf '%s\n' "$BADGING" | grep '^package:' | head -n 1)"
echo "$PKG_LINE"
ACTUAL_PKG=$(printf '%s\n' "$PKG_LINE" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
ACTUAL_CODE=$(printf '%s\n' "$PKG_LINE" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")
ACTUAL_NAME=$(printf '%s\n' "$PKG_LINE" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")

if [[ "$ACTUAL_PKG" != "com.warden.app" ]]; then
  echo "ERROR: Unexpected applicationId '$ACTUAL_PKG' (expected com.warden.app)" >&2
  exit 1
fi
if [[ -n "$EXPECTED_VERSION_NAME" && "$ACTUAL_NAME" != "$EXPECTED_VERSION_NAME" ]]; then
  echo "ERROR: versionName '$ACTUAL_NAME' != expected '$EXPECTED_VERSION_NAME'" >&2
  exit 1
fi
if [[ -n "$EXPECTED_VERSION_CODE" && "$ACTUAL_CODE" != "$EXPECTED_VERSION_CODE" ]]; then
  echo "ERROR: versionCode '$ACTUAL_CODE' != expected '$EXPECTED_VERSION_CODE'" >&2
  exit 1
fi

echo "==> 4. Checking embedded baseline profile and zero native .so libraries"
ZIP_LISTING=$(unzip -Z1 "$APK_PATH")
if ! echo "$ZIP_LISTING" | grep -qx 'assets/dexopt/baseline.prof'; then
  echo "ERROR: Missing assets/dexopt/baseline.prof inside APK" >&2
  exit 1
fi
SO_FILES=$(echo "$ZIP_LISTING" | grep '\.so$' || true)
if [[ -n "$SO_FILES" ]]; then
  echo "ERROR: Unexpected native .so libraries found inside APK:" >&2
  echo "$SO_FILES" >&2
  exit 1
fi

echo "PASS: $APK_PATH verified (pkg=$ACTUAL_PKG, versionName=$ACTUAL_NAME, versionCode=$ACTUAL_CODE, size=${APK_SIZE}B)"
