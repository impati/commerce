# 서비스 간 호출 실패의 의미를 보존한다

- **ID:** BL-0067
- **기록일:** 2026-09-15

## 배경

서비스 간 HTTP 클라이언트의 실패 처리가 일관되지 않다. order-service의 member·catalog 클라이언트, display-service와 cart-service의 catalog 클라이언트, member-service의 notification 클라이언트는 응답 코드 실패와 전송 실패를 `RestClient` 예외인 채로 응용 계층에 올려보낸다. 반면 order-service의 나머지 클라이언트는 일부 실패를 `DomainException`으로 옮기지만 호출의 성격에 따라 `conflict`, `unavailable`, `outcomeUnknown`을 고르는 기준이 클라이언트마다 다르다.

프로토콜 예외가 그대로 새면 응답이 `ErrorResponse` 계약을 벗어나고, 하위 서비스의 장애가 호출자의 내부 오류처럼 보인다. 하위 5xx나 전송 실패를 `conflict`로 옮기면 사용자의 요청 문제와 우리 서비스의 장애도 구분되지 않는다. 읽기 실패, 부수효과가 일어났는지 알 수 없는 실패, 하위 서비스가 명시적으로 거절한 요청은 복구와 재시도 방법이 서로 다른데 현재 경계가 그 의미를 보존하지 않는다.

## 목표

게이트웨이를 제외한 서비스 간 호출 실패가 모두 도메인 언어로 응용 계층에 도달한다. 요청 충돌, 서비스 이용 불가, 처리 결과 불명을 응답 상태와 `ErrorResponse`로 구분할 수 있고, 같은 성격의 실패가 클라이언트마다 다르게 번역되지 않는다.
