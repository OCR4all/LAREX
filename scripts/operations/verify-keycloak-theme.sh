#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
THEME_PATH="${1:-$ROOT_DIR/config/keycloak/theme.jar}"
METADATA_PATH="$(dirname "$THEME_PATH")/theme.json"

command -v gh >/dev/null 2>&1 || { echo 'Install the GitHub CLI (gh) to verify release provenance.' >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo 'Install jq to read the theme metadata.' >&2; exit 1; }
[[ -f "$THEME_PATH" ]] || { echo "Theme not found: $THEME_PATH" >&2; exit 1; }
[[ -f "$METADATA_PATH" ]] || { echo "Theme metadata not found: $METADATA_PATH" >&2; exit 1; }

VERSION="$(jq -r '.version // empty' "$METADATA_PATH")"
COMMIT="$(jq -r '.source.commit // empty' "$METADATA_PATH")"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$ ]] || { echo "Theme metadata does not name a release version: ${VERSION:-none}" >&2; exit 1; }
[[ -n "$COMMIT" ]] || { echo 'Theme metadata does not name a source commit.' >&2; exit 1; }

# Fail closed. Checksums alone cannot establish who published the theme.
gh attestation verify "$THEME_PATH" \
  --repo ocr4all/larex \
  --signer-workflow ocr4all/larex/.github/workflows/release.yml \
  --source-ref "refs/tags/v$VERSION" \
  --source-digest "$COMMIT"

echo "Verified LAREX $VERSION release theme provenance (source commit $COMMIT)."
