#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THEME_PATH="${1:-$ROOT_DIR/config/keycloak/theme.jar}"

command -v gh >/dev/null 2>&1 || { echo 'Install the GitHub CLI (gh) to verify release provenance.' >&2; exit 1; }
[[ -f "$THEME_PATH" ]] || { echo "Theme not found: $THEME_PATH" >&2; exit 1; }

# Fail closed. Checksums alone cannot establish who published the theme.
gh attestation verify "$THEME_PATH" \
  --repo ocr4all/larex \
  --signer-workflow ocr4all/larex/.github/workflows/release.yml \
  --source-ref refs/heads/main

echo 'Verified LAREX release theme provenance. Review the reported source commit against the intended release.'
