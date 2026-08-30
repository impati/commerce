-- 매입 결과를 확인하지 못한 채 취소된 주문을 표시한다 (PD-0012-R12).
-- 주문 생애주기는 취소로 끝났고, 모르는 것은 결제 쪽 사실이므로 상태가 아니라 별도 열로 둔다.
alter table orders add column payment_outcome_unknown boolean not null default false;
