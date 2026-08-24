-- 결제 미확인 주문의 다음 정리 시각 (PD-0015-R7, PD-0015-R8, ADR-0009).
--
-- 조건부 UPDATE로 이 값을 미래로 밀어 한 건을 점유한다. 갱신된 행이 1이면 점유에 성공한 것이고,
-- 0이면 다른 인스턴스가 이미 집었거나 아직 시도 시각이 아니다. 정리하는 인스턴스가 여러 개인 것을
-- 전제하므로 배타성이 필요하다.
--
-- 점유가 곧 백오프다. 실패해서 아무것도 쓰지 않아도 이 시각까지는 다시 집히지 않으므로,
-- 결제 서비스 장애 중에 밀린 건수만큼 부하가 커지지 않는다. 처리 중 인스턴스가 죽어도 시각이
-- 지나면 다시 집히므로 별도 복구 절차가 없다.
--
-- null은 "지금 시도할 수 있다"는 뜻이며 표시가 켜지는 순간의 기본값이다.
alter table orders add column payment_reconcile_after timestamp with time zone;

create index idx_orders_payment_reconcile on orders (payment_outcome_unknown, payment_reconcile_after);
