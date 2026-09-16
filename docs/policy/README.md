# 정책 (비즈니스 규칙 결정)

현재 무엇이 참이어야 하는지를 정한다. 각 문서는 `PD-NNNN` ID를 갖고, 문서 안의 규칙은 `PD-NNNN-RN`으로 가리킨다. 규칙 ID는 테스트 주석에 남아 정책과 검증을 잇는다.

**참조는 한 방향이다.** 코드가 정책을 가리키고, 정책 문서는 테스트나 코드를 가리키지 않는다. 문서가 코드를 가리키면 코드가 바뀔 때마다 문서가 거짓이 되지만, 코드가 문서를 가리키면 코드와 함께 움직인다. 같은 이유로 일정·담당자·진행 상황처럼 시간이 지나면 낡는 정보도 담지 않는다.

파일명은 `pd-NNNN-kebab-case-title.md`이고 번호는 이어서 부여한다. `BL`, `ADR` 번호와 독립적으로 채번한다. 삭제된 문서까지 Git 이력에서 확인해 번호를 재사용하지 않는다. 작성 규약은 아래 로컬 규칙을 우선하고, 나머지는 `decide-policy` 스킬의 `references/policy-convention.md`를 따른다.

**규칙이 바뀌면 새 번호를 만들고 이전 문서를 같은 변경에서 삭제한다.** `docs/policy/`에는 현재 유효한 정책만 남긴다. 무엇이 언제 왜 바뀌었는지와 삭제된 문서의 내용은 Git 이력에서 찾는다. 아직 병합되지 않아 한 번도 유효하지 않았던 문서는 새 번호를 만들지 않고 고친다.

정책 목록은 현재 문서를 찾는 색인이다. 규칙의 실제 검증 여부는 테스트가 답한다.

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

| # | 정책 |
| --- | --- |
| [0001](pd-0001-signup-and-email-verification.md) | 가입과 이메일 소유 확인 |
| [0003](pd-0003-order-lifecycle-and-cancellation.md) | 주문 생애주기와 취소 |
| [0005](pd-0005-inventory-reservation.md) | 재고 예약과 가용 수량 |
| [0007](pd-0007-product-exposure-and-search.md) | 상품 노출과 검색 |
| [0011](pd-0011-payment-authorization-and-capture.md) | 결제 승인과 매입 |
| [0013](pd-0013-shipment-progress-and-cancellation.md) | 배송 진행과 취소 |
| [0014](pd-0014-login-rejection-and-session-lifetime.md) | 로그인 거절과 세션 수명 |
| [0016](pd-0016-notification-delivery.md) | 알림 기록과 발송 |
| [0017](pd-0017-checkout-execution-and-recovery.md) | 체크아웃 실행과 복구 |
| [0018](pd-0018-cart-checkout-snapshot.md) | 장바구니와 구매 스냅샷 |
| [0020](pd-0020-order-history-visibility.md) | 주문 내역 노출과 진행 이력 |
