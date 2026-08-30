# 알림 발송이 단일 인스턴스를 전제한다

- **ID:** BL-0047
- **기록일:** 2026-08-25

## 배경

[OutboxDispatcher](../../../apps/notification-service/boot/worker/src/main/java/com/impati/commerce/notification/adapter/in/scheduler/OutboxDispatcher.java)가 스스로 인정하고 있다.

> 인스턴스가 여러 개면 같은 항목을 두 번 집을 수 있으므로 운영에서는 조회에 잠금을 걸거나 리더 선출이 필요하다. 지금은 단일 인스턴스를 전제한다.

[findPendingMail](../../../apps/notification-service/infra/persistence/src/main/java/com/impati/commerce/notification/adapter/out/persistence/JdbcNotificationRepository.java)이 `where delivery_status = 'PENDING' ... limit :limit`인 점유 없는 조회다. 인스턴스 둘이 같은 주기에 깨면 같은 행을 함께 집어 같은 메일을 두 번 보낸다. 상태를 `SENT`로 바꾸는 것은 발송 **뒤**이므로 그 사이가 그대로 창이다.

**이 저장소는 분산 환경을 전제한다.** 단일 인스턴스를 가정한 로직은 그 가정이 깨지는 날 조용히 틀린다 — 예외가 나지 않고 메일만 두 통 간다. 같은 성질의 문제를 재고에서 한 번 겪었고(JVM 락을 DB로 옮겼다), 결제 정리에서는 처음부터 점유로 풀었다.

백오프가 없는 것도 함께 딸려 온다. 실패한 행이 다음 주기에 곧바로 다시 집히므로, 메일 시스템 장애 중에는 밀린 건수만큼 재시도가 증폭된다.

## 목표

인스턴스가 여러 개여도 같은 알림이 두 번 발송되지 않는다. 배타성을 지키는 수단이 스케줄러가 아니라 저장소에 있어서 진입점을 늘려도 성질이 유지된다. 실패한 건이 최소 간격 안에 다시 집히지 않는다.

[BL-0039](bl-0039-resolve-unknown-payment-outcome.md)에서 조건부 UPDATE로 점유하는 방식([ADR-0009](../../adr/0009-reconcile-unconfirmed-payments.md))을 이미 세웠으므로 새로 정할 것은 적다.
