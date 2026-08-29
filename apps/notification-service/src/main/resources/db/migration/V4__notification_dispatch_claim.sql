-- 발송을 건별로 점유한다 (ADR-0011, ADR-0009).
--
-- 다음 시도 시각을 조건부 UPDATE로 밀어 집는다. 갱신된 행이 1이면 이 인스턴스가 점유했고
-- 0이면 다른 인스턴스가 이미 집었거나 아직 시각이 아니다. 배타성·실패 백오프·크래시
-- 자기치유가 이 한 컬럼에서 나온다 — 점유가 시각을 미리 밀어두므로 실패 경로에 쓰기가 없어도
-- 최소 간격 안에는 다시 집히지 않는다.
alter table notifications add column next_attempt_after timestamp with time zone;

-- 후보 조회가 delivery_status와 next_attempt_after로 거르고 seq로 정렬한다.
create index idx_notifications_dispatch on notifications (delivery_status, next_attempt_after, seq);

-- V2의 발송 대기 인덱스를 대체한다. 점유가 붙으면서 시각 없이 거르는 조회가 사라졌다.
drop index idx_notifications_pending;
