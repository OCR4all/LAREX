#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
THEME_PATH="${1:-$ROOT_DIR/config/keycloak/theme.jar}"
TEST_DIRECTORY="$(mktemp -d)"
CONTAINER_ID=''
cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then docker rm -f "$CONTAINER_ID" >/dev/null; fi
  rm -rf "$TEST_DIRECTORY"
}
trap cleanup EXIT

cat > "$TEST_DIRECTORY/realm.json" <<'JSON'
{
  "realm": "larex-theme-test", "enabled": true,
  "loginTheme": "keycloakify-larex", "emailTheme": "keycloakify-larex",
  "clients": [{"clientId": "theme-test", "publicClient": true,
    "redirectUris": ["http://localhost/callback"], "standardFlowEnabled": true}]
}
JSON
chmod 755 "$TEST_DIRECTORY"
chmod 644 "$TEST_DIRECTORY/realm.json"
CONTAINER_ID="$(docker run --detach --rm \
  --publish 127.0.0.1::8080 \
  --mount "type=bind,source=$THEME_PATH,target=/opt/keycloak/providers/larex_theme.jar,readonly" \
  --mount "type=bind,source=$TEST_DIRECTORY/realm.json,target=/opt/keycloak/data/import/realm.json,readonly" \
  quay.io/keycloak/keycloak:26.7.4 \
  start --db=dev-file --http-enabled=true --hostname=http://localhost --import-realm)"
PORT="$(docker port "$CONTAINER_ID" 8080/tcp | awk -F: '{print $NF}')"
URL="http://127.0.0.1:$PORT/realms/larex-theme-test/protocol/openid-connect/auth?client_id=theme-test&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&response_type=code&scope=openid"

for attempt in {1..90}; do
  if curl --silent --fail "$URL" -o "$TEST_DIRECTORY/login.html"; then break; fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER_ID" 2>/dev/null || true)" != true ]]; then break; fi
  sleep 2
done
if [[ ! -f "$TEST_DIRECTORY/login.html" ]] \
  || ! grep -q '/keycloakify-larex/' "$TEST_DIRECTORY/login.html"; then
  docker logs "$CONTAINER_ID" >&2
  echo 'Official Keycloak did not render the mounted LAREX login theme' >&2
  exit 1
fi
echo 'Official Keycloak rendered the mounted LAREX login theme with production start.'
