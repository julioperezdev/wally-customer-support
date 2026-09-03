#!/usr/bin/env bash

set -euo pipefail

readonly DEFAULT_SECRET_ID="wcs/prod/whatsapp"
readonly DEFAULT_PHONE_NUMBER_ID="1271920986004478"
readonly DEFAULT_GRAPH_API_VERSION="v25.0"
readonly DEFAULT_TEMPLATE_NAME="jaspers_market_plain_text_v1"
readonly DEFAULT_LANGUAGE_CODE="en_US"

secret_id="$DEFAULT_SECRET_ID"
phone_number_id="$DEFAULT_PHONE_NUMBER_ID"
graph_api_version="$DEFAULT_GRAPH_API_VERSION"
template_name="$DEFAULT_TEMPLATE_NAME"
language_code="$DEFAULT_LANGUAGE_CODE"
recipient=""
secret_json=""
access_token=""

usage() {
  cat <<'EOF'
Usage:
  scripts/send-whatsapp-template.sh --recipient <whatsapp-id> [options]

Required:
  --recipient <whatsapp-id>       Destination WhatsApp ID in international format,
                                  digits only and without a leading +.

Options:
  --secret-id <id>                Secrets Manager secret ID
                                  (default: wcs/prod/whatsapp)
  --phone-number-id <id>          Meta phone number ID
                                  (default: current test number)
  --graph-api-version <version>   Graph API version (default: v25.0)
  --template <name>               Approved template name
                                  (default: jaspers_market_plain_text_v1)
  --language <code>               Template language (default: en_US)
  -h, --help                      Show this help

AWS credentials and region are resolved by the AWS CLI default provider chain.
The script does not accept or print the access token.
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
    --recipient)
      (($# >= 2)) || die "--recipient requires a value"
      recipient="$2"
      shift 2
      ;;
    --secret-id)
      (($# >= 2)) || die "--secret-id requires a value"
      secret_id="$2"
      shift 2
      ;;
    --phone-number-id)
      (($# >= 2)) || die "--phone-number-id requires a value"
      phone_number_id="$2"
      shift 2
      ;;
    --graph-api-version)
      (($# >= 2)) || die "--graph-api-version requires a value"
      graph_api_version="$2"
      shift 2
      ;;
    --template)
      (($# >= 2)) || die "--template requires a value"
      template_name="$2"
      shift 2
      ;;
    --language)
      (($# >= 2)) || die "--language requires a value"
      language_code="$2"
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

[[ -n "$recipient" ]] || die "--recipient is required"
[[ "$recipient" =~ ^[0-9]+$ ]] || die "--recipient must contain digits only"
[[ "$phone_number_id" =~ ^[0-9]+$ ]] || die "--phone-number-id must contain digits only"
[[ "$graph_api_version" =~ ^v[0-9]+\.[0-9]+$ ]] || die "invalid Graph API version"
[[ -n "$secret_id" ]] || die "secret ID cannot be empty"
[[ -n "$template_name" ]] || die "template name cannot be empty"
[[ -n "$language_code" ]] || die "language code cannot be empty"

require_command aws
require_command jq
require_command curl

cleanup() {
  unset secret_json access_token
}
trap cleanup EXIT HUP INT TERM

secret_json="$(
  aws secretsmanager get-secret-value \
    --secret-id "$secret_id" \
    --query SecretString \
    --output text
)" || die "could not read secret '$secret_id' from AWS Secrets Manager"

access_token="$(
  printf '%s' "$secret_json" |
    jq -er '(."access-token" // .access_token // .accessToken // .WHATSAPP_ACCESS_TOKEN) | select(type == "string" and length > 0)'
)" || die "secret '$secret_id' does not contain a non-empty WhatsApp access token"

unset secret_json

payload="$(
  jq -cn \
    --arg recipient "$recipient" \
    --arg template "$template_name" \
    --arg language "$language_code" \
    '{messaging_product: "whatsapp", to: $recipient, type: "template", template: {name: $template, language: {code: $language}}}'
)"

endpoint="https://graph.facebook.com/${graph_api_version}/${phone_number_id}/messages"

printf 'Sending WhatsApp template through phone number ID %s\n' "$phone_number_id"
curl --fail-with-body --silent --show-error --include \
  --request POST "$endpoint" \
  --header "Authorization: Bearer ${access_token}" \
  --header 'Content-Type: application/json' \
  --data-raw "$payload"
