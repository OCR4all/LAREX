#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_PARENT="${1:-${TMPDIR:-/tmp}}"

fail() {
  echo "$*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    fail "sha256sum or shasum is required"
  fi
}

for command in bash jq unzip zip; do
  command -v "$command" >/dev/null 2>&1 || fail "$command is required"
done

mkdir -p "$TEMP_PARENT"
TEST_ROOT="$(mktemp -d "$TEMP_PARENT/larex-release-artifacts.XXXXXX")"
trap 'rm -rf "$TEST_ROOT"' EXIT

VERSION="1.0.0"
ARCHIVE_NAME="larex-deployment-$VERSION.zip"
ARCHIVE_PATH="$TEST_ROOT/$ARCHIVE_NAME"
CHECKSUM_PATH="$ARCHIVE_PATH.sha256"
EXTRACTED_ROOT="$TEST_ROOT/extracted"
DIGEST="sha256:$(printf '0%.0s' {1..64})"
COMMIT="0123456789abcdef0123456789abcdef01234567"

bash "$ROOT_DIR/scripts/build-deployment-package.sh" "$VERSION" "$TEST_ROOT" >/dev/null

read -r EXPECTED_ARCHIVE_SHA256 CHECKSUM_ARCHIVE < "$CHECKSUM_PATH"
[[ "$CHECKSUM_ARCHIVE" == "$ARCHIVE_NAME" ]] \
  || fail "External checksum names the wrong deployment artifact"
[[ "$(sha256_file "$ARCHIVE_PATH")" == "$EXPECTED_ARCHIVE_SHA256" ]] \
  || fail "External deployment artifact checksum failed"

mkdir -p "$EXTRACTED_ROOT"
unzip -q "$ARCHIVE_PATH" -d "$EXTRACTED_ROOT"
BUNDLE_ROOT="$EXTRACTED_ROOT/larex-deployment-$VERSION"
[[ -f "$BUNDLE_ROOT/SHA256SUMS" ]] || fail "Internal checksum manifest is missing"

CHECKED_FILES=0
while read -r expected relative_path; do
  [[ "$relative_path" != /* && "$relative_path" != ../* && "$relative_path" != */../* ]] \
    || fail "Unsafe internal checksum path: $relative_path"
  [[ -f "$BUNDLE_ROOT/$relative_path" ]] \
    || fail "Internal checksum references a missing file: $relative_path"
  [[ "$(sha256_file "$BUNDLE_ROOT/$relative_path")" == "$expected" ]] \
    || fail "Internal checksum failed: $relative_path"
  CHECKED_FILES=$((CHECKED_FILES + 1))
done < "$BUNDLE_ROOT/SHA256SUMS"

ACTUAL_FILES="$(find "$BUNDLE_ROOT" -type f | wc -l | tr -d '[:space:]')"
[[ "$ACTUAL_FILES" -eq $((CHECKED_FILES + 1)) ]] \
  || fail "Internal checksum manifest does not cover every bundle file"
[[ -z "$(find "$BUNDLE_ROOT" -type l -print -quit)" ]] \
  || fail "Deployment bundle contains a symlink"

bash "$ROOT_DIR/scripts/build-release-manifest.sh" \
  "$VERSION" \
  "$TEST_ROOT" \
  ocr4all/larex \
  "$COMMIT" \
  "$DIGEST" \
  "$DIGEST" \
  "$DIGEST" \
  >/dev/null

jq -e \
  --arg commit "$COMMIT" \
  --arg archiveSha256 "$EXPECTED_ARCHIVE_SHA256" \
  '.schemaVersion == 1
    and .source.commit == $commit
    and .deploymentBundle.sha256 == $archiveSha256
    and (.images.backend | startswith("ghcr.io/ocr4all/larex/backend@sha256:"))
    and (.images.frontend | startswith("ghcr.io/ocr4all/larex/frontend@sha256:"))
    and (.images.docs | startswith("ghcr.io/ocr4all/larex/docs@sha256:"))' \
  "$TEST_ROOT/larex-release-$VERSION.json" >/dev/null

printf 'tampered\n' >> "$ARCHIVE_PATH"
if bash "$ROOT_DIR/scripts/build-release-manifest.sh" \
  "$VERSION" \
  "$TEST_ROOT" \
  ocr4all/larex \
  "$COMMIT" \
  "$DIGEST" \
  "$DIGEST" \
  "$DIGEST" \
  >/dev/null 2>&1; then
  fail "Release manifest accepted a deployment bundle with a mismatched checksum"
fi

echo "Release artifact validation passed"
