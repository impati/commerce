#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

cd "$ROOT_DIR"

needs_build=false
for service in member-service catalog-service display-service inventory-service cart-service payment-service shipping-service notification-service order-service api-gateway; do
  if [[ ! -f "apps/${service}/build/libs/${service}-0.1.0.jar" ]]; then
    needs_build=true
  fi
done

if [[ "${SKIP_BUILD:-false}" != "true" && "$needs_build" == "true" ]]; then
  ./gradlew bootJar
fi

start_service() {
  local service="$1"
  local port="$2"
  local jar="apps/${service}/build/libs/${service}-0.1.0.jar"
  local log="$RUN_DIR/${service}.log"
  local pid_file="$RUN_DIR/${service}.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "${service} already running"
    return
  fi

  echo "starting ${service} on ${port}"
  java -jar "$jar" >"$log" 2>&1 &
  echo "$!" >"$pid_file"
}

wait_health() {
  local service="$1"
  local port="$2"
  for _ in {1..60}; do
    if curl -fs "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
      echo "${service} is healthy"
      return
    fi
    sleep 1
  done
  echo "${service} did not become healthy. See $RUN_DIR/${service}.log" >&2
  exit 1
}

start_service member-service 8101
start_service catalog-service 8102
start_service display-service 8103
start_service inventory-service 8104
start_service cart-service 8105
start_service payment-service 8106
start_service shipping-service 8107
start_service notification-service 8109
start_service order-service 8108
start_service api-gateway 8080

wait_health member-service 8101
wait_health catalog-service 8102
wait_health display-service 8103
wait_health inventory-service 8104
wait_health cart-service 8105
wait_health payment-service 8106
wait_health shipping-service 8107
wait_health notification-service 8109
wait_health order-service 8108
wait_health api-gateway 8080

echo "all services are running. gateway: http://localhost:8080"
