#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-$(tr -d '[:space:]' < "$ROOT_DIR/VERSION")}"
OUTPUT_DIR="${2:-$ROOT_DIR/dist}"
FILE_LIST="$ROOT_DIR/scripts/deployment-bundle-files.txt"

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

if [[ ! "$VERSION" =~ ^[A-Za-z0-9._-]+$ ]]; then
  fail "Invalid bundle version: $VERSION"
fi

mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
STAGING_DIR="$(mktemp -d)"

BUNDLE_NAME="larex-deployment-$VERSION"
BUNDLE_ROOT="$STAGING_DIR/$BUNDLE_NAME"
ARCHIVE_PATH="$OUTPUT_DIR/$BUNDLE_NAME.zip"
ARCHIVE_CHECKSUM_PATH="$ARCHIVE_PATH.sha256"
TEMP_ARCHIVE="$OUTPUT_DIR/.$BUNDLE_NAME.zip.$$"

cleanup() {
  rm -rf "$STAGING_DIR"
  rm -f "$TEMP_ARCHIVE"
}
trap cleanup EXIT

mkdir -p "$BUNDLE_ROOT"

SEEN_FILES=$'\n'
BUNDLE_FILES=()
while IFS= read -r relative_path || [[ -n "$relative_path" ]]; do
  [[ -z "$relative_path" || "$relative_path" == \#* ]] && continue

  case "$relative_path" in
    /*|.|..|../*|*/..|*/../*)
      fail "Unsafe deployment bundle path: $relative_path"
      ;;
  esac
  if [[ "$SEEN_FILES" == *$'\n'"$relative_path"$'\n'* ]]; then
    fail "Duplicate deployment bundle path: $relative_path"
  fi
  [[ -f "$ROOT_DIR/$relative_path" ]] || fail "Missing deployment bundle file: $relative_path"
  [[ ! -L "$ROOT_DIR/$relative_path" ]] || fail "Symlinks are not allowed in the deployment bundle: $relative_path"

  SEEN_FILES+="$relative_path"$'\n'
  BUNDLE_FILES+=("$relative_path")
  mkdir -p "$BUNDLE_ROOT/$(dirname "$relative_path")"
  cp "$ROOT_DIR/$relative_path" "$BUNDLE_ROOT/$relative_path"
done < "$FILE_LIST"

[[ ${#BUNDLE_FILES[@]} -gt 0 ]] || fail "Deployment bundle manifest is empty"

printf '%s\n' "$VERSION" > "$BUNDLE_ROOT/VERSION"

CHECKSUM_MANIFEST="$BUNDLE_ROOT/SHA256SUMS"
: > "$CHECKSUM_MANIFEST"
for relative_path in "${BUNDLE_FILES[@]}"; do
  printf '%s  %s\n' \
    "$(sha256_file "$BUNDLE_ROOT/$relative_path")" \
    "$relative_path" \
    >> "$CHECKSUM_MANIFEST"
done

(
  cd "$STAGING_DIR"
  zip -q -r "$TEMP_ARCHIVE" "$BUNDLE_NAME"
)

mv -f "$TEMP_ARCHIVE" "$ARCHIVE_PATH"
printf '%s  %s\n' \
  "$(sha256_file "$ARCHIVE_PATH")" \
  "$(basename "$ARCHIVE_PATH")" \
  > "$ARCHIVE_CHECKSUM_PATH"

echo "$ARCHIVE_PATH"
echo "$ARCHIVE_CHECKSUM_PATH"
