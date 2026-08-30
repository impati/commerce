-- 이메일 소유 인증 토큰.
--
-- 원문 토큰을 저장하지 않는다. DB가 유출되면 평문 토큰은 그대로 계정 인증 수단이 되므로
-- 비밀번호와 같은 취급이 필요하다. 조회는 해시로 한다.
create table email_verifications (
    token_hash varchar(64) primary key,
    member_id  varchar(64) not null,
    expires_at datetime(6) not null,
    used_at    datetime(6),
    constraint fk_email_verifications_member foreign key (member_id) references members (id)
);

create index idx_email_verifications_member on email_verifications (member_id);

-- 세션. 같은 이유로 해시만 보관한다.
--
-- 불투명 토큰을 쓰는 이유는 폐기가 즉시 되기 때문이다. JWT는 요청당 홉이 없는 대신
-- 폐기하려면 별도 블랙리스트가 필요하다.
create table member_sessions (
    token_hash varchar(64) primary key,
    member_id  varchar(64) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6),
    constraint fk_member_sessions_member foreign key (member_id) references members (id)
);

create index idx_member_sessions_member on member_sessions (member_id);
