# 백로그

지금 구현하지 않는 문제와 작업 후보를 항목별로 남긴다. 각 항목은 `BL-NNNN` 추적 ID를 갖고, 그 ID가 백로그 파일과 작업 브랜치·커밋·리뷰를 잇는다.

항목을 추가할 때는 `capture-backlog` 스킬을 쓴다. 작업을 시작할 때는 `BL-NNNN 작업 시작`으로 `start-work` 스킬을 부른다.

**상태는 위치로 구분한다.** 이 디렉터리에는 열린 항목만 두고, 완료한 항목은 [done/](done/)으로 옮긴다. 완료 항목을 지우지 않는 이유는 채번 때문이다 — 다음 `BL-NNNN`은 `done/`까지 세서 정하고 번호를 재사용하지 않는다.

## 후보 판단 기준

현재 코드와 호출 흐름에서 문제가 발생할 수 있는 경로와 영향을 확인한 뒤 후보로 삼는다. 실제 장애 기록이 없어도 현재 경로에서 성립하는 실패는 대상이지만, 아직 없는 기능이나 호출자를 가정해야만 생기는 문제는 미리 해결하지 않는다. 해당 기능이 생길 때 다시 판단한다.

기존 백로그도 작업 시작 전에 이 기준으로 재검토한다. 후보에서 제외하기로 한 항목은 완료로 옮기지 않고 삭제하며, 이유는 커밋에 남긴다. 삭제한 ID는 재사용하지 않고 채번할 때 삭제 이력도 확인한다.

## 다른 문서와의 경계

| 위치 | 담는 것 |
| --- | --- |
| `docs/backlog/` | 앞으로 할 후보. 배경과 목표만 |
| `docs/adr/` | 무엇을 왜 골랐나. 재검토 조건까지 |
| `problem/` | 겪은 문제와 기술 조사. 사용자가 요청할 때만 쓴다 |
| `docs/` | 설계 — 무엇을 만드는가 |
| `CLAUDE.md` | 작업 룰 — 어떻게 작업하는가 |
| `git log` | 무엇을 왜 했나. 이 저장소는 `CHANGELOG.md`를 두지 않는다 |

코드에 붙는 부채는 백로그가 아니라 코드의 `TODO` 주석에 남긴다. 정본은 grep이다.

```bash
make todo
```

## 지금 우선순위

위에서부터 먼저 한다. 이 순서는 항목 파일에 적지 않는다 — 순서는 바뀌고 항목은 남는다.

기존 항목의 상대 순서는 유지한다. 다음 작업을 고를 때 아래 기준으로 현재 코드에서 문제가 성립하는지 먼저 확인한다.

| 순서 | 항목 | 왜 이 순서인가 |
| --- | --- | --- |
| 1 | [BL-0069](bl-0069-cart-line-management.md) 장바구니 줄 관리 | 구매 전 기본 조작이 추가만 가능한 상태다 |
| 2 | [BL-0071](bl-0071-order-price-breakdown.md) 주문 금액 구성과 배송비 | 최종 청구 금액의 구성이 먼저 확정돼야 한다 |
| 3 | [BL-0016](bl-0016-display-card-price-mismatch.md) 지면 카드 가격 | 탐색 단계의 가격과 실제 구매 가격이 어긋날 수 있다 |
| 4 | [BL-0025](bl-0025-inventory-reservation-never-expires.md) 재고 예약 만료 | 방치된 예약이 실제 판매 가능 재고를 무기한 막는다 |
| 5 | [BL-0072](bl-0072-customer-order-cancellation.md) 출고 전 주문 취소 | 성립한 구매를 고객이 안전하게 되돌릴 수 있어야 한다 |
| 6 | [BL-0073](bl-0073-inventory-movement-history.md) 재고 이동 이력 | 취소와 반품이 되돌리는 재고를 설명할 근거가 필요하다 |
| 7 | [BL-0075](bl-0075-return-and-refund.md) 반품과 환불 | 배송 이후 고객 여정을 닫는다 |
| 8 | [BL-0076](bl-0076-payment-attempt-and-retry.md) 결제 시도와 재시도 | 거절된 결제를 구매 내용 손실 없이 다시 시도할 수 있어야 한다 |
| 9 | [BL-0077](bl-0077-real-payment-gateway-integration.md) 실제 PG 연동 | 로컬 대역을 실제 외부 결제 흐름으로 연결한다 |
| 10 | [BL-0078](bl-0078-payment-transaction-history.md) 결제 거래 이력 | 고객 청구와 환불을 설명하고 대사할 근거다 |
| 11 | [BL-0079](bl-0079-payment-reconciliation.md) PG 거래 대사 | 내부 결제와 외부 거래의 불일치를 발견한다 |
| 12 | [BL-0080](bl-0080-payment-payout-settlement.md) PG 입금 정산 | 예상 금액과 실제 입금액까지 재무 흐름을 닫는다 |
| 13 | [BL-0081](bl-0081-commerce-operations-backoffice.md) 커머스 운영 백오피스 | 기능을 DB 직접 조작 없이 운영할 수 있게 한다 |
| 14 | [BL-0006](bl-0006-local-profile-smoke.md) local 프로파일 스모크 | 하네스가 못 보는 구간이다. 급하지 않다 |
| 15 | [BL-0007](bl-0007-password-policy-to-domain.md) 비밀번호 정책을 도메인으로 | 클래스 리뷰에서 나왔다. 지금 동작에는 문제가 없다 |
| 16 | [BL-0030](bl-0030-policy-verification-gaps.md) 정책 규칙 검증 공백 | 상품 노출 전체와 체크아웃 R1~R4. 크지만 기계적이라 앞의 것들이 끝난 뒤가 낫다 |
| 17 | [BL-0086](bl-0086-in-house-delivery-fulfillment.md) 자체 배송 이행 | 외부 택배사 기반 핵심 흐름을 먼저 완성한 뒤 확장한다 |
| 18 | [BL-0087](bl-0087-marketplace-seller-fulfillment.md) 다중 판매자 주문 이행 | 직매입 쇼핑몰의 핵심 흐름을 먼저 완성한 뒤 마켓플레이스로 확장한다 |

## 순서를 정하지 않은 항목

정해야 할 때 정한다.

| 항목 | 성격 |
| --- | --- |
| [BL-0009](bl-0009-split-money-per-domain.md) Money 도메인별 분리 | 합의는 됐고 둘 위치가 미정 |
| [BL-0010](bl-0010-identifier-collision-risk.md) 식별자 생성 방식 | PK라 미루면 비싸진다 |
| [BL-0011](bl-0011-flow-encapsulation-direction.md) 흐름 캡슐화 방향 | 설계 방향 결정. ADR 대상 |
| [BL-0013](bl-0013-test-isolation-strategy.md) 테스트 격리 방식 | 지금은 작성자 규율에 의존 |
| [BL-0015](bl-0015-pre-commit-scans-working-tree.md) pre-commit이 working tree를 본다 | 알려진 한계였다 |
| [BL-0018](bl-0018-normalize-email-case.md) 이메일 대소문자 정규화 | 기존 결정을 뒤집는 교환. 관측치가 필요하다 ([PD-0001](../policy/pd-0001-signup-and-email-verification.md)) |
| [BL-0019](bl-0019-invalidate-previous-verification-token.md) 재발송 시 이전 토큰 무효화 | 유효한 확인 토큰이 여러 개 존재한다 |
| [BL-0020](bl-0020-password-length-upper-bound.md) 비밀번호 길이 상한 | BCrypt가 72바이트 초과분을 조용히 버린다 |
| [BL-0021](bl-0021-pd-0001-verification-gaps.md) PD-0001 검증 공백 | 규칙 10개 중 5개를 아무것도 고정하지 않는다 |
| [BL-0022](bl-0022-unverified-login-leaks-account-existence.md) 미인증 로그인 응답의 계정 열거 | 한 문서 안에서 R1과 R2가 어긋난다 ([PD-0014](../policy/pd-0014-login-rejection-and-session-lifetime.md)) |
| [BL-0023](bl-0023-pd-0014-verification-gaps.md) PD-0014 검증 공백 | R3·R6·R7을 아무것도 고정하지 않는다 |
| [BL-0026](bl-0026-unpublished-sku-is-readable.md) 발행 전 판매 단위 노출 | 상품은 숨기는데 하위 단위가 샌다 |
| [BL-0028](bl-0028-order-total-forces-krw.md) 주문 총액 통화 고정 | 지금 맞는 이유가 계산이 옳아서가 아니다 |
| [BL-0031](bl-0031-check-policy-rule-coverage.md) 규칙·테스트 대응 검사 | 규율에 의존하는 것은 반드시 샌다 |
| [BL-0044](bl-0044-session-resolve-ignores-member-status.md) 세션 확인이 회원 상태를 무시 | 차단·탈퇴를 붙이는 순간 조용히 성립한다 |
| [BL-0049](bl-0049-scheduler-test-isolation-is-opt-out.md) 테스트의 스케줄러 격리가 규율에 달려 있다 | 이미 네 곳에서 샜다. 하네스 자신의 신뢰성 문제다 |
| [BL-0056](bl-0056-commit-unit-for-other-services.md) 나머지 서비스의 커밋 단위 | 지금 맞는 이유가 우연이다 ([ADR-0012](../adr/0012-order-events-as-outbox.md)) |
| [BL-0059](bl-0059-errors-are-strings-not-types.md) 실패를 문자열로 다룬다 | 자르기로 막은 것은 증상이다. 완료된 [BL-0067](done/bl-0067-preserve-downstream-failure-semantics.md)과 맞닿아 있다 |
