-- 알림을 아웃박스로 만든다.
--
-- 기존에는 알림 기록만 남기고 실제 발송은 없었다. 발송이 붙으면 "기록은 됐는데 발송이 실패한"
-- 상태가 생기고, 그걸 표현할 자리가 없으면 실패가 조용히 사라진다. order-service가 알림 호출
-- 예외를 삼키고 있던 것도 같은 문제였다.
--
-- 기록은 요청 트랜잭션에서 커밋하고 발송은 분리한다. 메일 시스템 장애가 가입 실패가 되지 않는다.
alter table notifications add column channel varchar(16) default 'NONE' not null;
alter table notifications add column recipient varchar(255);
alter table notifications add column delivery_status varchar(16) default 'SKIPPED' not null;
alter table notifications add column attempts int default 0 not null;
alter table notifications add column last_error varchar(500);

create index idx_notifications_pending on notifications (delivery_status, seq);
