create table notifications (
    id         varchar(64)  primary key,
    seq        bigint       auto_increment,
    event_type varchar(64)  not null,
    member_id  varchar(64)  not null,
    subject    varchar(255) not null,
    body       varchar(2000) not null,

    -- auto_increment 컬럼은 키의 첫 컬럼이어야 한다. 목록 정렬이 이 값을 쓰므로
    -- 인덱스가 어차피 필요하고, 테이블 안에서 선언해 그 요구를 함께 만족시킨다.
    key idx_notifications_seq (seq)
);

create index idx_notifications_member_id on notifications (member_id);
