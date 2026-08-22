# 정책 규칙의 검증 공백을 메운다

- **ID:** BL-0030
- **기록일:** 2026-08-17

## 배경

여덟 개 정책 문서에 규칙 55개가 있었고, 그중 절반 이상을 아무 테스트도 고정하지 않았다. 고정되지 않은 규칙은 코드를 반대로 바꿔도 `make verify`가 통과한다.

이 중 결제와 배송은 [BL-0034](done/bl-0034-checkout-payment-integrity.md)에서 메워졌다. PD-0008과 PD-0010은 각각 [PD-0011](../policy/pd-0011-payment-authorization-and-capture.md), [PD-0013](../policy/pd-0013-shipment-progress-and-cancellation.md)로 대체되면서 규칙마다 테스트가 붙었다. 남은 것은 아래다.

- **상품 노출([PD-0007](../policy/pd-0007-product-exposure-and-search.md))에는 규칙 테스트가 하나도 없다.** 테스트가 전부 영속화 왕복 확인이다. 저장하고 읽으면 같은 값이 나온다는 것만 보고, 무엇을 거절해야 하는지는 보지 않는다.
- **체크아웃([PD-0012](../policy/pd-0012-checkout-and-compensation.md))의 R1~R4가 비어 있다.** 빈 장바구니 거절, 기본 배송지 선택, 남의 배송지 거절, 주문 라인의 가격 확정이 그것이다. PD-0004에서 그대로 넘어온 규칙이라 대체 작업에서도 다루지 않았다.
- **배송([PD-0013](../policy/pd-0013-shipment-progress-and-cancellation.md))의 R4가 비어 있다.** 배송지가 생성 시점의 사본이라는 규칙이다. R8과 R9는 "지원하지 않는다"와 "서로 자동 전이하지 않는다"이므로 고정할 동작이 없다.

주문 상태 전이도 마찬가지다. 성공 경로 하나만 있고 거절 경로가 없다. 결제 전 주문에 배송을 붙이거나 완료된 주문을 취소하는 시도가 실제로 막히는지 확인하는 것이 없다.

CLAUDE.md는 보상과 롤백 경로를 성공 경로와 같은 비중으로 테스트하라고 정한다. 지금 상태는 그 기준에 미치지 못한다.

## 목표

각 정책 규칙에 그것을 고정하는 테스트가 대응하고, 테스트 주석에 규칙 ID가 남는다. 상태 전이 규칙은 허용되는 전이와 거절되는 전이를 함께 확인한다.

우선순위는 거절 규칙이다. 무엇을 허용하는지는 사용 중에 드러나지만 무엇을 거절해야 하는지는 드러나지 않는다.

규칙 자체를 바꾸는 항목([BL-0025](bl-0025-inventory-reservation-never-expires.md), [BL-0026](bl-0026-unpublished-sku-is-readable.md), [BL-0028](bl-0028-order-total-forces-krw.md), [BL-0029](bl-0029-order-and-shipment-status-diverge.md))을 먼저 처리하면 여기서 만들 테스트가 달라진다.
