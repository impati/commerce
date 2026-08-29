# 인증 메일 요청이 가입 트랜잭션 안에 있다

- **ID:** BL-0004
- **기록일:** 2026-08-16

## 배경

[RegistrationExecutor.issueVerification](../../apps/member-service/src/main/java/com/impati/commerce/member/application/component/RegistrationExecutor.java)이 `@Transactional` 안에서 notification-service를 HTTP로 부른다. 손해가 셋이다.

**하나. 알림 장애가 인증 장애가 된다.** [공통 타임아웃](../../libs/common-http/src/main/java/com/impati/commerce/http/HttpClientTimeoutProperties.java)이 connect 1초 · read 3초이므로 최악 4초 동안 DB 커넥션이 트랜잭션과 함께 잡힌다. notification-service가 느려진 상태에서 동시 가입이 커넥션 풀 크기만큼만 들어와도 member-service 전체가 멈추고, 그러면 로그인도 함께 멈춘다. 타임아웃을 더 줄여도 창이 좁아질 뿐 결합은 남는다.

**둘. 실패한 발송이 유실된다.** 예외를 잡아 `log.warn` 한 줄로 끝낸다. 재시도가 없으므로 그 계정은 확인 대기 상태로 남고, 복구는 사용자가 "메일이 안 왔다"를 스스로 인지해 재발송을 눌러야만 일어난다. 실패가 상태로 남지 않아 얼마나 일어나는지도 알 수 없다.

**셋. 결과를 모른다.** read timeout은 "안 갔다"가 아니라 "모른다"다. notification-service가 이미 기록하고 메일까지 보낸 뒤 응답만 유실됐을 수 있다. 결제에서 겪은 것과 같은 성질이며([ADR-0009](../adr/0009-reconcile-unconfirmed-payments.md)), 지금은 재시도를 하지 않아 드러나지 않을 뿐이다.

예외를 잡는 것으로 가입이 유지되는 것은 맞다 — `NotificationClient` 구현이 트랜잭션에 참여하지 않는 빈이기 때문이다. 다만 그 자리에 `@Transactional` 빈이 하나만 끼어도 rollback-only가 켜져 커밋 시점에 가입 자체가 날아간다. member-service의 저장소 셋은 전부 `@Transactional`이므로 이 함정은 한 줄 거리에 있다.

## 목표

가입 트랜잭션이 notification-service의 응답 시간에 묶이지 않는다. 발송 실패가 유실되지 않고 재시도되며, 그 재시도가 프로세스 재시작을 넘어 살아남는다. 재시도 한도를 넘긴 것은 상태로 남아 관측된다.

order-service → notification-service 구간은 원인이 달라 [BL-0052](../bl-0052-order-notification-delivered-once.md)으로 분리했다.
