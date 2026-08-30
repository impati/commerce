-- 같은 발송 요청을 여러 번 받아도 알림을 한 건만 기록한다 (ADR-0011).
--
-- 발신자는 응답을 못 받으면 처리 여부를 알 수 없고 그 상태에서 할 수 있는 선택은 재시도뿐이다.
-- 그래서 발신은 at-least-once이고, 중복을 없애는 것은 결과를 아는 수신측의 일이다.
--
-- 승자를 정하는 것은 애플리케이션의 조회가 아니라 이 제약이다. 조회로 먼저 확인하면 동시에
-- 들어온 두 요청이 둘 다 "없음"을 읽는 경합이 남는다.
--
-- 기록만 남기는 알림(주문 이벤트)은 아직 키가 없다. 유니크 제약은 NULL을 서로 다르게 보므로
-- 그 행들은 여럿이어도 걸리지 않는다.
alter table notifications add column idempotency_key varchar(64);

create unique index uq_notifications_idempotency_key on notifications (idempotency_key);
