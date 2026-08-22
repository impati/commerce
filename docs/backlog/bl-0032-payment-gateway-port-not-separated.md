# 결제 로컬 대역이 응용 계층에 있다

- **ID:** BL-0032
- **기록일:** 2026-08-22

## 배경

payment-service의 응용 계층이 결제 대행사 역할을 직접 흉내낸다. [PaymentService](../../apps/payment-service/src/main/java/com/impati/commerce/payment/application/PaymentService.java)가 `DECLINE_TOKENS` 집합을 들고 특정 토큰 문자열(`card_test_decline`, `decline`, `fail`)을 거절로 판정한다. 거절 경로를 데모와 테스트에서 밟을 수 있게 하려고 넣은 장치이며, 그 토큰은 데모 시드 데이터이고 README에 문서화돼 있다.

CLAUDE.md는 외부 시스템 어댑터의 로컬 대역 구현을 어댑터에 두라고 정한다 — 외부 호출을 하지 않는 것은 어댑터 선택의 결과여야 하고, 도메인이나 응용 계층이 그 사실을 알아서는 안 된다. 지금은 그 판정이 응용 계층에 있으므로 실제 대행사를 붙일 때 `PaymentService`를 고쳐야 한다. **어댑터만 갈아끼워 운영에 나갈 수 있는 상태가 아니다.**

승인 성공 시의 값도 같은 문제를 갖는다. [Payment](../../apps/payment-service/src/main/java/com/impati/commerce/payment/domain/PaymentModels.java) 생성자가 `method`를 `CARD`로, `transactionId`를 자체 생성 식별자로 하드코딩한다. 실제로는 대행사가 돌려주는 값이다.

## 목표

결제 대행사가 포트로 분리되고, 로컬 대역과 실제 대행사가 그 포트의 두 구현이 된다. 응용 계층에서 특정 토큰 문자열을 아는 코드가 사라지고, 대행사를 바꾸는 작업이 어댑터 교체로 끝난다. 거절 경로를 데모와 테스트에서 밟는 수단은 유지된다.
