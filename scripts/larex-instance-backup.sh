#!/usr/bin/env bash

set -Eeuo pipefail

FORMAT_NAME="larex-instance-backup"
FORMAT_VERSION="1.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

COMMAND=""
ARCHIVE=""
OUTPUT="./backups"
PROFILE="prod"
AUTH_MODE=""
INCLUDE_CONFIG=false
ASSUME_YES=false
FORCE_INCOMPATIBLE=false
CUSTOM_COMPOSE=false
ENV_FILE=""
COMPOSE_FILES=()
COMPOSE_ARGS=()
TEMP_DIR=""
VERIFIED_DIR=""
RESTART_ON_EXIT=false
STOPPED_SERVICES=()

log() {
  printf '%s\n' "$*" >&2
}

fail() {
  log "Error: $*"
  exit 1
}

usage() {
  cat <<'EOF'
Create, verify, preflight, and restore a complete self-hosted LAREX snapshot.

Usage:
  larex-instance-backup.sh create [options]
  larex-instance-backup.sh verify ARCHIVE
  larex-instance-backup.sh preflight ARCHIVE [options]
  larex-instance-backup.sh restore ARCHIVE --yes [options]

Options:
  --output PATH             Output directory or .tar.gz path (create only)
  --profile NAME            prod, prod-local, or external-keycloak (default: prod)
  --env-file PATH           Override the Compose environment file
  --compose-file PATH       Override Compose files; repeat for multiple files
  --auth-mode MODE          bundled or external
  --include-config          Include Compose/env configuration (contains secrets)
  --force-incompatible      Permit a LAREX major-version mismatch
  --yes                     Confirm the destructive restore operation
  -h, --help                Show this help

Examples:
  ./scripts/larex-instance-backup.sh create --profile prod
  ./scripts/larex-instance-backup.sh verify backups/larex-instance-*.tar.gz
  ./scripts/larex-instance-backup.sh preflight BACKUP --profile prod
  ./scripts/larex-instance-backup.sh restore BACKUP --profile prod --yes

The default snapshot deliberately excludes environment files and secrets. Use
--include-config only when the archive will be stored with secret-grade controls.
External identity-provider state is never captured by LAREX.
EOF
}

cleanup() {
  local exit_code=$?
  if [[ "${RESTART_ON_EXIT}" == true && ${#STOPPED_SERVICES[@]} -gt 0 ]]; then
    log "Restarting services paused for the snapshot..."
    compose start "${STOPPED_SERVICES[@]}" >/dev/null || true
  fi
  if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
    rm -rf "${TEMP_DIR}"
  fi
  exit "${exit_code}"
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    create|verify|preflight|restore)
      [[ -z "${COMMAND}" ]] || fail "Only one command may be supplied"
      COMMAND="$1"
      shift
      if [[ "${COMMAND}" != "create" && $# -gt 0 && "$1" != --* ]]; then
        ARCHIVE="$1"
        shift
      fi
      ;;
    --output)
      [[ $# -ge 2 ]] || fail "--output requires a value"
      OUTPUT="$2"
      shift 2
      ;;
    --profile)
      [[ $# -ge 2 ]] || fail "--profile requires a value"
      PROFILE="$2"
      shift 2
      ;;
    --env-file)
      [[ $# -ge 2 ]] || fail "--env-file requires a value"
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      [[ $# -ge 2 ]] || fail "--compose-file requires a value"
      if [[ "${CUSTOM_COMPOSE}" == false ]]; then
        COMPOSE_FILES=()
        CUSTOM_COMPOSE=true
      fi
      COMPOSE_FILES+=("$2")
      shift 2
      ;;
    --auth-mode)
      [[ $# -ge 2 ]] || fail "--auth-mode requires a value"
      AUTH_MODE="$2"
      shift 2
      ;;
    --include-config)
      INCLUDE_CONFIG=true
      shift
      ;;
    --force-incompatible)
      FORCE_INCOMPATIBLE=true
      shift
      ;;
    --yes)
      ASSUME_YES=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -n "${COMMAND}" && "${COMMAND}" != "create" && -z "${ARCHIVE}" ]]; then
        ARCHIVE="$1"
        shift
      else
        fail "Unknown argument: $1"
      fi
      ;;
  esac
done

[[ -n "${COMMAND}" ]] || {
  usage
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

absolute_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s\n' "${path}"
  else
    printf '%s/%s\n' "$(pwd)" "${path#./}"
  fi
}

configure_compose() {
  if [[ "${CUSTOM_COMPOSE}" == true ]]; then
    [[ -n "${AUTH_MODE}" ]] || fail "Custom Compose files require --auth-mode"
  else
  case "${PROFILE}" in
    prod)
      [[ -n "${ENV_FILE}" ]] || ENV_FILE="${REPO_ROOT}/.env.prod"
      if [[ "${CUSTOM_COMPOSE}" == false ]]; then
        COMPOSE_FILES=(
          "${REPO_ROOT}/compose.prod.base.yaml"
          "${REPO_ROOT}/compose.prod.auth.bundled-keycloak.yaml"
          "${REPO_ROOT}/compose.prod.publish.localhost.yaml"
        )
      fi
      [[ -n "${AUTH_MODE}" ]] || AUTH_MODE="bundled"
      ;;
    prod-local)
      [[ -n "${ENV_FILE}" ]] || ENV_FILE="${REPO_ROOT}/.env.prod.local"
      if [[ "${CUSTOM_COMPOSE}" == false ]]; then
        COMPOSE_FILES=(
          "${REPO_ROOT}/compose.prod.base.yaml"
          "${REPO_ROOT}/compose.prod.auth.bundled-keycloak.yaml"
          "${REPO_ROOT}/compose.prod.local.yaml"
        )
      fi
      [[ -n "${AUTH_MODE}" ]] || AUTH_MODE="bundled"
      ;;
    external-keycloak)
      [[ -n "${ENV_FILE}" ]] || ENV_FILE="${REPO_ROOT}/.env.prod"
      if [[ "${CUSTOM_COMPOSE}" == false ]]; then
        COMPOSE_FILES=(
          "${REPO_ROOT}/compose.prod.base.yaml"
          "${REPO_ROOT}/compose.prod.auth.external-keycloak.yaml"
          "${REPO_ROOT}/compose.prod.publish.localhost.yaml"
        )
      fi
      [[ -n "${AUTH_MODE}" ]] || AUTH_MODE="external"
      ;;
    *)
      fail "Unknown profile: ${PROFILE}"
      ;;
  esac
  fi

  [[ "${AUTH_MODE}" == "bundled" || "${AUTH_MODE}" == "external" ]] \
    || fail "--auth-mode must be bundled or external"
  [[ ${#COMPOSE_FILES[@]} -gt 0 ]] || fail "At least one Compose file is required"

  COMPOSE_ARGS=()
  if [[ -n "${ENV_FILE}" ]]; then
    [[ -f "${ENV_FILE}" ]] || fail "Environment file not found: ${ENV_FILE}"
    COMPOSE_ARGS+=(--env-file "${ENV_FILE}")
  fi
  local file
  for file in "${COMPOSE_FILES[@]}"; do
    [[ -f "${file}" ]] || fail "Compose file not found: ${file}"
    COMPOSE_ARGS+=(-f "${file}")
  done
}

compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}

service_exists() {
  compose config --services | grep -Fxq "$1"
}

service_container() {
  compose ps -a -q "$1" | head -n 1
}

service_running() {
  compose ps --status running --services | grep -Fxq "$1"
}

ensure_container() {
  local service="$1"
  if [[ -z "$(service_container "${service}")" ]]; then
    compose create --no-deps "${service}" >/dev/null
  fi
}

pause_mutating_services() {
  local candidates=(frontend app)
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    candidates=(frontend app keycloak)
  fi

  local service
  for service in "${candidates[@]}"; do
    if service_exists "${service}" && service_running "${service}"; then
      STOPPED_SERVICES+=("${service}")
    fi
  done
  if [[ ${#STOPPED_SERVICES[@]} -gt 0 ]]; then
    log "Pausing mutation-capable services: ${STOPPED_SERVICES[*]}"
    compose stop -t 60 "${STOPPED_SERVICES[@]}" >/dev/null
  fi
}

database_value() {
  local service="$1"
  local sql="$2"
  compose exec -T "${service}" sh -ec \
    'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' \
    sh "${sql}"
}

database_schema_version() {
  local value
  value="$(database_value "$1" \
    "SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1), 'unknown');" \
    2>/dev/null || true)"
  printf '%s\n' "${value:-unknown}"
}

database_server_version() {
  database_value "$1" "SHOW server_version;" | head -n 1
}

dump_database() {
  local service="$1"
  local target="$2"
  log "Dumping database service ${service}..."
  compose exec -T "${service}" sh -ec \
    'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
    >"${target}"
  [[ -s "${target}" ]] || fail "Database dump is empty: ${service}"
}

volume_reference() {
  local service="$1"
  local destination="$2"
  local container
  container="$(service_container "${service}")"
  [[ -n "${container}" ]] || fail "No container exists for service ${service}"
  docker inspect --format \
    "{{range .Mounts}}{{if eq .Destination \"${destination}\"}}{{if eq .Type \"volume\"}}volume:{{.Name}}{{else}}bind:{{.Source}}{{end}}{{end}}{{end}}" \
    "${container}"
}

helper_image() {
  local container
  container="$(service_container postgres)"
  [[ -n "${container}" ]] || fail "No PostgreSQL container is available as a volume helper"
  docker inspect --format '{{.Config.Image}}' "${container}"
}

backup_volume() {
  local service="$1"
  local destination="$2"
  local target="$3"
  local reference
  reference="$(volume_reference "${service}" "${destination}")"
  [[ -n "${reference}" ]] || fail "Service ${service} has no mount at ${destination}"
  local mount_type="${reference%%:*}"
  local mount_source="${reference#*:}"
  log "Archiving ${service}:${destination}..."
  docker run --rm \
    --mount "type=${mount_type},source=${mount_source},target=/snapshot,readonly" \
    --entrypoint tar "$(helper_image)" -C /snapshot -czf - . >"${target}"
  [[ -s "${target}" ]] || fail "Volume archive is empty: ${service}:${destination}"
}

restore_volume() {
  local service="$1"
  local destination="$2"
  local source="$3"
  local reference
  reference="$(volume_reference "${service}" "${destination}")"
  [[ -n "${reference}" ]] || fail "Service ${service} has no mount at ${destination}"
  local mount_type="${reference%%:*}"
  local mount_source="${reference#*:}"
  log "Restoring ${service}:${destination}..."
  docker run --rm -i \
    --mount "type=${mount_type},source=${mount_source},target=/snapshot" \
    --entrypoint sh "$(helper_image)" -ec \
    'find /snapshot -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +; tar -xzf - -C /snapshot' \
    <"${source}"
}

validate_tar_members() {
  local archive="$1"
  local entry
  while IFS= read -r entry; do
    entry="${entry#./}"
    case "${entry}" in
      ""|".") ;;
      /*|../*|*/../*|*/..)
        fail "Unsafe path in archive ${archive}: ${entry}"
        ;;
    esac
  done < <(tar -tzf "${archive}")
}

write_checksums() {
  local directory="$1"
  shift
  local relative
  : >"${directory}/checksums.sha256"
  for relative in "$@"; do
    printf '%s  %s\n' "$(sha256_file "${directory}/${relative}")" "${relative}" \
      >>"${directory}/checksums.sha256"
  done
}

extract_and_verify() {
  local archive="$1"
  [[ -f "${archive}" ]] || fail "Backup archive not found: ${archive}"
  validate_tar_members "${archive}"

  TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/larex-instance-verify.XXXXXX")"
  VERIFIED_DIR="${TEMP_DIR}/snapshot"
  mkdir -p "${VERIFIED_DIR}"
  tar -xzf "${archive}" -C "${VERIFIED_DIR}"
  if find "${VERIFIED_DIR}" -type l | grep -q .; then
    fail "Snapshot contains symbolic links"
  fi

  [[ -f "${VERIFIED_DIR}/manifest.json" ]] || fail "Snapshot manifest is missing"
  [[ -f "${VERIFIED_DIR}/checksums.sha256" ]] || fail "Snapshot checksums are missing"

  local format schema expected relative actual
  local verified_paths="${TEMP_DIR}/verified-paths"
  : >"${verified_paths}"
  format="$(jq -r '.format // empty' "${VERIFIED_DIR}/manifest.json")"
  schema="$(jq -r '.schemaVersion // empty' "${VERIFIED_DIR}/manifest.json")"
  [[ "${format}" == "${FORMAT_NAME}" ]] || fail "Unsupported snapshot format: ${format:-missing}"
  [[ "${schema}" == "${FORMAT_VERSION}" ]] || fail "Unsupported snapshot schema: ${schema:-missing}"

  while read -r expected relative; do
    [[ -n "${expected}" && -n "${relative}" ]] || fail "Malformed checksum entry"
    case "${relative}" in
      /*|../*|*/../*|*/..) fail "Unsafe checksum path: ${relative}" ;;
    esac
    grep -Fxq "${relative}" "${verified_paths}" \
      && fail "Duplicate checksum path: ${relative}"
    printf '%s\n' "${relative}" >>"${verified_paths}"
    [[ -f "${VERIFIED_DIR}/${relative}" ]] || fail "Snapshot component is missing: ${relative}"
    actual="$(sha256_file "${VERIFIED_DIR}/${relative}")"
    [[ "${actual}" == "${expected}" ]] || fail "Checksum mismatch: ${relative}"
  done <"${VERIFIED_DIR}/checksums.sha256"

  local snapshot_file
  while IFS= read -r snapshot_file; do
    relative="${snapshot_file#${VERIFIED_DIR}/}"
    [[ "${relative}" == "checksums.sha256" ]] && continue
    grep -Fxq "${relative}" "${verified_paths}" \
      || fail "Snapshot file is not covered by checksums: ${relative}"
  done < <(find "${VERIFIED_DIR}" -type f | LC_ALL=C sort)

  [[ -f "${VERIFIED_DIR}/data/larex-postgres.dump" ]] \
    || fail "LAREX PostgreSQL dump is missing"
  [[ -f "${VERIFIED_DIR}/data/app-data.tar.gz" ]] \
    || fail "Application data archive is missing"
  validate_tar_members "${VERIFIED_DIR}/data/app-data.tar.gz"

  local archive_auth
  archive_auth="$(jq -r '.authMode' "${VERIFIED_DIR}/manifest.json")"
  if [[ "${archive_auth}" == "bundled" ]]; then
    [[ -f "${VERIFIED_DIR}/data/keycloak-postgres.dump" ]] \
      || fail "Bundled Keycloak database dump is missing"
    [[ -f "${VERIFIED_DIR}/data/keycloak-data.tar.gz" ]] \
      || fail "Bundled Keycloak data archive is missing"
    validate_tar_members "${VERIFIED_DIR}/data/keycloak-data.tar.gz"
  fi
}

print_verification_summary() {
  local manifest="${VERIFIED_DIR}/manifest.json"
  log "Snapshot verified successfully."
  jq -r '"  Created: \(.createdAt)\n  LAREX: \(.larexVersion)\n  Image: \(.backendImage)\n  Schema: \(.database.schemaVersion)\n  PostgreSQL: \(.database.serverVersion)\n  Auth: \(.authMode)\n  Consistency: \(.consistency)"' \
    "${manifest}" >&2
}

create_backup() {
  require_command docker
  configure_compose
  compose config --quiet
  service_exists postgres || fail "Compose model has no postgres service"
  service_exists app || fail "Compose model has no app service"
  service_running postgres || fail "The postgres service must be running"
  ensure_container app
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    service_exists keycloak-postgres || fail "Bundled profile has no keycloak-postgres service"
    service_exists keycloak || fail "Bundled profile has no keycloak service"
    service_running keycloak-postgres || fail "The keycloak-postgres service must be running"
    ensure_container keycloak
  fi

  local timestamp destination
  timestamp="$(date -u +"%Y%m%d-%H%M%S")"
  if [[ "${OUTPUT}" == *.tar.gz ]]; then
    destination="$(absolute_path "${OUTPUT}")"
    mkdir -p "$(dirname "${destination}")"
  else
    mkdir -p "${OUTPUT}"
    destination="$(absolute_path "${OUTPUT}")/larex-instance-${timestamp}.tar.gz"
  fi
  [[ ! -e "${destination}" ]] || fail "Refusing to overwrite existing snapshot: ${destination}"

  TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/larex-instance-create.XXXXXX")"
  local create_temp="${TEMP_DIR}"
  local staging="${create_temp}/snapshot"
  mkdir -p "${staging}/data"
  RESTART_ON_EXIT=true
  pause_mutating_services

  local larex_schema larex_pg_version keycloak_pg_version=""
  larex_schema="$(database_schema_version postgres)"
  larex_pg_version="$(database_server_version postgres)"
  dump_database postgres "${staging}/data/larex-postgres.dump"
  backup_volume app /mnt/data "${staging}/data/app-data.tar.gz"

  local components='["larex-postgres","app-data"]'
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    keycloak_pg_version="$(database_server_version keycloak-postgres)"
    dump_database keycloak-postgres "${staging}/data/keycloak-postgres.dump"
    backup_volume keycloak /opt/keycloak/data "${staging}/data/keycloak-data.tar.gz"
    components='["larex-postgres","app-data","keycloak-postgres","keycloak-data"]'
  fi

  local config_included=false
  if [[ "${INCLUDE_CONFIG}" == true ]]; then
    log "Including deployment configuration; the snapshot now contains secrets."
    mkdir -p "${staging}/configuration"
    [[ -z "${ENV_FILE}" ]] || cp "${ENV_FILE}" "${staging}/configuration/$(basename "${ENV_FILE}")"
    local compose_file
    for compose_file in "${COMPOSE_FILES[@]}"; do
      cp "${compose_file}" "${staging}/configuration/$(basename "${compose_file}")"
    done
    config_included=true
  fi

  local created_at larex_version backend_image keycloak_image=""
  created_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  larex_version="$(tr -d '[:space:]' <"${REPO_ROOT}/VERSION")"
  backend_image="$(docker inspect --format '{{.Config.Image}}' "$(service_container app)")"
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    keycloak_image="$(docker inspect --format '{{.Config.Image}}' "$(service_container keycloak)")"
  fi

  jq -n \
    --arg format "${FORMAT_NAME}" \
    --arg schemaVersion "${FORMAT_VERSION}" \
    --arg createdAt "${created_at}" \
    --arg larexVersion "${larex_version}" \
    --arg backendImage "${backend_image}" \
    --arg authMode "${AUTH_MODE}" \
    --arg consistency "offline" \
    --arg dbSchema "${larex_schema}" \
    --arg dbVersion "${larex_pg_version}" \
    --arg keycloakDbVersion "${keycloak_pg_version}" \
    --arg keycloakImage "${keycloak_image}" \
    --argjson components "${components}" \
    --argjson configurationIncluded "${config_included}" \
    '{
      format: $format,
      schemaVersion: $schemaVersion,
      createdAt: $createdAt,
      larexVersion: $larexVersion,
      backendImage: $backendImage,
      authMode: $authMode,
      consistency: $consistency,
      database: {schemaVersion: $dbSchema, serverVersion: $dbVersion},
      keycloak: (if $authMode == "bundled" then
        {image: $keycloakImage, databaseServerVersion: $keycloakDbVersion}
      else null end),
      components: $components,
      configurationIncluded: $configurationIncluded
    }' >"${staging}/manifest.json"

  local checksum_files=(manifest.json data/larex-postgres.dump data/app-data.tar.gz)
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    checksum_files+=(data/keycloak-postgres.dump data/keycloak-data.tar.gz)
  fi
  if [[ "${INCLUDE_CONFIG}" == true ]]; then
    local config_file
    while IFS= read -r config_file; do
      checksum_files+=("${config_file#${staging}/}")
    done < <(find "${staging}/configuration" -type f | LC_ALL=C sort)
  fi
  write_checksums "${staging}" "${checksum_files[@]}"

  tar -czf "${destination}.partial" -C "${staging}" .
  mv "${destination}.partial" "${destination}"

  rm -rf "${create_temp}"
  TEMP_DIR=""
  extract_and_verify "${destination}"
  print_verification_summary
  RESTART_ON_EXIT=true
  log "Snapshot created: ${destination}"
}

version_major() {
  printf '%s\n' "$1" | sed -E 's/^[^0-9]*([0-9]+).*/\1/'
}

preflight_backup() {
  local archive="$1"
  require_command docker
  configure_compose
  compose config --quiet
  extract_and_verify "${archive}"

  local archive_auth archive_version current_version archive_pg target_pg
  archive_auth="$(jq -r '.authMode' "${VERIFIED_DIR}/manifest.json")"
  [[ "${archive_auth}" == "${AUTH_MODE}" ]] \
    || fail "Auth-mode mismatch: snapshot=${archive_auth}, target=${AUTH_MODE}"

  current_version="$(tr -d '[:space:]' <"${REPO_ROOT}/VERSION")"
  archive_version="$(jq -r '.larexVersion' "${VERIFIED_DIR}/manifest.json")"
  if [[ "$(version_major "${archive_version}")" != "$(version_major "${current_version}")" \
        && "${FORCE_INCOMPATIBLE}" != true ]]; then
    fail "LAREX major-version mismatch (${archive_version} -> ${current_version}); use --force-incompatible only after reviewing migrations"
  fi

  service_exists postgres || fail "Target Compose model has no postgres service"
  service_exists app || fail "Target Compose model has no app service"
  service_running postgres || fail "Target postgres must be running for preflight"
  archive_pg="$(jq -r '.database.serverVersion' "${VERIFIED_DIR}/manifest.json")"
  target_pg="$(database_server_version postgres)"
  if (( $(version_major "${target_pg}") < $(version_major "${archive_pg}") )); then
    fail "Target PostgreSQL ${target_pg} is older than snapshot PostgreSQL ${archive_pg}"
  fi

  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    service_exists keycloak-postgres || fail "Target has no keycloak-postgres service"
    service_exists keycloak || fail "Target has no keycloak service"
    service_running keycloak-postgres || fail "Target keycloak-postgres must be running for preflight"
  fi
  print_verification_summary
  if [[ "${CUSTOM_COMPOSE}" == true ]]; then
    log "Restore preflight passed for the custom Compose model."
  else
    log "Restore preflight passed for profile ${PROFILE}."
  fi
}

restore_database() {
  local service="$1"
  local source="$2"
  log "Restoring database service ${service}..."
  compose exec -T "${service}" sh -ec '
    psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"
    exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      --exit-on-error --no-owner --no-privileges
  ' <"${source}"
}

restore_backup() {
  [[ "${ASSUME_YES}" == true ]] \
    || fail "Restore replaces the target databases and data volumes; re-run with --yes"

  preflight_backup "$1"
  # preflight created VERIFIED_DIR and configured Compose.
  local snapshot_dir="${VERIFIED_DIR}"
  STOPPED_SERVICES=()
  RESTART_ON_EXIT=false
  pause_mutating_services
  ensure_container app
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    ensure_container keycloak
  fi

  restore_volume app /mnt/data "${snapshot_dir}/data/app-data.tar.gz"
  restore_database postgres "${snapshot_dir}/data/larex-postgres.dump"
  if [[ "${AUTH_MODE}" == "bundled" ]]; then
    restore_volume keycloak /opt/keycloak/data "${snapshot_dir}/data/keycloak-data.tar.gz"
    restore_database keycloak-postgres "${snapshot_dir}/data/keycloak-postgres.dump"
  fi

  local expected_schema restored_schema
  expected_schema="$(jq -r '.database.schemaVersion' "${snapshot_dir}/manifest.json")"
  restored_schema="$(database_schema_version postgres)"
  [[ "${restored_schema}" == "${expected_schema}" ]] \
    || fail "Restored schema version ${restored_schema} does not match snapshot ${expected_schema}"

  if [[ ${#STOPPED_SERVICES[@]} -gt 0 ]]; then
    log "Starting services that were running before restore..."
    compose start "${STOPPED_SERVICES[@]}" >/dev/null
  fi
  STOPPED_SERVICES=()
  log "Restore completed successfully. Check service health before accepting traffic."
}

require_command jq
require_command tar
if ! command -v sha256sum >/dev/null 2>&1; then
  require_command shasum
fi

case "${COMMAND}" in
  create)
    create_backup
    ;;
  verify)
    [[ -n "${ARCHIVE}" ]] || fail "verify requires an archive path"
    extract_and_verify "$(absolute_path "${ARCHIVE}")"
    print_verification_summary
    ;;
  preflight)
    [[ -n "${ARCHIVE}" ]] || fail "preflight requires an archive path"
    preflight_backup "$(absolute_path "${ARCHIVE}")"
    ;;
  restore)
    [[ -n "${ARCHIVE}" ]] || fail "restore requires an archive path"
    restore_backup "$(absolute_path "${ARCHIVE}")"
    ;;
esac
