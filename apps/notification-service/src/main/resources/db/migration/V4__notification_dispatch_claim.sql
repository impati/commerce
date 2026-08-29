-- 발송을 점유한다 (ADR-0011).
--
-- 다음 시도 시각을 조건부 UPDATE로 밀어 한 번에 집고, 집은 묶음에 그 주기의 식별자를 남긴다.
-- 배타성·실패 백오프·크래시 자기치유가 next_attempt_after 하나에서 나온다 — 점유가 시각을
-- 미리 밀어두므로 실패 경로에 쓰기가 없어도 최소 간격 안에는 다시 집히지 않는다.
alter table notifications add column next_attempt_after timestamp with time zone;

-- 집은 묶음을 가리킨다. 무엇을 집었는지 다시 읽을 때와 운영에서 누가 들고 있는지 볼 때 쓴다.
alter table notifications add column tx_id varchar(64);

-- 점유 대상 선택이 delivery_status와 next_attempt_after로 거르고 seq로 정렬한다.
create index idx_notifications_dispatch on notifications (delivery_status, next_attempt_after, seq);

create index idx_notifications_tx_id on notifications (tx_id);

-- V2의 발송 대기 인덱스를 대체한다. 점유가 붙으면서 시각 없이 거르는 조회가 사라졌다.
drop index idx_notifications_pending;
