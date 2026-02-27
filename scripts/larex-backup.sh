#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

API_BASE="${LAREX_API_BASE:-http://localhost:8080/api/v1}"
TOKEN="${LAREX_API_TOKEN:-}"
POLL_SECONDS="${LAREX_BACKUP_POLL_SECONDS:-2}"

usage() {
  cat <<USAGE
Usage:
  $(basename "$0") dump --output <path> [--api <url>] [--token <bearer-token>]
  $(basename "$0") reseed --source <dump.zip> --output <path> [--map <src=target,src2=target2>] [--api <url>] [--token <bearer-token>]

Environment:
  LAREX_API_BASE           Backend base URL (default: http://localhost:8080/api/v1)
  LAREX_API_TOKEN          Bearer token for GLOBAL_ADMIN user
  LAREX_BACKUP_POLL_SECONDS  Poll interval for job status (default: 2)
USAGE
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

json_escape() {
  printf '%s' "$1" | jq -Rr @json
}

api_call() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  local auth_header=()
  if [[ -n "$TOKEN" ]]; then
    auth_header=(-H "Authorization: Bearer $TOKEN")
  fi

  if [[ -n "$data" ]]; then
    curl -sS -X "$method" \
      "${auth_header[@]}" \
      -H "Content-Type: application/json" \
      -d "$data" \
      "$API_BASE$path"
  else
    curl -sS -X "$method" \
      "${auth_header[@]}" \
      "$API_BASE$path"
  fi
}

validate_path() {
  local role="$1"
  local path="$2"

  local payload
  payload=$(jq -cn --arg p "$path" --arg r "$role" '{path:$p, role:$r}')
  local response
  response=$(api_call POST "/admin/backup/validate-path" "$payload")

  local valid
  valid=$(printf '%s' "$response" | jq -r '.valid // false')
  if [[ "$valid" != "true" ]]; then
    local err
    err=$(printf '%s' "$response" | jq -r '.errorMessage // "Path validation failed"')
    echo "Path validation failed ($role): $err" >&2
    exit 1
  fi

  printf '%s' "$response" | jq -r '.normalizedPath'
}

start_job() {
  local payload="$1"
  local response
  response=$(api_call POST "/admin/backup/jobs" "$payload")

  local job_id
  job_id=$(printf '%s' "$response" | jq -r '.id // empty')
  if [[ -z "$job_id" ]]; then
    echo "Failed to start backup job: $response" >&2
    exit 1
  fi

  printf '%s' "$job_id"
}

poll_job() {
  local job_id="$1"

  while true; do
    local response
    response=$(api_call GET "/admin/backup/jobs/$job_id")

    local status progress step
    status=$(printf '%s' "$response" | jq -r '.status // "UNKNOWN"')
    progress=$(printf '%s' "$response" | jq -r '.progressPercent // 0')
    step=$(printf '%s' "$response" | jq -r '.currentStep // ""')

    printf '[%s] %s%% %s\n' "$status" "$progress" "$step"

    case "$status" in
      COMPLETED)
        printf '%s\n' "$response" | jq -r '.resultPath // empty'
        return 0
        ;;
      FAILED)
        local err
        err=$(printf '%s' "$response" | jq -r '.errorMessage // "Unknown error"')
        echo "Job failed: $err" >&2
        return 1
        ;;
      CANCELLED)
        echo "Job cancelled" >&2
        return 1
        ;;
    esac

    sleep "$POLL_SECONDS"
  done
}

main() {
  require_cmd curl
  require_cmd jq

  if [[ $# -lt 1 ]]; then
    usage
    exit 1
  fi

  local mode="$1"
  shift

  local source_path=""
  local output_path=""
  local mapping=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --source)
        source_path="$2"
        shift 2
        ;;
      --output)
        output_path="$2"
        shift 2
        ;;
      --map)
        mapping="$2"
        shift 2
        ;;
      --api)
        API_BASE="$2"
        shift 2
        ;;
      --token)
        TOKEN="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage
        exit 1
        ;;
    esac
  done

  if [[ -z "$output_path" ]]; then
    echo "--output is required" >&2
    exit 1
  fi

  local normalized_output
  normalized_output=$(validate_path OUTPUT "$output_path")

  local payload
  if [[ "$mode" == "dump" ]]; then
    payload=$(jq -cn \
      --arg type "DUMP" \
      --arg output "$normalized_output" \
      '{type:$type, outputPath:$output}')
  elif [[ "$mode" == "reseed" ]]; then
    if [[ -z "$source_path" ]]; then
      echo "--source is required for reseed" >&2
      exit 1
    fi

    local normalized_source
    normalized_source=$(validate_path SOURCE "$source_path")

    local mapping_json='{}'
    if [[ -n "$mapping" ]]; then
      IFS=',' read -ra entries <<< "$mapping"
      mapping_json='{}'
      for entry in "${entries[@]}"; do
        local src="${entry%%=*}"
        local tgt="${entry#*=}"
        if [[ -n "$src" && -n "$tgt" ]]; then
          mapping_json=$(printf '%s' "$mapping_json" | jq --arg s "$src" --arg t "$tgt" '. + {($s): $t}')
        fi
      done
    fi

    payload=$(jq -cn \
      --arg type "RESEED" \
      --arg source "$normalized_source" \
      --arg output "$normalized_output" \
      --argjson mapping "$mapping_json" \
      '{type:$type, sourcePath:$source, outputPath:$output, workspaceMapping:$mapping}')
  else
    echo "Unknown mode: $mode" >&2
    usage
    exit 1
  fi

  local job_id
  job_id=$(start_job "$payload")
  echo "Started job: $job_id"

  local result_path
  result_path=$(poll_job "$job_id")
  echo "Completed. Result path: $result_path"
}

main "$@"
