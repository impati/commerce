# member-service 아웃박스

- **ID:** BL-0004
- **기록일:** 2026-08-16

## 배경

인증 메일 요청이 가입 트랜잭션 안에 있다 ([RegistrationService](../../apps/member-service/src/main/java/com/impati/commerce/member/application/component/RegistrationExecutor.java)). 실패해도 가입은 유지되지만 상대가 느리면 DB 트랜잭션이 함께 늘어난다.

order-service → notification-service 구간도 같은 문제다. 그쪽은 예외를 삼켜 실패가 기록되지 않는다.

## 목표

서비스 간 호출이 DB 트랜잭션 밖으로 나가고, 실패한 발송이 유실되지 않고 재시도된다. notification-service에 이미 만든 아웃박스와 같은 형태를 두 구간에 적용한다.
