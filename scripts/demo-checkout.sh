#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${DEMO_EMAIL:-demo@impati.test}"
PASSWORD="${DEMO_PASSWORD:-demo-password}"

json_post() {
  local path="$1"
  local body="$2"
  curl -sS -H 'Content-Type: application/json' -H "Authorization: Bearer ${TOKEN:-}" \
    -H "Idempotency-Key: ${CHECKOUT_KEY:-demo-unused}" -X POST "${BASE_URL}${path}" -d "$body"
}

echo "1. login"
# 퍼블릭 API는 memberId를 받지 않는다. 신원은 토큰에서만 온다.
# 요청에 붙이는 것은 접근 토큰이다. 세션 토큰은 갱신과 로그아웃에만 쓴다 (ADR-0007).
login_response="$(curl -sS -H 'Content-Type: application/json' -X POST "${BASE_URL}/login" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")"
TOKEN="$(printf '%s' "$login_response" | jq -r '.accessToken')"
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
cart_view="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/cart")"
quote_id="$(printf '%s' "$cart_view" | jq -r '.quote.id')"
member_view="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/me")"
address_id="$(printf '%s' "$member_view" | jq -r '.addresses[] | select(.defaultAddress) | .id')"
address_confirmation="$(printf '%s' "$member_view" | jq -r '.addresses[] | select(.defaultAddress) | .confirmationToken')"
CHECKOUT_KEY="demo-$(date +%s)-${RANDOM}"
checkout_body="$(jq -n --arg quote "$quote_id" --arg address "$address_id" --arg confirmation "$address_confirmation" \
  '{paymentToken:"card_test_success",quoteId:$quote,addressId:$address,addressConfirmationToken:$confirmation}')"
checkout_response="$(json_post "/checkout" "$checkout_body")"
echo "$checkout_response"
echo
echo

shipment_id="$(printf '%s' "$checkout_response" | jq -r '.shipment.id')"

echo "5. carrier-driven shipping"
"$(dirname "$0")/demo-shipping.sh" "$shipment_id"
echo
echo

# 알림은 이제 비동기로 도착한다 (ADR-0016).
#
# 주문 사건이 아웃박스에 커밋되고, 릴레이가 카프카로 발행하고, 컨슈머가 받아 적는다. 그래서
# 배송 완료 직후에 물어보면 아직 비어 있다. 기다리는 것은 데모의 편의이며 검증이 아니다 —
# 이 스크립트에는 assert가 없다.
echo "6. notifications (사건이 브로커를 거쳐 오므로 잠깐 기다린다)"
for _ in $(seq 1 20); do
  body="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/notifications")"
  [[ "$body" != "[]" ]] && break
  sleep 1
done
echo "$body"
echo
