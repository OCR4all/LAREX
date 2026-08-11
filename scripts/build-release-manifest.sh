#!/usr/bin/env bash

set -euo pipefail

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

[[ $# -eq 7 ]] || fail \
  "Usage: $0 <version> <output-dir> <repository> <commit> <backend-digest> <frontend-digest> <docs-digest>"

VERSION="$1"
OUTPUT_DIR="$2"
REPOSITORY="$3"
COMMIT="$4"
BACKEND_DIGEST="$5"
FRONTEND_DIGEST="$6"
DOCS_DIGEST="$7"

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || fail "Release version must be a stable semantic version: $VERSION"
[[ "$REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] \
  || fail "Invalid GitHub repository: $REPOSITORY"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] \
  || fail "Invalid source commit: $COMMIT"

for digest in "$BACKEND_DIGEST" "$FRONTEND_DIGEST" "$DOCS_DIGEST"; do
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "Invalid container image digest: $digest"
done

command -v jq >/dev/null 2>&1 || fail "jq is required"
[[ -d "$OUTPUT_DIR" ]] || fail "Release output directory does not exist: $OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

ARCHIVE="larex-deployment-$VERSION.zip"
ARCHIVE_PATH="$OUTPUT_DIR/$ARCHIVE"
ARCHIVE_CHECKSUM_PATH="$ARCHIVE_PATH.sha256"
MANIFEST_PATH="$OUTPUT_DIR/larex-release-$VERSION.json"

[[ -f "$ARCHIVE_PATH" ]] || fail "Deployment bundle is missing: $ARCHIVE_PATH"
[[ -f "$ARCHIVE_CHECKSUM_PATH" ]] || fail "Deployment bundle checksum is missing: $ARCHIVE_CHECKSUM_PATH"

read -r RECORDED_SHA256 RECORDED_ARCHIVE < "$ARCHIVE_CHECKSUM_PATH"
ACTUAL_SHA256="$(sha256_file "$ARCHIVE_PATH")"
[[ "$RECORDED_ARCHIVE" == "$ARCHIVE" ]] \
  || fail "Deployment checksum names an unexpected artifact: $RECORDED_ARCHIVE"
[[ "$RECORDED_SHA256" == "$ACTUAL_SHA256" ]] \
  || fail "Deployment bundle checksum does not match: $ARCHIVE"

TEMP_MANIFEST="$(mktemp "$OUTPUT_DIR/.larex-release-manifest.XXXXXX")"
trap 'rm -f "$TEMP_MANIFEST"' EXIT

jq -n \
  --arg version "$VERSION" \
  --arg repository "$REPOSITORY" \
  --arg commit "$COMMIT" \
  --arg archive "$ARCHIVE" \
  --arg archiveSha256 "$ACTUAL_SHA256" \
  --arg backend "ghcr.io/ocr4all/larex/backend@$BACKEND_DIGEST" \
  --arg frontend "ghcr.io/ocr4all/larex/frontend@$FRONTEND_DIGEST" \
  --arg docs "ghcr.io/ocr4all/larex/docs@$DOCS_DIGEST" \
  '{
    schemaVersion: 1,
    version: $version,
    source: {
      repository: $repository,
      commit: $commit
    },
    deploymentBundle: {
      file: $archive,
      sha256: $archiveSha256
    },
    images: {
      backend: $backend,
      frontend: $frontend,
      docs: $docs
    },
    security: {
      imageSignatures: "sigstore-keyless",
      provenance: "slsa-mode-max",
      sbom: "spdx-oci-attestation"
    }
  }' > "$TEMP_MANIFEST"

jq -e \
  --arg version "$VERSION" \
  --arg archive "$ARCHIVE" \
  --arg archiveSha256 "$ACTUAL_SHA256" \
  '.schemaVersion == 1
    and .version == $version
    and .deploymentBundle.file == $archive
    and .deploymentBundle.sha256 == $archiveSha256
    and (.images.backend | contains("@sha256:"))
    and (.images.frontend | contains("@sha256:"))
    and (.images.docs | contains("@sha256:"))' \
  "$TEMP_MANIFEST" >/dev/null

mv -f "$TEMP_MANIFEST" "$MANIFEST_PATH"
echo "$MANIFEST_PATH"
