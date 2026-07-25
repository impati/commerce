-- 이메일 유일성을 대소문자 구분으로 바꾼다.
--
-- RFC 5321상 로컬부의 대소문자 해석은 수신 도메인이 결정한다. 외부에서 a@x.com과 A@x.com이
-- 같은 사서함인지 단정할 수 없으므로, 대소문자를 무시하면 다른 사서함의 주인이 가입하지 못한다.
-- 차단된 사용자는 데이터에 남지 않아 우리가 그 손실을 관측할 수도 없다.
--
-- 중복 계정 위험은 이메일 소유 인증으로 막는다. 같은 사람이 대소문자를 달리해 두 번 가입하면
-- 인증 메일이 같은 받은편지함에 도착한다. 배경은 problem/003 참고.
drop index ux_members_email_normalized;

alter table members drop column email_normalized;

create unique index ux_members_email on members (email);
