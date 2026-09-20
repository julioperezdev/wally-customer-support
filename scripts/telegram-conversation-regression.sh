#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DEFAULT_FIXTURE="$ROOT_DIR/src/test/resources/fixtures/conversation-regression-v1.json"
readonly DEFAULT_ENDPOINT="https://guapajjmta.us-east-1.awsapprunner.com/webhook/telegram"

fixture="$DEFAULT_FIXTURE"
endpoint="${WCS_TELEGRAM_WEBHOOK_URL:-$DEFAULT_ENDPOINT}"
chat_id="${WCS_TELEGRAM_CHAT_ID:-}"
difficulty=""
case_id=""
delay_seconds=3
live=false

usage() {
  cat <<'EOF'
Usage:
  scripts/telegram-conversation-regression.sh --dry-run [filters]
  scripts/telegram-conversation-regression.sh --live --chat-id <id> [filters]

The live flag is mandatory before sending anything. The script sends the
sanitized messages from the fixture through the normal WCS Telegram webhook;
the backend replies to the supplied Telegram chat. It never reads or prints
the bot token and never stores responses or chat history.

Options:
  --fixture <path>               Regression fixture JSON
  --url <url>                    WCS webhook endpoint
  --chat-id <id>                 Synthetic Telegram chat used for the smoke
  --difficulty <easy|medium|hard> Run only one difficulty
  --case <id>                    Run one scenario, e.g. H-001
  --delay-seconds <n>            Delay between messages (default: 3)
  --live                         Actually submit messages
  --dry-run                      Print the planned messages only
  -h, --help                     Show this help

Examples:
  scripts/telegram-conversation-regression.sh --dry-run --difficulty hard
  scripts/telegram-conversation-regression.sh --live --chat-id '<TEST_CHAT_ID>' --case H-001
  scripts/telegram-conversation-regression.sh --live --chat-id '<TEST_CHAT_ID>' --difficulty easy --delay-seconds 4
EOF
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

while (($# > 0)); do
  case "$1" in
    --fixture) (($# >= 2)) || die "--fixture requires a value"; fixture="$2"; shift 2 ;;
    --url) (($# >= 2)) || die "--url requires a value"; endpoint="$2"; shift 2 ;;
    --chat-id) (($# >= 2)) || die "--chat-id requires a value"; chat_id="$2"; shift 2 ;;
    --difficulty) (($# >= 2)) || die "--difficulty requires a value"; difficulty="$2"; shift 2 ;;
    --case) (($# >= 2)) || die "--case requires a value"; case_id="$2"; shift 2 ;;
    --delay-seconds) (($# >= 2)) || die "--delay-seconds requires a value"; delay_seconds="$2"; shift 2 ;;
    --live) live=true; shift ;;
    --dry-run) shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
done

require_command jq
[[ -f "$fixture" ]] || die "fixture not found: $fixture"
[[ "$delay_seconds" =~ ^[0-9]+$ ]] || die "--delay-seconds must be a non-negative integer"
[[ -z "$difficulty" || "$difficulty" =~ ^(easy|medium|hard)$ ]] || die "unsupported difficulty: $difficulty"
[[ -z "$case_id" || "$case_id" =~ ^[EMH]-[0-9]{3}$ ]] || die "--case must look like E-001, M-001 or H-001"

jq -e '.scenarios | type == "array" and length >= 50' "$fixture" >/dev/null \
  || die "fixture must contain at least 50 scenarios"

if "$live"; then
  [[ -n "$chat_id" ]] || die "--chat-id is required with --live"
  [[ "$endpoint" =~ ^https:// ]] || die "live endpoint must use HTTPS"
  [[ "$endpoint" == */webhook/telegram ]] || die "endpoint must end in /webhook/telegram"
fi

filter='.scenarios[]'
if [[ -n "$difficulty" ]]; then
  filter="$filter | select(.difficulty == \"$difficulty\")"
fi
if [[ -n "$case_id" ]]; then
  filter="$filter | select(.id == \"$case_id\")"
fi

scenario_ids=()
while IFS= read -r scenario_id; do
  [[ -n "$scenario_id" ]] && scenario_ids+=("$scenario_id")
done < <(jq -r "$filter | .id" "$fixture")
(( ${#scenario_ids[@]} > 0 )) || die "no scenarios match the selected filters"

printf 'Conversation regression suite: %d scenario(s), mode=%s\n' \
  "${#scenario_ids[@]}" "$([[ "$live" == true ]] && printf live || printf dry-run)"

for id in "${scenario_ids[@]}"; do
  printf '\n[%s]\n' "$id"
  message_count="$(jq -r "$filter | select(.id == \"$id\") | .messages | length" "$fixture")"
  for ((index=0; index<message_count; index++)); do
    message="$(jq -r "$filter | select(.id == \"$id\") | .messages[$index]" "$fixture")"
    printf '%d/%d %s\n' "$((index + 1))" "$message_count" "$message"
    if "$live"; then
      "$ROOT_DIR/scripts/smoke-telegram-message.sh" \
        --url "$endpoint" \
        --chat-id "$chat_id" \
        --message "$message"
      if ((delay_seconds > 0)); then
        sleep "$delay_seconds"
      fi
    fi
  done
done

printf '\nSuite submitted. Validate replies in Telegram and correlate sanitized logs by timestamp/correlation ID.\n'
