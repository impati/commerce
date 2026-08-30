-- 인증 메일 발송 아웃박스 (BL-0004, ADR-0010).
--
-- 가입 트랜잭션이 notification-service를 직접 부르지 않고 여기에 의도를 적는다. 회원·확인
-- 토큰과 같은 트랜잭션에서 커밋되므로 "가입은 됐는데 보낼 것이 없다"가 생기지 않고, 커밋된
-- 항목은 프로세스가 죽어도 남으므로 재시도가 재시작을 넘어 이어진다.
create table verification_mails (
    id                 varchar(64)  primary key,
    seq                bigint       auto_increment,
    member_id          varchar(64)  not null,
    email              varchar(255) not null,

    -- 원문 토큰. 발송이 나중으로 미뤄지므로 보관할 수밖에 없다.
    --
    -- email_verifications가 해시만 두는 것과 대비되는데, 그쪽은 조회 수단이고 이쪽은 보낼
    -- 내용이라 해시로 대신할 수 없다. 대신 종단 상태(SENT·FAILED)가 되는 순간 비운다 —
    -- 정상 경로에서 평문이 여기 있는 시간은 기록부터 발송까지, 보통 1초 미만이다.
    token              varchar(128),

    status             varchar(16)  not null,
    attempts           int          not null default 0,
    last_error         varchar(500),

    -- 다음 시도 시각. 조건부 UPDATE로 이 값을 미래로 밀어 한 건을 점유한다 (ADR-0009와
    -- 같은 방식). 갱신된 행이 1이면 점유했고 0이면 다른 인스턴스가 이미 집었거나 아직
    -- 시도 시각이 아니다. 점유가 곧 백오프이므로 실패해서 아무것도 쓰지 않아도 이 시각까지는
    -- 다시 집히지 않는다 — 실패 경로에 쓰기가 없어야 한다.
    --
    -- null은 "지금 시도할 수 있다"는 뜻이며 기록 시점의 기본값이다.
    next_attempt_after datetime(6),

    -- auto_increment 컬럼은 키의 첫 컬럼이어야 한다. 아래 발송 인덱스는 seq가 마지막이라
    -- 그 요구를 만족시키지 못하므로 따로 선언한다.
    key idx_verification_mails_seq (seq),

    constraint fk_verification_mails_member foreign key (member_id) references members (id)
);

-- 후보 조회가 status와 next_attempt_after로 거르고 seq로 정렬한다.
create index idx_verification_mails_dispatch on verification_mails (status, next_attempt_after, seq);

create index idx_verification_mails_member on verification_mails (member_id);
