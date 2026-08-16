# 실행 모듈을 나눌 것인가

- **ID:** BL-0012
- **기록일:** 2026-08-16

## 배경

지금은 서비스당 실행 가능한 서버가 하나여서 컨슈머·배치·internal API를 따로 만들 수 없다. `build.gradle`이 `apps/*` 전부에 boot 플러그인을 붙이는 것이 코드적 제약이다.

나눌 이유가 실재하는 곳은 둘이다 — member(external vs internal)와 notification(api vs worker).

member를 external/internal로 나누는 것은 [BL-0001](bl-0001-gateway-bypass-block.md)의 해법 후보이기도 하다. 공유 시크릿은 코드로 막는 것이고 별도 프로세스는 네트워크로 막는 것이라 후자가 강하다.

## 목표

한 서비스가 여러 실행 단위를 가질 수 있는지 정하고, 가능하다면 boot 플러그인 적용 범위를 그에 맞게 바꾼다. BL-0001과 같은 일을 두 번 하지 않도록 함께 판단한다.
