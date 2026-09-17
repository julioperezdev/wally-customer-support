#!/usr/bin/env bash

set -euo pipefail

readonly DEFAULT_ENDPOINT="https://guapajjmta.us-east-1.awsapprunner.com/webhook/telegram"
readonly DEFAULT_SECRET_ID="wcs/prod/telegram"
readonly DEFAULT_REGION="us-east-1"

endpoint="${WCS_TELEGRAM_WEBHOOK_URL:-$DEFAULT_ENDPOINT}"
secret_id="${WCS_TELEGRAM_SECRET_ID:-$DEFAULT_SECRET_ID}"
region="${AWS_REGION:-$DEFAULT_REGION}"
chat_id="${WCS_TELEGRAM_CHAT_ID:-}"
sender_id="${WCS_TELEGRAM_SENDER_ID:-}"
message=""
update_id="${WCS_TELEGRAM_UPDATE_ID:-}"

usage() {
  cat <<'EOF'
Usage:
  scripts/smoke-telegram-message.sh --message <text> [options]

Required:
  --message <text>               Message to simulate
  --chat-id <id>                 Telegram chat ID used by the backend

Options:
  --url <url>                    Webhook endpoint
                                  (default: current WCS App Runner endpoint)
  --secret-id <id>               Secrets Manager secret ID
                                  (default: wcs/prod/telegram)
  --region <region>              AWS region used to read the secret
                                  (default: us-east-1)
  --sender-id <id>               Sender ID (defaults to --chat-id)
  --update-id <number>           Telegram update ID (generated if omitted)
  -h, --help                     Show this help

Environment alternatives:
  WCS_TELEGRAM_WEBHOOK_URL, WCS_TELEGRAM_SECRET_ID, WCS_TELEGRAM_CHAT_ID,
  WCS_TELEGRAM_SENDER_ID, WCS_TELEGRAM_UPDATE_ID, AWS_REGION

The script reads only the webhook secret from Secrets Manager. It does not
accept or print the Telegram bot token. The request simulates an inbound
Telegram Update; the backend's normal outbound adapter sends the response to
the supplied chat ID.
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
    --url)
      (($# >= 2)) || die "--url requires a value"
      endpoint="$2"
      shift 2
      ;;
    --secret-id)
      (($# >= 2)) || die "--secret-id requires a value"
      secret_id="$2"
      shift 2
      ;;
    --region)
      (($# >= 2)) || die "--region requires a value"
      region="$2"
      shift 2
      ;;
    --chat-id)
      (($# >= 2)) || die "--chat-id requires a value"
      chat_id="$2"
      shift 2
      ;;
    --sender-id)
      (($# >= 2)) || die "--sender-id requires a value"
      sender_id="$2"
      shift 2
      ;;
    --message)
      (($# >= 2)) || die "--message requires a value"
      message="$2"
      shift 2
      ;;
    --update-id)
      (($# >= 2)) || die "--update-id requires a value"
      update_id="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

[[ "$endpoint" =~ ^https:// || "$endpoint" =~ ^http://(localhost|127\.0\.0\.1)(:|/) ]] \
  || die "--url must use HTTPS, or HTTP for localhost"
[[ "$endpoint" == */webhook/telegram ]] || die "--url must end in /webhook/telegram"
[[ -n "$secret_id" ]] || die "secret ID cannot be empty"
[[ -n "$chat_id" ]] || die "--chat-id is required"
[[ -n "$message" ]] || die "--message is required"

if [[ -z "$sender_id" ]]; then
  sender_id="$chat_id"
fi

if [[ -z "$update_id" ]]; then
  update_id="$(printf '%s%05d' "$(date +%s)" "$$")"
fi

[[ "$update_id" =~ ^[0-9]+$ ]] || die "--update-id must contain only digits"

require_command aws
require_command jq
require_command curl

secret_json=""
webhook_secret_token=""
payload=""

cleanup() {
  unset secret_json webhook_secret_token payload
}
trap cleanup EXIT HUP INT TERM

secret_json="$(
  aws secretsmanager get-secret-value \
    --secret-id "$secret_id" \
    --region "$region" \
    --query SecretString \
    --output text
)" || die "could not read secret '$secret_id' from AWS Secrets Manager"

webhook_secret_token="$(
  printf '%s' "$secret_json" |
    jq -er '(."webhook-secret-token" // .webhook_secret_token // .webhookSecretToken // ."secret-token" // .TELEGRAM_WEBHOOK_SECRET_TOKEN) | select(type == "string" and length > 0)'
)" || die "secret '$secret_id' does not contain a non-empty Telegram webhook secret"

unset secret_json

payload="$(
  jq -cn \
    --arg update_id "$update_id" \
    --arg chat_id "$chat_id" \
    --arg sender_id "$sender_id" \
    --arg text "$message" \
    --argjson date "$(date +%s)" \
    '{
      update_id: ($update_id | tonumber),
      message: {
        message_id: ($update_id | tonumber),
        from: {id: $sender_id, is_bot: false, first_name: "Smoke Test"},
        chat: {id: $chat_id, type: "private"},
        date: $date,
        text: $text
      }
    }'
)"

printf 'Submitting Telegram smoke message to WCS (update_id=%s)\n' "$update_id"
curl --fail-with-body --silent --show-error --include \
  --request POST "$endpoint" \
  --header 'Content-Type: application/json' \
  --header "X-Telegram-Bot-Api-Secret-Token: ${webhook_secret_token}" \
  --data-raw "$payload"
printf '\n'
