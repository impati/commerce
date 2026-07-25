#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

json_post() {
  local path="$1"
  local body="$2"
  curl -sS -H 'Content-Type: application/json' -X POST "${BASE_URL}${path}" -d "$body"
}

echo "1. home"
curl -sS "${BASE_URL}/display/home"
echo
echo

echo "2. add cart item"
json_post "/cart/mem_demo/items" '{"skuId":"sku_tee_white_m","quantity":2}'
echo
echo

echo "3. checkout"
checkout_response="$(json_post "/checkout" '{"memberId":"mem_demo","paymentToken":"card_test_success"}')"
echo "$checkout_response"
echo
echo

# sed로 뽑으면 greedy 매칭이 shipment.address.id를 집어온다. 필드 순서에 의존하지 않게 jq를 쓴다.
shipment_id="$(printf '%s' "$checkout_response" | jq -r '.shipment.id')"

echo "4. ship"
json_post "/shipments/${shipment_id}/ship" '{}'
echo
echo

echo "5. deliver"
json_post "/shipments/${shipment_id}/deliver" '{}'
echo
echo

echo "6. notifications"
curl -sS "${BASE_URL}/notifications"
echo

