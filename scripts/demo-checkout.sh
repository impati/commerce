#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${DEMO_EMAIL:-demo@impati.test}"
PASSWORD="${DEMO_PASSWORD:-demo-password}"

json_post() {
  local path="$1"
  local body="$2"
  curl -sS -H 'Content-Type: application/json' -H "Authorization: Bearer ${TOKEN:-}" \
    -X POST "${BASE_URL}${path}" -d "$body"
}

echo "1. login"
# 퍼블릭 API는 memberId를 받지 않는다. 신원은 세션 토큰에서만 온다.
login_response="$(curl -sS -H 'Content-Type: application/json' -X POST "${BASE_URL}/login" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")"
TOKEN="$(printf '%s' "$login_response" | jq -r '.token')"
echo "session acquired"
echo

echo "2. home"
curl -sS "${BASE_URL}/display/home"
echo
echo

echo "3. add cart item"
json_post "/cart/items" '{"skuId":"sku_tee_white_m","quantity":2}'
echo
echo

echo "4. checkout"
checkout_response="$(json_post "/checkout" '{"paymentToken":"card_test_success"}')"
echo "$checkout_response"
echo
echo

shipment_id="$(printf '%s' "$checkout_response" | jq -r '.shipment.id')"

echo "5. ship"
json_post "/shipments/${shipment_id}/ship" '{}'
echo
echo

echo "6. deliver"
json_post "/shipments/${shipment_id}/deliver" '{}'
echo
echo

echo "7. notifications"
curl -sS -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/notifications"
echo
