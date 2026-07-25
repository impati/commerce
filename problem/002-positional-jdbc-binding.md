# 위치 기반 JDBC 바인딩과 왕복 테스트의 사각지대

작성일 2026-07-25 / 관련 커밋 `880df33`

## 배경

order-service를 영속화할 때 `JdbcTemplate`에 위치 기반 `?`로 14개 컬럼을 바인딩했다. 컬럼을 하나 추가하면 고칠 곳이 여섯이다. 마이그레이션, INSERT 컬럼 목록, `?` 개수, INSERT 인자, UPDATE, RowMapper. 하나를 빠뜨리거나 순서를 밀어 쓰면 컴파일러도 DB도 잡지 못한다. `ship_*` 컬럼이 varchar 7개 연속이라 인접한 값이 뒤바뀌어도 타입 오류가 나지 않는다.

여기까지는 SQL을 직접 쓰는 방식의 알려진 비용이다. 실제로 확인해 보니 상황이 더 나빴다. `city`와 `postalCode`를 쓰기와 읽기 양쪽에서 대칭으로 뒤바꾼 뒤 테스트를 돌렸다.

| 상태 | 테스트 결과 |
| --- | --- |
| 정상 | 7개 통과 |
| INSERT와 RowMapper에서 city ↔ postalCode 대칭 교체 | **7개 통과** |

복원된 객체는 멀쩡하다. 쓸 때 틀린 것을 읽을 때 같은 방향으로 틀려서 되돌려 놓기 때문이다. 다만 DB의 `ship_city` 컬럼에는 우편번호가, `ship_postal_code`에는 도시명이 들어 있다. 그 컬럼으로 조회하거나 다른 시스템이 읽으면 그때 드러난다.

교체를 INSERT에만 넣고 UPDATE는 그대로 뒀는데도 통과했다. `save`는 UPDATE를 먼저 시도하고 0이면 INSERT하므로, 같은 컬럼의 값이 저장 횟수에 따라 달라지는 상태였다.

즉, 문제는 위치 기반 바인딩이 실수하기 쉽다는 데 있지 않다. 그 실수가 하네스의 사각지대에 있어서, 통과한 초록불이 매핑의 정확성을 전혀 보증하지 못한다는 데 있다. 따라서 사고 유형 자체를 없애는 바인딩 방식과, 왕복이 아닌 검증 수단이 함께 필요하다.

## 범위가 아닌 것

ORM 선택을 다시 논의하지 않는다. SQL을 직접 쓰는 방향은 유지한다. 여기서 다루는 것은 그 방식의 매핑 정확성을 어떻게 보장하느냐다.

## 왜 왕복 테스트로는 안 되는가

`저장 → 조회 → 비교`는 대칭 오류에 눈이 없다. 쓰기 경로와 읽기 경로가 같은 잘못된 매핑을 공유하면 결과가 상쇄된다. 이 구조에서 왕복 테스트가 실제로 검증하는 것은 "쓰기와 읽기가 서로 일관된가"이며, "각 값이 자기 컬럼에 들어갔는가"가 아니다.

앞서 [problem/001](001-read-triggers-write.md)에서 인메모리 어댑터가 `saveStock` 누락을 탐지하지 못한 것과 같은 계열이다. 테스트 대역이나 검증 방식이 검증 대상과 같은 가정을 공유하면 그 가정이 틀렸을 때 아무것도 걸러지지 않는다.

## 조치

`NamedParameterJdbcTemplate`과 `MapSqlParameterSource`로 전환했다. 컬럼 이름과 값이 한 줄에서 짝지어지므로 위치가 밀려서 값이 어긋나는 일이 구조적으로 생기지 않는다.

```java
.addValue("ship_line1", address.line1())
.addValue("ship_city", address.city())
.addValue("ship_postal_code", address.postalCode())
```

`select *`도 걷어내고 컬럼 목록을 명시했다. 컬럼이 추가될 때 결과셋 모양이 말없이 바뀌는 것을 막는다.

이름 바인딩만으로는 절반만 해결된다. 값을 잘못된 이름에 넣는 실수는 여전히 가능하다. 그래서 컬럼을 직접 읽어 확인하는 테스트를 함께 뒀다.

```java
var row = jdbc.queryForMap(
        "select ship_city, ship_postal_code from orders where id = ?", order.id());
assertThat(row.get("SHIP_CITY")).isEqualTo("Seoul");
assertThat(row.get("SHIP_POSTAL_CODE")).isEqualTo("04524");
```

같은 대칭 교체를 다시 넣으면 이제 `writesEachAddressFieldToItsOwnColumn`이 실패한다. 조치 전후를 같은 실수로 비교한 결과다.

| 상태 | 왕복 테스트만 | 컬럼 검증 포함 |
| --- | --- | --- |
| 대칭 교체 | 통과 | 실패 |

## 검토했으나 채택하지 않은 대안

- jOOQ: 스키마에서 타입 있는 컬럼 참조를 생성하므로 컬럼 이름 변경이 컴파일 실패로 드러난다. 유일하게 컴파일 타임 보장을 준다. 다만 코드 생성 단계가 들어오면서 스키마와 빌드 순서가 결합되고, `./gradlew test` 하나가 유일한 신호라는 지금의 하네스 단순함이 깨진다. 레퍼런스 프로젝트 규모에서 값을 못 한다고 판단했다.
- Spring Data JDBC: 매핑이 선언적이라 SQL을 안 써도 된다. 다만 엔티티 모델을 요구해서 도메인 모델을 그 형태로 맞추거나 별도 엔티티를 두게 되고, 애초에 JPA를 피한 이유가 일부 돌아온다.
- `SimpleJdbcInsert`: 컬럼 목록을 코드에서 관리하지 않아 INSERT는 깔끔해진다. UPDATE를 여전히 직접 써야 해서 두 경로의 방식이 갈라진다.
- Mockito로 바인딩 호출을 verify: 어떤 값이 어떤 이름으로 전달됐는지는 잡지만, DB에 실제로 그 컬럼으로 들어갔는지는 확인하지 못한다. 검증 지점이 한 단계 앞이다.

## 규칙으로 남긴 것

[CLAUDE.md](../CLAUDE.md)에 두 줄을 추가했다.

- **JDBC는 이름 바인딩만 쓴다.** 위치 기반 `?`와 `select *`를 쓰지 않는다.
- **컬럼 매핑은 왕복 테스트로 검증되지 않는다.** 컬럼 값을 직접 읽는 테스트를 함께 둔다.

이 패턴을 남은 서비스에 복제할 예정이었으므로, 굳기 전에 고치는 것이 쌌다. 위치 기반 `?`로 굳었다면 같은 사고 유형이 여덟 서비스에 퍼진 뒤에 발견됐을 것이다.

## 미해결·후속

- 이름 바인딩도 컬럼 누락은 잡지 못한다. 마이그레이션에 컬럼을 추가하고 코드에서 쓰지 않으면 아무 신호가 없다. 컴파일 타임에 잡는 방법은 jOOQ뿐이며 도입 여부는 미정이다.
- 컬럼 검증 테스트가 서비스마다 비슷한 모양으로 반복된다. 공용 헬퍼로 뺄지, 반복을 그대로 둘지 정하지 않았다.

## 확인해서 사실이 아니었던 것

이 문서 초안에는 "`queryForMap`의 키가 H2에서 대문자로 오므로 다른 DB로 옮기면 컬럼 검증 테스트가 깨진다"고 적혀 있었다. 확인하지 않고 쓴 내용이며 사실이 아니다.

`JdbcTemplate.queryForMap`은 `ColumnMapRowMapper`를 쓰고, 이 매퍼는 `LinkedCaseInsensitiveMap`을 만들어 돌려준다. 키 조회가 대소문자를 구분하지 않는다. `row.get("order_id")`로 바꿔 실행해도 통과하는 것을 확인했다.

## 참고

- [JdbcOrderRepository](../apps/order-service/src/main/java/com/impati/commerce/order/adapter/out/persistence/JdbcOrderRepository.java)
- [JdbcOrderRepositoryTest](../apps/order-service/src/test/java/com/impati/commerce/order/adapter/out/persistence/JdbcOrderRepositoryTest.java) — `writesEachAddressFieldToItsOwnColumn`
- [problem/001](001-read-triggers-write.md) — 검증 수단이 검증 대상과 가정을 공유해 아무것도 걸러내지 못한 앞선 사례
