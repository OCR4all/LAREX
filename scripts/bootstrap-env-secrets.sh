#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  scripts/bootstrap-env-secrets.sh prod
  scripts/bootstrap-env-secrets.sh prod-local
  scripts/bootstrap-env-secrets.sh actions

Creates the corresponding top-level env file from the example template if needed and
fills in secret values for keys that are missing or still using placeholder defaults.

Modes:
  prod        -> .env.prod from deployment/env/.env.prod.example
  prod-local  -> .env.prod.local from deployment/env/.env.prod.local.example
  actions     -> .env.actions from deployment/env/.env.actions.example
EOF
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

MODE="$1"
case "$MODE" in
  prod)
    TEMPLATE="$ROOT_DIR/deployment/env/.env.prod.example"
    OUTPUT="$ROOT_DIR/.env.prod"
    ;;
  prod-local)
    TEMPLATE="$ROOT_DIR/deployment/env/.env.prod.local.example"
    OUTPUT="$ROOT_DIR/.env.prod.local"
    ;;
  actions)
    TEMPLATE="$ROOT_DIR/deployment/env/.env.actions.example"
    OUTPUT="$ROOT_DIR/.env.actions"
    ;;
  *)
    usage
    exit 1
    ;;
esac

if [[ ! -f "$TEMPLATE" ]]; then
  echo "Template not found: $TEMPLATE" >&2
  exit 1
fi

if [[ ! -f "$OUTPUT" ]]; then
  cp "$TEMPLATE" "$OUTPUT"
  echo "Created $OUTPUT from template."
fi

random_secret() {
  python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(48))
PY
}

random_password() {
  python3 - <<'PY'
import secrets
alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
print("".join(secrets.choice(alphabet) for _ in range(24)))
PY
}

current_value() {
  local key="$1"
  python3 - "$OUTPUT" "$key" <<'PY'
import sys
path, key = sys.argv[1:3]
value = ""
with open(path, "r", encoding="utf-8") as handle:
    for raw in handle:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        current_key, current_value = line.split("=", 1)
        if current_key == key:
            value = current_value
            break
print(value)
PY
}

write_value() {
  local key="$1"
  local value="$2"
  python3 - "$OUTPUT" "$key" "$value" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
key = sys.argv[2]
value = sys.argv[3]

lines = path.read_text(encoding="utf-8").splitlines()
updated = False

for index, raw in enumerate(lines):
    if raw.startswith(f"{key}="):
        lines[index] = f"{key}={value}"
        updated = True
        break

if not updated:
    lines.append(f"{key}={value}")

path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

ensure_key() {
  local key="$1"
  local generated_value="$2"
  local current
  current="$(current_value "$key")"

  if [[ -z "$current" ]]; then
    write_value "$key" "$generated_value"
    echo "Added $key"
    return
  fi

  case "$current" in
    change-me|PLEASE_CHANGE_IN_PRODUCTION|replace-with-random-32-plus-character-secret|change-me-initial-admin|larex_dev_pw|admin)
      write_value "$key" "$generated_value"
      echo "Seeded $key"
      ;;
    *)
      echo "Keeping existing $key"
      ;;
  esac
}

if [[ "$MODE" == "actions" ]]; then
  ensure_key "LAREX_ACTION_ENDPOINT_SECRET_KRAKEN_SEGMENTATION_V1" "$(random_secret)"
  echo
  echo "Seeded secrets in $OUTPUT"
  echo "Review processor image, resource, and callback settings before deployment."
  exit 0
fi

ensure_key "POSTGRES_PASSWORD" "$(random_password)"
ensure_key "KEYCLOAK_POSTGRES_PASSWORD" "$(random_password)"
ensure_key "KEYCLOAK_ADMIN_PASSWORD" "$(random_password)"
ensure_key "KEYCLOAK_ADMIN_CLIENT_SECRET" "$(random_secret)"
ensure_key "NUXT_SESSION_PASSWORD" "$(random_secret)"
ensure_key "NUXT_OAUTH_KEYCLOAK_CLIENT_SECRET" "$(random_secret)"
ensure_key "LAREX_INITIAL_ADMIN_TEMP_PASSWORD" "$(random_password)"
ensure_key "LAREX_NOTIFICATIONS_BRIDGE_SECRET" "$(random_secret)"
ensure_key "NUXT_NOTIFICATION_BRIDGE_SECRET" "$(random_secret)"
ensure_key "NUXT_COLLABORATION_SECRET" "$(random_secret)"
ensure_key "LAREX_ACTION_ENDPOINT_SECRET_ENCRYPTION_KEY" "$(random_secret)"

echo
echo "Seeded secrets in $OUTPUT"
echo "Review non-secret host/domain values before running Docker Compose."
