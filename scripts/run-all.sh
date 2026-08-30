#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

cd "$ROOT_DIR"

# DB를 먼저 챙긴다.
#
# jar이 없으면 빌드하는 것과 같은 종류의 선행 작업이다. 이 스크립트의 계약은 "make boot-all
# 한 줄이면 쓸 수 있는 상태가 된다"이고, 저장소가 H2에서 MySQL로 옮겨가면서 그 계약이 외부
# 인프라에 의존하게 됐다. 문서로 메우면 새므로 스크립트가 챙긴다 (ADR-0013).
#
# 포트가 열려 있다는 것을 "우리 DB가 떠 있다"로 읽지 않는다. 3306은 흔한 포트라 다른
# 프로젝트의 MySQL이 이미 잡고 있을 수 있고, 그것에 조용히 붙으면 남의 데이터베이스에
# 마이그레이션을 돌리게 된다. 실제로 이 스크립트를 처음 돌렸을 때 그런 컨테이너가 있었다.
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3316}"

port_open() {
  (exec 3<>"/dev/tcp/${DB_HOST}/${DB_PORT}") 2>/dev/null
}

compose_mysql_running() {
  command -v docker >/dev/null 2>&1 &&
    docker compose ps --status=running --services 2>/dev/null | grep -qx mysql
}

# 포트가 열린 것을 준비된 것으로 읽지 않는다.
#
# 도커는 컨테이너가 뜨는 순간 포트를 바인딩하지만 mysqld는 그보다 늦게 접속을 받는다. 포트만
# 보고 넘어가면 서비스가 "Communications link failure"로 죽는다 — 실제로 그렇게 실패했다.
# compose에 healthcheck가 있으므로 그것을 기다린다.
mysql_healthy() {
  local id
  id="$(docker compose ps -q mysql 2>/dev/null)" || return 1
  [[ -n "$id" ]] || return 1
  [[ "$(docker inspect --format '{{.State.Health.Status}}' "$id" 2>/dev/null)" == "healthy" ]]
}

wait_for_ready() {
  for _ in {1..90}; do
    if mysql_healthy; then
      return 0
    fi
    sleep 1
  done
  return 1
}

ensure_database() {
  # 사용자가 접속 정보를 직접 지정했으면 그 판단을 따른다.
  if [[ -n "${DB_URL:-}" ]]; then
    echo "using DB_URL from the environment"
    return
  fi

  if compose_mysql_running; then
    if wait_for_ready; then
      echo "mysql is ready at ${DB_HOST}:${DB_PORT}"
      return
    fi
    echo "compose의 mysql이 떠 있는데 ${DB_HOST}:${DB_PORT}에 닿지 않는다." >&2
    echo "docker compose logs mysql 으로 원인을 볼 것." >&2
    exit 1
  fi

  if port_open; then
    echo "${DB_HOST}:${DB_PORT}를 이미 다른 프로세스가 쓰고 있다." >&2
    echo "우리 DB가 아닌 곳에 마이그레이션을 돌리지 않기 위해 여기서 멈춘다." >&2
    echo "그것을 내리거나, 의도한 것이라면 DB_URL을 지정해 다시 실행할 것." >&2
    exit 1
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "mysql이 필요한데 ${DB_HOST}:${DB_PORT}에 없고 docker도 없다." >&2
    exit 1
  fi

  echo "starting mysql"
  docker compose up -d mysql
  if ! wait_for_ready; then
    echo "mysql이 ${DB_HOST}:${DB_PORT}에 뜨지 않았다. docker compose logs mysql 을 볼 것." >&2
    exit 1
  fi
  echo "mysql is ready at ${DB_HOST}:${DB_PORT}"
}

ensure_database

# 항상 빌드한다.
#
# 예전에는 jar이 없을 때만 빌드했다. 그러면 코드를 고친 뒤 make boot-all을 돌려도 낡은 jar이
# 뜨고, 그 사실이 아무 데도 드러나지 않는다 — 실제로 이 변경을 검증하다가 H2로 도는 예전
# 빌드를 띄웠다. Gradle이 증분 빌드라 바뀐 것이 없으면 값이 거의 들지 않는다.
if [[ "${SKIP_BUILD:-false}" != "true" ]]; then
  ./gradlew bootJar
fi

# 이름·모듈 경로·포트를 따로 받는다.
#
# 실행 단위가 서비스와 1:1이 아니게 됐다 — order와 notification은 api와 worker 둘이고, 모듈
# 경로(order-service/boot/api)와 산출물 이름(order-api)이 다르다 (ADR-0014).
start_service() {
  local service="$1"
  local module="$2"
  local port="$3"
  local jar="apps/${module}/build/libs/${service}-0.1.0.jar"
  local log="$RUN_DIR/${service}.log"
  local pid_file="$RUN_DIR/${service}.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "${service} already running"
    return
  fi

  echo "starting ${service} on ${port}"
  java -jar "$jar" --spring.profiles.active="${SPRING_PROFILE:-local}" >"$log" 2>&1 &
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

start_service member-service member-service 8101
start_service catalog-service catalog-service 8102
start_service display-service display-service 8103
start_service inventory-service inventory-service 8104
start_service cart-service cart-service 8105
start_service payment-service payment-service 8106
start_service shipping-service shipping-service 8107
start_service notification-api notification-service/boot/api 8109
start_service notification-worker notification-service/boot/worker 8119
start_service order-api order-service/boot/api 8108
start_service order-worker order-service/boot/worker 8118
start_service api-gateway api-gateway 8080

wait_health member-service 8101
wait_health catalog-service 8102
wait_health display-service 8103
wait_health inventory-service 8104
wait_health cart-service 8105
wait_health payment-service 8106
wait_health shipping-service 8107
wait_health notification-api 8109
wait_health notification-worker 8119
wait_health order-api 8108
wait_health order-worker 8118
wait_health api-gateway 8080

echo "all services are running. gateway: http://localhost:8080"
