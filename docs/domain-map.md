# Domain Map

## 식별한 도메인

사용자가 상품을 보고 결제와 배송 완료까지 가는 데 필요한 도메인은 다음과 같이 식별했습니다.

| 도메인 | 포함한 이유 | 핵심 모델 |
| --- | --- | --- |
| 회원 | 주문 주체와 배송지 소유 | `Member`, `Address` |
| 전시 | 사용자가 상품을 발견하는 화면 구성 | `DisplaySection` |
| 상품 | 판매 대상과 가격 스냅샷 | `Product`, `Sku` |
| 재고 | 한정 자원 보호, 예약/확정/해제 | `StockItem`, `Reservation` |
| 장바구니 | 주문 전 구매 의도 | `Cart`, `CartLine` |
| 주문 | 상거래 트랜잭션의 중심 aggregate | `Order`, `OrderLine` |
| 결제 | 외부 PG와 유사한 승인/매입 경계 | `Payment` |
| 배송 | fulfillment lifecycle | `Shipment` |
| 알림 | 고객/운영자 커뮤니케이션 | `Notification` |
| API Gateway | 외부 API와 내부 서비스 조합 | route, downstream client |

## 선택한 분리 기준

1. 데이터 소유권이 다르면 서비스를 나눴습니다.
2. 변경 속도가 다른 영역을 분리했습니다. 예를 들어 전시 정책은 주문 정책보다 자주 바뀝니다.
3. 장애 격리와 보상 트랜잭션이 필요한 영역을 명시했습니다. 결제 실패는 재고 예약 해제와 주문 취소로 보상됩니다.
4. 조회 중심 도메인인 전시/상품과 트랜잭션 중심 도메인인 주문/재고/결제를 분리했습니다.

## 현재 구현의 현실적인 단순화

- DB 대신 인메모리 repository를 사용합니다.
- 메시지 브로커 대신 주문 서비스가 notification-service에 HTTP로 이벤트를 기록합니다.
- 서비스 디스커버리는 쓰지 않고 설정값의 base URL로 연결합니다.
- 운영 환경에서는 각 서비스별 DB, broker, tracing, retry, circuit breaker를 추가하는 것이 다음 단계입니다.

