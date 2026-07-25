-- 비밀번호와 인증 상태.
--
-- password_hash를 nullable로 둔다. 이 마이그레이션 이전에 만들어진 회원은 비밀번호가 없고,
-- 그 상태를 "설정되지 않음"으로 정직하게 표현한다. 빈 문자열 같은 placeholder를 넣으면
-- 비밀번호가 있는 것처럼 보이면서 아무 값과도 일치하지 않는 모호한 상태가 된다.
-- 로그인은 password_hash가 없는 회원을 거부한다.
alter table members add column password_hash varchar(100);

-- 기존 회원은 이미 활성 상태로 두고, 신규 가입만 PENDING_VERIFICATION으로 시작한다.
