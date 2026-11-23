#!/usr/bin/env bash
set -euo pipefail

MIGRATIONS=backend/src/main/resources/db/migration

release=$(git tag --list 'v*' --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1 || true)
if [[ -z "$release" ]]; then
  echo "No stable release tag found; fetch tags before running this check." >&2
  exit 1
fi

changed=$(git diff --no-renames --name-status --diff-filter=DMT "$release" HEAD -- "$MIGRATIONS")
if [[ -n "$changed" ]]; then
  echo "Migrations released in $release must not change. Add a new migration instead:" >&2
  echo "$changed" >&2
  exit 1
fi

echo "Migrations released in $release are unchanged."
