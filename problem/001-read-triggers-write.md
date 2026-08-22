# 조회가 쓰기를 유발하던 저장소

작성일 2026-07-25 / 관련 커밋 `c2ac8a8`, `f1ed7d6`

## 배경

저장소를 포트로 추상화하는 작업 중 `CartRepository.getOrCreate`를 발견했다. 구현은 `computeIfAbsent`였다. `GET /cart/{memberId}`는 조회 API인데, 호출하면 저장소에 장바구니가 생긴다. 재고 쪽은 한 걸음 더 나가 있었다. `StockItem`을 저장하는 메서드가 포트에 아예 없었고, 저장소가 맵 내부 객체의 참조를 그대로 반환하고 호출자가 그 객체를 변형하면 저장된 것으로 취급하고 있었다.

두 코드는 모두 `ConcurrentHashMap`에서는 정상 동작한다. 다만 정상 동작하는 이유가 저장소 구현의 특성에 있다. 조회에 삽입을 끼워 넣을 수 있고, 조회가 항상 같은 객체를 돌려준다는 두 가지 사실에 애플리케이션 로직이 기대고 있다. 즉, 문제는 조회 API가 데이터를 만든다는 현상 자체가 아니라, 애플리케이션이 저장소 구현의 특성에 의존해서 저장소를 교체할 수 없다는 데 있다. 인터페이스를 뽑아놓아도 이 의존이 남아 있으면 추상화는 이름만 남는다.

따라서 포트를 구현 중립적으로 다시 정의하고, 그 규약을 테스트로 고정할 필요가 있다.

## 범위가 아닌 것

DB 도입은 다루지 않는다. 여기서 다루는 것은 저장소를 교체할 수 있는 상태로 만드는 것까지다. H2와 Flyway를 붙이는 작업은 후속이다.

## 문제 1. 조회가 행을 만든다

```java
// before — 저장소가 조회하면서 없으면 만들어 넣는다
public Cart getOrCreate(String memberId) {
    return carts.computeIfAbsent(memberId, Cart::new);
}

// CartService.get — save를 부르지 않는데도 저장소가 바뀐다
public CartResponse get(String memberId) {
    return carts.getOrCreate(memberId).toResponse();
}
```

DB 어댑터로 바꾸면 조회마다 INSERT가 따라붙는다. 그 결과는 다음과 같다.

- 읽기 전용 복제본으로 조회를 보낼 수 없다
- 조회에 부수효과가 있어 캐시를 붙일 수 없다
- 헬스체크, 크롤러, 모니터링이 데이터를 만든다
- 조회에 트랜잭션과 쓰기 락이 필요해져 느려지고 경쟁이 생긴다

## 문제 2. 변형이 곧 저장이다

재고는 네 개의 경로(`addStock`, `reserve`, `commit`, `release`)에서 모두 저장 호출이 없었다.

```java
// before — 맵 안의 객체를 직접 변형한다. 저장 호출이 없다.
inventory.findStock(line.skuId()).orElseThrow().reserve(line.quantity());
```

이 코드는 DB 어댑터에서 조용히 실패한다. 예외가 나지 않고, 예약과 확정과 해제가 반영되지 않은 채로 넘어간다. 문제 1보다 위험한 이유는 관찰 가능한 증상이 늦게 나타나기 때문이다.

## 조치

| 대상 | 변경 |
| --- | --- |
| `CartRepository` | `getOrCreate` 제거, `findByMemberId` 추가 |
| `CartService` | 없을 때 빈 장바구니를 만드는 책임을 애플리케이션으로 이동 |
| `InventoryRepository` | `getOrCreateStock` 제거, `saveStock` 추가 |
| `InventoryService` | 네 경로 모두 변형 후 명시적으로 `saveStock` 호출 |

`CartService.get`은 응답을 위해 빈 `Cart`를 만들지만 저장하지 않는다. HTTP 응답은 이전과 같이 200과 빈 장바구니이므로 API 계약과 프론트엔드는 바뀌지 않는다.

## 검증에서 드러난 것

문제 2는 인메모리 어댑터로 테스트하면 잡히지 않는다. 어댑터가 같은 객체를 돌려주므로 `saveStock`을 부르지 않아도 변경이 남는다. 이를 확인하기 위해 `InventoryService.commit`의 `saveStock` 호출을 제거한 상태로 저장소만 바꿔 테스트를 돌렸다.

| 테스트가 쓴 저장소 | `saveStock` 제거 | 결과 |
| --- | --- | --- |
| `DetachedInventoryRepository` (DB 모방) | 적용 | 실패 |
| `InMemoryInventoryRepository` | 적용 | 통과 |

인메모리 어댑터로만 검증하면 DB에서 깨질 코드를 계속 통과시킨다. 따라서 애플리케이션 계층 테스트는 조회마다 새 객체를 돌려주는 대역을 쓴다.

```java
// 스냅샷만 보관하고 조회할 때마다 새 StockItem을 만들어 돌려준다
@Override
public Optional<StockItem> findStock(String skuId) {
    return Optional.ofNullable(stock.get(skuId)).map(DetachedInventoryRepository::rebuild);
}

@Override
public void saveStock(StockItem item) {
    stock.put(item.skuId(), item.toResponse());
}
```

## 검토했으나 채택하지 않은 대안

- 인메모리 어댑터가 조회 시 복사본을 돌려주게 한다: 프로덕션 코드가 테스트를 위해 방어적 복사 비용을 지불한다. 도메인 객체에 복사 수단이 없어 어댑터가 도메인 재구성 로직을 갖게 되는 것도 부담이다.
- Mockito로 `saveStock` 호출 여부를 verify한다: 호출 사실은 잡지만 저장하지 않으면 값이 사라진다는 결과를 표현하지 못한다. 저장 대상이나 순서가 틀린 경우를 놓친다.
- API를 조회와 생성으로 분리한다(`GET`은 404, `POST`로 생성): 응답 계약이 바뀌어 프론트엔드도 바뀐다. 빈 장바구니와 없는 장바구니를 구별할 도메인 이유가 없고, 당시 프론트엔드는 초기 로딩이 `Promise.all`이라 404 하나로 화면 전체가 시드 데이터로 바뀌는 상태였다.

## 규칙으로 남긴 것

[CLAUDE.md](../CLAUDE.md)에 두 줄을 추가했다.

- **조회는 저장소에 쓰지 않는다.** 없는 것을 만들어 넣는 `getOrCreate` 류를 저장소 포트에 두지 않고, 기본값 생성은 애플리케이션이 한다.
- **조회로 얻은 객체를 변형한 것만으로 저장됐다고 가정하지 않는다.** 변경했으면 저장을 명시적으로 호출한다.

## 미해결·후속

- `Reservation`은 대역에서도 참조를 그대로 보관한다. id를 생성자에서 생성하므로 스냅샷에서 복원할 수 없다. 예약은 원래부터 `saveReservation`을 명시적으로 부르고 있어 위험이 낮다고 판단해 미뤘다.
- `InventoryService`의 메서드가 `synchronized`다. 단일 프로세스와 맵을 가정한 동기화이므로, DB로 옮기면 낙관적 락이나 `SELECT ... FOR UPDATE`로 대체해야 한다.
- `Order`가 계약 타입인 `AddressResponse`를 도메인 필드로 들고 있다. 영속화하면 DTO가 그대로 스키마가 된다. 도메인 전용 값 객체로 분리할지 JSON 컬럼으로 둘지 확정 필요.

## 참고

- [CartRepository](../apps/cart-service/src/main/java/com/impati/commerce/cart/application/port/out/CartRepository.java), [CartService](../apps/cart-service/src/main/java/com/impati/commerce/cart/application/component/CartExecutor.java)
- [InventoryRepository](../apps/inventory-service/src/main/java/com/impati/commerce/inventory/application/port/out/InventoryRepository.java), [InventoryService](../apps/inventory-service/src/main/java/com/impati/commerce/inventory/application/component/InventoryExecutor.java)
- [InventoryServiceTest](../apps/inventory-service/src/test/java/com/impati/commerce/inventory/application/component/InventoryExecutorTest.java) — `DetachedInventoryRepository`
