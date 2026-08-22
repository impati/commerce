-- 한 주문에 결제는 하나만 존재한다 (PD-0011-R2). 응용 계층의 조회는 동시 요청을 막지 못하므로
-- 마지막 판정을 DB가 한다.
alter table payments add constraint uq_payments_order_id unique (order_id);

-- 유일 제약이 같은 컬럼의 인덱스를 만든다. 남겨두면 같은 조회에 인덱스가 두 개가 된다.
drop index idx_payments_order_id;
