# 동시 가입 경합이 500을 반환한다

- **ID:** BL-0008
- **기록일:** 2026-08-16

## 배경

사전 조회 두 건이 모두 통과한 뒤 하나가 `ux_members_email`에 걸리면 `DataIntegrityViolationException`이 나는데 [ApiExceptionHandler](../../apps/member-service/src/main/java/com/impati/commerce/member/support/ApiExceptionHandler.java)가 `DomainException`만 매핑한다.

500은 "서버가 고장났다"는 뜻이라 클라이언트가 재시도 판단을 못 한다.

## 목표

동시 가입 경합에서 진 쪽이 409를 받는다. 제약 위반을 도메인 언어로 옮기는 매핑이 핸들러에 있다.
