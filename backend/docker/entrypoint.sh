#!/bin/sh
set -eu

APP_USER="${LAREX_APP_USER:-appuser}"
APP_GROUP="${LAREX_APP_GROUP:-appgroup}"
UPLOAD_ROOT="${LAREX_UPLOAD_DIR:-/mnt/data/uploads}"
UPLOAD_TEMP="${LAREX_UPLOAD_TEMP_DIR:-${UPLOAD_ROOT}/temp}"
PROJECT_EXPORT_ROOT="${LAREX_PROJECT_EXPORT_DIR:-/mnt/data/export-artifacts}"
UPLOAD_OWNER="${LAREX_UPLOAD_OWNER:-${APP_USER}:${APP_GROUP}}"
UPLOAD_MODE="${LAREX_UPLOAD_MODE:-755}"
SKIP_APP_START="${LAREX_SKIP_APP_START:-false}"

log() {
  printf '[larex-entrypoint] %s\n' "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

ensure_directory() {
  target="$1"
  label="$2"

  if ! mkdir -p "$target"; then
    fail "Unable to create ${label} directory at ${target}. Check for read-only filesystem, insufficient permissions, or insufficient disk space."
  fi

  if [ ! -d "$target" ]; then
    fail "${label} path exists but is not a directory: ${target}"
  fi

  if ! chmod "$UPLOAD_MODE" "$target"; then
    fail "Unable to apply chmod ${UPLOAD_MODE} to ${label} directory ${target}"
  fi

  if [ "$(id -u)" -eq 0 ]; then
    if ! chown "$UPLOAD_OWNER" "$target"; then
      fail "Unable to set ownership ${UPLOAD_OWNER} on ${label} directory ${target}"
    fi
  else
    log "Running as non-root user; skipping chown for ${label} directory ${target}"
  fi
}

log "Preparing upload directories"
ensure_directory "$UPLOAD_ROOT" "upload root"
ensure_directory "$UPLOAD_TEMP" "upload temp"
ensure_directory "$PROJECT_EXPORT_ROOT" "project export artifact"

if [ "$SKIP_APP_START" = "true" ]; then
  log "Skipping app startup (LAREX_SKIP_APP_START=true)"
  exit 0
fi

if [ "$(id -u)" -eq 0 ]; then
  if command -v runuser >/dev/null 2>&1; then
    exec runuser -u "$APP_USER" -- java -jar /app/app.jar
  fi

  if command -v su >/dev/null 2>&1; then
    exec su -s /bin/sh "$APP_USER" -c 'exec java -jar /app/app.jar'
  fi

  fail "Cannot drop privileges to ${APP_USER}; neither runuser nor su is available"
fi

exec java -jar /app/app.jar
