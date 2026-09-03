#!/usr/bin/env bash

set -euo pipefail

readonly DEFAULT_SECRET_ID="wcs/prod/telegram"

secret_id="$DEFAULT_SECRET_ID"
webhook_url=""
secret_json=""
bot_token=""
webhook_secret_token=""
payload=""

usage() {
  cat <<'EOF'
Usage:
  scripts/register-telegram-webhook.sh --url <https-webhook-url> [options]

Required:
  --url <url>                     Public HTTPS URL ending in /webhook/telegram

Options:
  --secret-id <id>                Secrets Manager secret ID
                                  (default: wcs/prod/telegram)
  -h, --help                      Show this help

The bot token and webhook secret are read from AWS Secrets Manager. The token
is not accepted as an argument or printed.
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
      webhook_url="$2"
      shift 2
      ;;
    --secret-id)
      (($# >= 2)) || die "--secret-id requires a value"
      secret_id="$2"
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

[[ "$webhook_url" =~ ^https:// ]] || die "--url must use HTTPS"
[[ "$webhook_url" == */webhook/telegram ]] || die "--url must end in /webhook/telegram"
[[ -n "$secret_id" ]] || die "secret ID cannot be empty"

require_command aws
require_command jq
require_command curl

cleanup() {
  unset secret_json bot_token webhook_secret_token payload
}
trap cleanup EXIT HUP INT TERM

secret_json="$(
  aws secretsmanager get-secret-value \
    --secret-id "$secret_id" \
    --query SecretString \
    --output text
)" || die "could not read secret '$secret_id' from AWS Secrets Manager"

bot_token="$(
  printf '%s' "$secret_json" |
    jq -er '(."bot-token" // .bot_token // .botToken // .token // .TELEGRAM_BOT_TOKEN) | select(type == "string" and length > 0)'
)" || die "secret '$secret_id' does not contain a non-empty Telegram bot token"

webhook_secret_token="$(
  printf '%s' "$secret_json" |
    jq -er '(."webhook-secret-token" // .webhook_secret_token // .webhookSecretToken // ."secret-token" // .TELEGRAM_WEBHOOK_SECRET_TOKEN) | select(type == "string" and length > 0)'
)" || die "secret '$secret_id' does not contain a non-empty webhook secret token"

unset secret_json

payload="$(
  jq -cn \
    --arg url "$webhook_url" \
    --arg secret "$webhook_secret_token" \
    '{url: $url, secret_token: $secret, allowed_updates: ["message"]}'
)"

printf 'Registering Telegram webhook at the configured HTTPS endpoint\n'
curl --fail-with-body --silent --show-error --include \
  --request POST "https://api.telegram.org/bot${bot_token}/setWebhook" \
  --header 'Content-Type: application/json' \
  --data-raw "$payload"
