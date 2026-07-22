#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-$(tr -d '[:space:]' < "$ROOT_DIR/VERSION")}"
OUTPUT_DIR="${2:-$ROOT_DIR/dist}"
FILE_LIST="$ROOT_DIR/scripts/deployment-bundle-files.txt"

if [[ ! "$VERSION" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid bundle version: $VERSION" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
STAGING_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGING_DIR"' EXIT

BUNDLE_NAME="larex-deployment-$VERSION"
BUNDLE_ROOT="$STAGING_DIR/$BUNDLE_NAME"
mkdir -p "$BUNDLE_ROOT"

while IFS= read -r relative_path || [[ -n "$relative_path" ]]; do
  [[ -z "$relative_path" || "$relative_path" == \#* ]] && continue
  mkdir -p "$BUNDLE_ROOT/$(dirname "$relative_path")"
  cp "$ROOT_DIR/$relative_path" "$BUNDLE_ROOT/$relative_path"
done < "$FILE_LIST"

printf '%s\n' "$VERSION" > "$BUNDLE_ROOT/VERSION"

(
  cd "$STAGING_DIR"
  zip -q -r "$OUTPUT_DIR/$BUNDLE_NAME.zip" "$BUNDLE_NAME"
)

echo "$OUTPUT_DIR/$BUNDLE_NAME.zip"
