-- 비밀번호를 필수로 둔다.
--
-- 비밀번호 없는 회원을 표현할 이유가 없다. nullable로 두면 "비밀번호가 설정되지 않은 회원"이라는
-- 상태가 스키마에 생기고, 로그인·재설정·인증 경로마다 그 분기를 다뤄야 한다.
--
-- 아직 계정이 없는 시점이므로 기존 행을 지우고 컬럼을 not null로 추가한다. 데모 회원은 시드가
-- 다시 만든다. 계정이 생긴 뒤에는 이 방식을 쓸 수 없고 백필이 필요하다.
delete from member_addresses;
delete from members;

alter table members add column password_hash varchar(100) not null;

-- 신규 가입은 PENDING_VERIFICATION으로 시작한다. status가 이제 의미를 갖는다.
