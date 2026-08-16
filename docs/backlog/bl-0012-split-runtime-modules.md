# 실행 모듈을 나눌 것인가

- **ID:** BL-0012
- **기록일:** 2026-08-16

## 배경

지금은 서비스당 실행 가능한 서버가 하나여서 컨슈머·배치·internal API를 따로 만들 수 없다. `build.gradle`이 `apps/*` 전부에 boot 플러그인을 붙이는 것이 코드적 제약이다.

나눌 이유가 실재하는 곳은 둘이다 — member(external vs internal)와 notification(api vs worker).

member를 external/internal로 나누면 내부 경로를 네트워크로 분리할 수 있다. 다만 신뢰 경계 자체는 [ADR-0002](../adr/0002-network-segmentation-as-trust-boundary.md)에서 네트워크 분리로 강제하기로 이미 정했으므로, 이 항목은 보안 대책이 아니라 실행 단위 구성의 문제로 남는다.

## 목표

한 서비스가 여러 실행 단위를 가질 수 있는지 정하고, 가능하다면 boot 플러그인 적용 범위를 그에 맞게 바꾼다.
