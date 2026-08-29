# 정책 (비즈니스 규칙 결정)

무엇이 참이어야 하는지를 정하고 그 시점을 고정한다. 각 문서는 `PD-NNNN` ID를 갖고, 문서 안의 규칙은 `PD-NNNN-RN`으로 가리킨다. 규칙 ID는 테스트 주석에 남아 정책과 검증을 잇는다.

**참조는 한 방향이다.** 코드가 정책을 가리키고, 정책 문서는 테스트나 코드를 가리키지 않는다. 문서가 코드를 가리키면 코드가 바뀔 때마다 문서가 거짓이 되지만, 코드가 문서를 가리키면 코드와 함께 움직인다. 같은 이유로 일정·담당자·진행 상황처럼 시간이 지나면 낡는 정보도 담지 않는다.

파일명은 `pd-NNNN-kebab-case-title.md`이고 번호는 이어서 부여한다. `BL`, `ADR` 번호와 독립적으로 채번한다. 작성 규약은 `decide-policy` 스킬의 `references/policy-convention.md`를 따른다.

**규칙이 바뀌면 기존 문서를 고치지 않는다.** 새 번호를 만들어 이전 문서를 대체한다고 적는다. 고쳐버리면 무엇이 언제 왜 바뀌었는지가 사라진다. 오탈자와 링크 정리만 기존 문서에서 한다.

**"지금 유효한 규칙 전부"를 모은 문서를 만들지 않는다.** 코드가 바뀌는 순간 거짓이 되고 아무도 고치지 않는다. 지금 무엇이 참인지는 테스트가 답한다.

## 다른 문서와의 경계

| 위치 | 담는 것 |
| --- | --- |
| `docs/policy/` | 무엇이 참이어야 하나. 사람이 정한 비즈니스 규칙 |
| `docs/adr/` | 그것을 어떤 방식으로 구현할지 골랐나. 재검토 조건까지 |
| `docs/backlog/` | 앞으로 할 후보. 배경과 목표만 |
| `docs/` | 설계 — 무엇을 만드는가 |
| `git log` | 무엇을 왜 했나 |

하나의 정책을 여러 방식으로 구현할 수 있을 때만 ADR이 따로 필요하다.

## 목록

| # | 정책 | 상태 |
| --- | --- | --- |
| [0001](pd-0001-signup-and-email-verification.md) | 가입과 이메일 소유 확인 | 유효 |
| [0002](pd-0002-login-rejection-and-session-lifetime.md) | 로그인 거절과 세션 수명 | 대체됨 ([0014](pd-0014-login-rejection-and-session-lifetime.md)) |
| [0003](pd-0003-order-lifecycle-and-cancellation.md) | 주문 생애주기와 취소 | 유효 |
| [0004](pd-0004-checkout-and-compensation.md) | 체크아웃 성립과 실패 보상 | 대체됨 ([0012](pd-0012-checkout-and-compensation.md)) |
| [0005](pd-0005-inventory-reservation.md) | 재고 예약과 가용 수량 | 유효 |
| [0006](pd-0006-cart-composition.md) | 장바구니 구성 | 유효 |
| [0007](pd-0007-product-exposure-and-search.md) | 상품 노출과 검색 | 유효 |
| [0008](pd-0008-payment-capture.md) | 결제 확정 | 대체됨 ([0011](pd-0011-payment-authorization-and-capture.md)) |
| [0009](pd-0009-notification-delivery.md) | 알림 기록과 발송 | 대체됨 ([0016](pd-0016-notification-delivery.md)) |
| [0010](pd-0010-shipment-progress.md) | 배송 진행 | 대체됨 ([0013](pd-0013-shipment-progress-and-cancellation.md)) |
| [0011](pd-0011-payment-authorization-and-capture.md) | 결제 승인과 매입 | 유효 |
| [0012](pd-0012-checkout-and-compensation.md) | 체크아웃 성립과 실패 보상 | 유효 |
| [0013](pd-0013-shipment-progress-and-cancellation.md) | 배송 진행과 취소 | 유효 |
| [0014](pd-0014-login-rejection-and-session-lifetime.md) | 로그인 거절과 세션 수명 | 유효 |
| [0015](pd-0015-unconfirmed-payment-reconciliation.md) | 결제 미확인 주문의 정리 | 유효 |
| [0016](pd-0016-notification-delivery.md) | 알림 기록과 발송 | 유효 |
