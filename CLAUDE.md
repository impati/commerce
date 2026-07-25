# CLAUDE.md

Impati Commerce 작업 규칙. Java 21 + Spring Boot 3.2 멀티모듈, 10개 서비스로 쪼갠 이커머스 레퍼런스.

서비스 목록/포트/API는 [README.md](README.md), 경계 설계는 [docs/domain-map.md](docs/domain-map.md)와 [docs/architecture.md](docs/architecture.md)를 본다. **이 문서에 중복 기재하지 않는다** — 두 벌이 되면 반드시 어긋난다.

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
- 보상/롤백 경로는 성공 경로와 **같은 비중으로** 테스트한다. 이 아키텍처에서 실제로 깨지는 곳이 거기다.
- 테스트를 새로 짰거나 크게 고쳤으면, 검증 대상 로직을 일부러 망가뜨려 테스트가 실패하는지 한 번 확인하고 원복한다. 통과만 확인한 테스트는 통과만 하는 테스트일 수 있다.

## 경계 규칙

- `apps/*` 모듈은 서로 직접 의존하지 않는다. 서비스 간 통신은 HTTP뿐이다.
- 서비스 간 주고받는 타입은 [ApiContracts](libs/common-contracts/src/main/java/com/impati/commerce/common/ApiContracts.java)에 record로 정의한다. 한쪽 서비스에만 있는 DTO를 따로 만들지 않는다.
- 에러는 `DomainException` 팩토리(`validation`/`notFound`/`conflict`/`paymentDeclined`)로 던지고, HTTP 상태 매핑은 각 서비스 `support/ApiExceptionHandler`가 담당한다. 컨트롤러에서 상태 코드를 직접 만들지 않는다.
- 패키지 구조는 `adapter/in/web`, `adapter/out/client`, `adapter/out/persistence`, `application`, `domain`, `support`를 따른다. 새 서비스도 같은 모양으로 만든다.
- 새 모듈을 추가하면 [settings.gradle](settings.gradle)에 `include`를 넣는다.

## 빌드 설정 주의

- 저장소 선언은 [settings.gradle](settings.gradle)의 `dependencyResolutionManagement`에만 둔다. `repositoriesMode`가 `FAIL_ON_PROJECT_REPOS`이므로 `build.gradle`에 `repositories { }`를 쓰면 **설정 평가 단계에서 빌드 전체가 죽는다.**
- 의존성은 mavenCentral로 해결되는 것만 쓴다. 사내 nexus 의존성을 추가하면 VPN 없는 환경에서 하네스가 못 돈다.

## 상태와 데이터

- 모든 저장소는 `InMemory*Repository` (`ConcurrentHashMap`) 싱글턴이다. `@SpringBootTest` 컨텍스트는 캐시되므로 **테스트 간에 상태가 남는다.** 빈 저장소를 가정하는 테스트를 쓰지 말고, 테스트가 자기 데이터를 직접 만들게 한다.
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

- 2026-07-25 — "문서와 룰의 배치" 추가. pre-commit 훅을 자동 게이트로 도입하고 `.idea/` 추적 해제. 계기: 하네스 룰을 `.claude/` 아래 문서로 관리할지 논의하면서, 자동 로드되는 것은 CLAUDE.md뿐이라는 점을 명시할 필요가 생겼다.
- 2026-07-25 — "git 저장소가 아니다"를 "되돌리기"로 교체. 확인하지 않고 쓴 오류였고, 실제로는 커밋 1개짜리 git 저장소다.
- 2026-07-25 — 최초 작성. 검증 명령, 테스트 채우기 규칙, 경계 규칙, 빌드 설정 주의, 메타룰. 계기: `./gradlew test`가 `FAIL_ON_PROJECT_REPOS` 위반으로 두 달간 실행조차 되지 않던 상태를 발견하고 하네스를 세우기 시작.
