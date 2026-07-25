# CLAUDE.md

Impati Commerce 작업 규칙. Java 21 + Spring Boot 3.2 멀티모듈, 10개 서비스로 쪼갠 이커머스 레퍼런스.

서비스 목록/포트/API는 [README.md](README.md), 경계 설계는 [docs/domain-map.md](docs/domain-map.md)와 [docs/architecture.md](docs/architecture.md)를 본다. **이 문서에 중복 기재하지 않는다** — 두 벌이 되면 반드시 어긋난다.

## 의사결정 기준

**현재 코드의 미완성 상태를 설계 근거로 쓰지 않는다.** 이 저장소는 예제 수준에서 출발했고 고도화 중이다. "지금은 로그인이 없으니", "메일을 안 보내니", "아직 인메모리니" 같은 문장은 판단의 근거가 될 수 없다. 그건 고쳐야 할 대상이지 전제가 아니다.

판단은 **실제 운영 서비스라면 어떻게 되어야 하는가**로 한다. 그 결론이 지금 코드와 다르면, 다르다고 강하게 말하고 무엇을 바꿔야 하는지 제시한다. 결론을 현재 상태에 맞춰 깎지 않는다.

만들 범위는 이렇게 나눈다.

| | 지금 만든다 | 나중에 붙인다 |
| --- | --- | --- |
| 도메인·응용 계층 | **완성 상태로** | |
| 포트(인터페이스) | **완성 상태로** | |
| 외부 시스템 어댑터 (메일, 결제사 등) | 로컬 대역 구현 | 실제 벤더 구현 |
| 인프라 (실 DB, 메시지 브로커) | 로컬 대역 | 운영 인스턴스 |

즉 **어댑터만 갈아끼우면 운영에 나갈 수 있는 상태**를 목표로 한다. 외부 호출을 안 하는 것은 어댑터 선택의 결과여야 하고, 도메인이나 응용 계층이 그 사실을 알아서는 안 된다.

개선점이 보이면 요청받지 않아도 즉시 보고한다. 타임아웃 부재처럼 지금 동작에는 문제가 없지만 운영에서 사고가 되는 것들이 대상이다.

## 검증 (하네스)

이 저장소에서 "동작한다"의 유일한 근거는 아래 명령의 종료 코드다.

```bash
./gradlew test
```

- 작업을 끝냈다고 보고하기 전에 반드시 실행한다. 실행하지 않았으면 "테스트하지 않았다"고 명시한다.
- 실패하면 실패 출력을 그대로 보고한다. 통과했다고 요약하지 않는다.
- **파이프로 감싸지 말 것.** `./gradlew test | tail -30`은 `tail`의 종료 코드를 반환해서 BUILD FAILED를 0으로 보고한다. 파이프가 필요하면 `set -o pipefail`을 먼저 쓴다.
- 프론트엔드를 건드렸으면 `make frontend-build`까지 확인한다.
- CI가 없다. 자동 게이트는 [scripts/git-hooks/pre-commit](scripts/git-hooks/pre-commit) 하나뿐이고, 이건 커밋할 때만 돈다. 커밋하지 않는 작업에는 아무 안전망이 없으므로 직접 돌린다.
- 훅은 `core.hooksPath` 설정이 필요하다. 새 clone에서는 `make setup-hooks`를 한 번 실행한다. `--no-verify`로 만든 커밋은 검증되지 않은 커밋이다.

## 테스트를 채우는 규칙

하네스는 테스트가 있는 만큼만 작동한다. 현재 커버리지는 얇으므로, 코드를 건드릴 때마다 조금씩 메운다.

- **도메인/애플리케이션 로직을 바꾸면** 해당 모듈에 단위 테스트를 함께 추가하거나 갱신한다. 참고: [OrderModelsTest](apps/order-service/src/test/java/com/impati/commerce/order/domain/OrderModelsTest.java), [InventoryServiceTest](apps/inventory-service/src/test/java/com/impati/commerce/inventory/application/InventoryServiceTest.java)
- **서비스 경계나 checkout saga에 닿는 변경이면** `@SpringBootTest` 시나리오를 추가한다. 기준 패턴은 [CheckoutSagaTest](apps/order-service/src/test/java/com/impati/commerce/order/CheckoutSagaTest.java): 대상 서비스만 실제로 띄우고, 다른 서비스 호출은 `MockServerRestClientCustomizer` + `MockRestServiceServer`로 stub한다. 컨트롤러 → 애플리케이션 → 클라이언트 → JSON 직렬화까지는 실제 코드가 돈다.
- **프로세스를 실제로 띄우는 e2e는 만들지 않는다.** `@SpringBootTest` + HTTP stub 수준까지가 이 프로젝트의 합의된 상한이다. [scripts/demo-checkout.sh](scripts/demo-checkout.sh)는 수동 확인용 데모이며 검증 수단이 아니다 (assert가 없다).
- **모든 서비스는 최소한 컨텍스트 로드 테스트를 갖는다** (`XxxApplicationTest.contextLoads`). 빈이 빠지거나 둘로 늘어나거나 설정값이 없으면 여기서 깨진다. 서비스별 시나리오 테스트가 생기면 지워도 된다.
- 보상/롤백 경로는 성공 경로와 **같은 비중으로** 테스트한다. 이 아키텍처에서 실제로 깨지는 곳이 거기다.
- 테스트를 새로 짰거나 크게 고쳤으면, 검증 대상 로직을 일부러 망가뜨려 테스트가 실패하는지 한 번 확인하고 원복한다. 통과만 확인한 테스트는 통과만 하는 테스트일 수 있다.

## 경계 규칙

- `apps/*` 모듈은 서로 직접 의존하지 않는다. 서비스 간 통신은 HTTP뿐이다.
- 서비스 간 주고받는 타입은 [ApiContracts](libs/common-contracts/src/main/java/com/impati/commerce/common/ApiContracts.java)에 record로 정의한다. 한쪽 서비스에만 있는 DTO를 따로 만들지 않는다.
- **`XxxRequest`/`XxxResponse`는 응용 계층 DTO 네이밍이다.** 도메인은 이 이름을 쓰지 않고 필드 타입으로도 갖지 않는다. 도메인 ↔ 계약 변환은 응용 계층의 매퍼가 맡는다. 선례: [OrderMapper](apps/order-service/src/main/java/com/impati/commerce/order/application/OrderMapper.java), [MemberModels.Address](apps/member-service/src/main/java/com/impati/commerce/member/domain/MemberModels.java). 예외는 `Money` 하나이며, 공용 값 타입을 어디에 둘지는 아직 정하지 않았다.
- 에러는 `DomainException` 팩토리(`validation`/`notFound`/`conflict`/`paymentDeclined`)로 던지고, HTTP 상태 매핑은 각 서비스 `support/ApiExceptionHandler`가 담당한다. 컨트롤러에서 상태 코드를 직접 만들지 않는다.
- 패키지 구조는 `adapter/in/web`, `adapter/out/client`, `adapter/out/persistence`, `application`, `domain`, `support`를 따른다. 새 서비스도 같은 모양으로 만든다.
- **조회는 저장소에 쓰지 않는다.** 없는 것을 만들어 넣는 `getOrCreate` 류를 저장소 포트에 두지 말고, 기본값 생성은 애플리케이션이 한다. GET에 INSERT가 따라붙으면 읽기 복제본·캐시·헬스체크가 전부 망가진다.
- **다른 서비스 호출도 포트로만 쓴다.** 협력자별 인터페이스(`XxxClient`)를 `application`에 두고 HTTP 구현(`HttpXxxClient`)은 `adapter/out/client`에 둔다. 프로토콜 오류를 도메인 언어로 옮기는 것도 어댑터의 일이다 (예: 402 → `paymentDeclined`). 예외는 api-gateway로, 응용·도메인 계층이 없는 순수 어댑터라 뒤집을 대상이 없다.
- `RestClient.Builder`는 서비스당 한 번만 주입받아 복제한다. 어댑터마다 주입받으면 빌더가 어댑터 수만큼 생겨서 빌더 단위로 동작하는 테스트 스텁과 공통 커스터마이저가 갈라진다. 선례: [CommerceRestClients](apps/order-service/src/main/java/com/impati/commerce/order/adapter/out/client/CommerceRestClients.java)
- **저장소는 포트로만 쓴다.** 인터페이스(`XxxRepository`)는 `application`에 두고 구현은 `adapter/out/persistence`에 둔다. 애플리케이션 서비스가 `InMemoryXxxRepository` 같은 구현 타입을 직접 참조하면 안 된다 — 저장소를 갈아끼울 수 없게 된다.
- 서비스 간 HTTP 호출의 공통 정책(타임아웃 등)은 [libs/common-http](libs/common-http)의 auto-configuration에 둔다. 서비스마다 반복하면 반드시 어긋난다. `clients.http.connect-timeout`, `clients.http.read-timeout`으로 조정한다.
- 새 모듈을 추가하면 [settings.gradle](settings.gradle)에 `include`를 넣는다.

## 빌드 설정 주의

- 저장소 선언은 [settings.gradle](settings.gradle)의 `dependencyResolutionManagement`에만 둔다. `repositoriesMode`가 `FAIL_ON_PROJECT_REPOS`이므로 `build.gradle`에 `repositories { }`를 쓰면 **설정 평가 단계에서 빌드 전체가 죽는다.**
- 의존성은 mavenCentral로 해결되는 것만 쓴다. 사내 nexus 의존성을 추가하면 VPN 없는 환경에서 하네스가 못 돈다.
- 프론트 `package.json`이 버전을 `latest`로 잡고 있다. 재현성은 `package-lock.json`에만 걸려 있으므로 **lockfile을 반드시 커밋한다.** `npm install`로 메이저 버전이 올라가 빌드가 깨지면 tsconfig부터 확인한다.

## 상태와 데이터

- **10개 서비스 전부 H2 파일 DB를 쓴다** (저장소가 있는 8개. api-gateway와 display-service는 저장소가 없다). 인메모리 저장소는 남아 있지 않다.
- DB를 쓰는 서비스는 [build.gradle](build.gradle)의 `configure([...])` 목록에도 넣어야 jdbc/flyway/h2 의존성이 붙는다.
- `@SpringBootTest` 컨텍스트와 in-memory DB는 테스트 간에 공유된다. **빈 저장소를 가정하는 테스트를 쓰지 말고** 테스트가 자기 데이터를 직접 만들게 한다. 고정 id를 여러 테스트에서 쓰면 PK 충돌이 난다.
- DB를 쓰는 서비스의 테스트는 `@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:...")`로 URL만 덮어쓴다. `src/test/resources/application.properties`를 만들면 **main 쪽 파일을 가려서** `clients.*.url`이 사라지고 컨텍스트가 뜨지 않는다.
- 로컬 DB 파일은 `.data/`에 생기고 git에 올리지 않는다. 초기화는 `rm -rf .data`다. 경로가 상대경로이므로 저장소 루트에서 실행해야 한다.
- **JDBC는 이름 바인딩만 쓴다.** `NamedParameterJdbcTemplate` + `MapSqlParameterSource`를 쓰고, 위치 기반 `?`와 `select *`는 쓰지 않는다. 위치 바인딩은 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 컴파일러도 DB도 잡지 못한다.
- **컬럼 매핑은 왕복 테스트로 검증되지 않는다.** 저장 후 조회해서 비교하면 쓰기와 읽기가 같은 방향으로 틀렸을 때 그대로 통과한다. 컬럼 값을 직접 읽는 테스트를 함께 둔다. 배경은 [problem/002](problem/002-positional-jdbc-binding.md)에 있다.
- 스키마 변경은 Flyway 마이그레이션으로 한다. 파일 DB는 데이터가 남으므로 `schema.sql`을 다시 돌리는 방식은 깨진다.
- **시드는 멱등해야 한다.** 파일 DB는 데이터가 남으므로 `ApplicationRunner` 시드가 재시작마다 다시 실행되면 데이터가 늘어난다. 넣기 전에 이미 있는지 확인한다.
- 데모 시드 데이터(`mem_demo`, `sku_*`, `card_test_success`, `card_test_decline`)는 README에 정리돼 있다. 시드를 바꾸면 README와 프론트 `src/mockData.ts`도 같이 고친다.

## 되돌리기

git 저장소이지만 이력이 `first commit` 하나뿐이다. 되돌릴 지점이 사실상 없으므로:

- 여러 파일을 지우거나 크게 재구성하기 전에 체크포인트 커밋을 만들 것을 제안한다.
- 작업을 끝내면 `git status`와 `git diff`로 의도한 파일만 바뀌었는지 확인한다.
- 커밋과 푸시는 사용자가 요청할 때만 한다.
- `git status`를 리뷰 신호로 쓴다. `.idea/`는 추적하지 않으므로 diff에 나오는 것은 전부 의도한 변경이어야 한다.

## 문서와 룰의 배치

세 곳의 역할이 다르다. 섞으면 아무도 읽지 않는 문서가 생긴다.

| 위치 | 담는 것 | 읽는 주체 |
| --- | --- | --- |
| `docs/` | 설계 — 무엇을 만드는가 | 사람. 에이전트는 링크를 타고 필요할 때만 |
| `CLAUDE.md` | 작업 룰 — 어떻게 작업하는가 | 에이전트가 매 세션 자동으로 읽음 |
| `.claude/` | 도구 설정 — settings, hooks, skills | Claude Code 런타임 |

- **자동으로 읽히는 것은 이 파일뿐이다.** `.claude/` 아래에 룰 문서를 만들면 읽히지 않는다. 룰은 여기에 쓴다.
- **이 파일은 짧게 유지한다.** 매 세션 컨텍스트를 차지하고, 길어지면 사람도 에이전트도 지키지 않는다. 설명이 길어진 룰은 상세를 `docs/`로 빼고 여기엔 한 줄과 링크만 남긴다.
- 단계가 여러 개인 반복 절차(새 서비스 추가 등)는 룰로 늘리지 말고 `.claude/skills/`로 뺀다.
- 매번 반드시 지켜져야 하는 것은 문장이 아니라 훅으로 만든다. 텍스트 룰은 새고, 훅은 새지 않는다.
- `.claude/settings.local.json`은 개인 설정이며 git에 올라가지 않는다. 팀이 공유할 설정은 `.claude/settings.json`에 둔다.

## 이 문서에 대한 규칙 (메타룰)

여기 적힌 룰이 실제 작업과 충돌하면, **조용히 우회하지 않는다.** 작업을 멈추고 어긋난 지점을 사용자에게 말한 뒤 룰 수정안을 제시한다. 룰이 현실보다 낡은 것도, 현실이 룰을 어긴 것도 둘 다 고칠 대상이다.

룰을 바꿨으면 아래 이력에 한 줄 남긴다.

## 변경 이력

- 2026-07-25 — "의사결정 기준"을 맨 앞에 추가. 현재 코드의 미완성 상태를 설계 근거로 쓰지 않고 운영 기준으로 판단한다. 계기: 이메일 대소문자 정책을 논의할 때 "지금은 로그인이 없으니 관용의 이득이 없다"는 식으로 미완성 상태를 근거로 삼은 것을 사용자가 지적했다.
- 2026-07-25 — 저장소 현황을 "10개 서비스 전부 H2 파일 DB"로 갱신. 재고 동시성은 `select for update` + check 제약으로 처리한다. 계기: 마지막 서비스(inventory) 영속화 완료.
- 2026-07-25 — 시드 멱등성 규칙 추가. 계기: catalog 시드가 재시작마다 상품 3개를 다시 만들어 파일 DB에서 계속 늘어나는 상태였다.
- 2026-07-25 — JDBC 이름 바인딩 규칙과 컬럼 단위 검증 규칙 추가. 계기: 위치 기반 `?`에서 city와 postalCode를 대칭으로 뒤바꿨는데 테스트 7개가 전부 통과했다.
- 2026-07-25 — 저장소 현황을 "모두 인메모리"에서 "order-service만 H2 파일"로 수정하고, DB 테스트의 프로퍼티 주입 방식과 Flyway 규칙 추가. 계기: order-service 영속화.
- 2026-07-25 — 도메인에서 `XxxRequest`/`XxxResponse` 금지 규칙 추가. 계기: `Order`와 `Shipment`가 계약 DTO인 `AddressResponse`를 도메인 필드로 들고 있어, 영속화하면 DTO가 그대로 스키마가 되는 상태였다.
- 2026-07-25 — "조회는 저장소에 쓰지 않는다" 규칙 추가. 계기: `CartService.get`이 `computeIfAbsent`로 조회만 해도 장바구니를 만들고 있었다.
- 2026-07-25 — 컨텍스트 로드 테스트 규칙 추가. 계기: 10개 서비스 중 8개는 스프링 컨텍스트가 깨져도 `./gradlew test`가 잡지 못하는 상태였다.
- 2026-07-25 — 저장소 포트 규칙 추가. 계기: 저장소를 인터페이스로 추상화하면서, 애플리케이션이 구현 타입을 직접 참조하면 갈아끼울 수 없다는 점을 규칙으로 못박을 필요가 생겼다.
- 2026-07-25 — 프론트 `latest` 의존성과 lockfile 주의 추가. 계기: `npm install`이 TypeScript 7을 끌어와 `make frontend-build`가 깨졌고, `@types/react`는 애초에 의존성에 없어 한 번도 통과한 적이 없었다.
- 2026-07-25 — "문서와 룰의 배치" 추가. pre-commit 훅을 자동 게이트로 도입하고 `.idea/` 추적 해제. 계기: 하네스 룰을 `.claude/` 아래 문서로 관리할지 논의하면서, 자동 로드되는 것은 CLAUDE.md뿐이라는 점을 명시할 필요가 생겼다.
- 2026-07-25 — "git 저장소가 아니다"를 "되돌리기"로 교체. 확인하지 않고 쓴 오류였고, 실제로는 커밋 1개짜리 git 저장소다.
- 2026-07-25 — 최초 작성. 검증 명령, 테스트 채우기 규칙, 경계 규칙, 빌드 설정 주의, 메타룰. 계기: `./gradlew test`가 `FAIL_ON_PROJECT_REPOS` 위반으로 두 달간 실행조차 되지 않던 상태를 발견하고 하네스를 세우기 시작.
