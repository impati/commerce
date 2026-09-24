#!/usr/bin/env bash
set -euo pipefail

SHIPMENT_ID="${1:?usage: scripts/demo-shipping.sh SHIPMENT_ID}"
SHIPPING_URL="${SHIPPING_URL:-http://localhost:8107}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
CARRIER_CODE="${CARRIER_CODE:-PRIMARY}"
CARRIER_WEBHOOK_SECRET="${CARRIER_WEBHOOK_SECRET:-local-carrier-secret}"

echo "packing complete and carrier registration"
shipment="$(curl -sS -H 'Content-Type: application/json' -X POST \
  "${SHIPPING_URL}/internal/shipments/${SHIPMENT_ID}/packing-complete" -d '{}')"
tracking_number="$(printf '%s' "$shipment" | jq -er '.trackingNumber')"
echo "$shipment"

carrier_event() {
  local status="$1"
  local timestamp occurred_at event_id body signature
  timestamp="$(date +%s)"
  occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  event_id="evt-demo-${status,,}-${timestamp}-${RANDOM}"
  body="$(jq -cn --arg tracking "$tracking_number" --arg status "$status" --arg occurredAt "$occurred_at" \
    '{trackingNumber:$tracking,status:$status,occurredAt:$occurredAt}')"
  signature="$(printf '%s\n%s\n%s' "$event_id" "$timestamp" "$body" \
    | openssl dgst -sha256 -hmac "$CARRIER_WEBHOOK_SECRET" | awk '{print $2}')"
  curl -sS -H 'Content-Type: application/json' \
    -H "X-Carrier-Event-Id: ${event_id}" \
    -H "X-Carrier-Timestamp: ${timestamp}" \
    -H "X-Carrier-Signature: sha256=${signature}" \
    -X POST "${GATEWAY_URL}/carrier/webhooks/events" -d "$body"
  echo
}

echo "carrier pickup"
carrier_event PICKED_UP

if [[ "${DEMO_CARRIER_FLOW:-delivered}" == "returned" ]]; then
  echo "final delivery failure"
  carrier_event DELIVERY_FAILED
  echo "return completed"
  carrier_event RETURNED
else
  echo "delivery completed"
  carrier_event DELIVERED
fi
