# ADR (기술 결정 기록)

무엇을 골랐고 왜 골랐는지, 그리고 어떤 조건에서 다시 볼지를 남긴다. 코드를 읽어도 알 수 없는 것이 여기 있다 — 채택하지 않은 대안과 그 이유다.

파일명은 `NNNN-kebab-case-title.md`이고 번호는 이어서 부여한다. 작성 규약과 템플릿은 `start-work` 스킬의 `references/adr-convention.md`를 따른다.

**결정을 뒤집을 때는 기존 문서를 고치지 않는다.** 새 번호를 만들어 이전 결정을 대체(supersede)한다고 적는다. 고쳐버리면 왜 뒤집었는지가 사라진다. 같은 작업을 구현하는 중 추가로 승인받은 결정만 기존 ADR의 `추가 결정`에 덧붙인다.

## 다른 문서와의 경계

| 위치 | 담는 것 |
| --- | --- |
| `docs/adr/` | 무엇을 왜 골랐나. 재검토 조건까지 |
| `docs/backlog/` | 앞으로 할 후보. 배경과 목표만 |
| `problem/` | 겪은 문제와 기술 조사. 사용자가 요청할 때만 쓴다 |
| `docs/` | 설계 — 무엇을 만드는가 |
| `CLAUDE.md` | 작업 룰 — 어떻게 작업하는가 |
| `git log` | 무엇을 왜 했나. 이 저장소는 `CHANGELOG.md`를 두지 않는다 |

## 목록

| # | 결정 | 상태 |
| --- | --- | --- |
| [0001](0001-session-token-strategy.md) | 로그인 세션을 불투명 토큰으로 유지한다 | 대체됨 ([0007](0007-hybrid-session-tokens.md)) |
| [0002](0002-network-segmentation-as-trust-boundary.md) | 서비스 간 신뢰 경계를 네트워크 분리로 강제한다 | 승인됨 |
| [0003](0003-internal-path-prefix.md) | 노출하지 않을 경로를 `/internal` 프리픽스로 선언한다 | 승인됨 |
| [0004](0004-split-authorization-and-capture.md) | 결제를 승인과 매입으로 나누고 매입을 체크아웃 마지막에 둔다 | 승인됨 |
| [0005](0005-payment-gateway-port.md) | 결제 대행사를 포트로 분리하고 거절을 값으로 표현한다 | 승인됨 |
| [0006](0006-verification-token-in-url-fragment.md) | 이메일 확인 토큰을 URL 프래그먼트로 옮긴다 | 승인됨 |
| [0007](0007-hybrid-session-tokens.md) | 세션을 단명 서명 토큰과 장수명 세션으로 나눈다 | 승인됨 |
| [0008](0008-outage-tolerant-identity.md) | 장애로 판정되는 동안 만료된 접근 토큰을 받는다 | 승인됨 |
| [0009](0009-reconcile-unconfirmed-payments.md) | 결제 미확인 주문을 점유 임차로 정리한다 | 대체됨 ([0018](0018-durable-checkout-recovery.md)) |
| [0010](0010-verification-mail-outbox.md) | 인증 메일 발송을 아웃박스로 분리한다 | 승인됨 |
| [0011](0011-deliver-verification-mail-once.md) | 인증 메일을 한 번만 보낸다 | 승인됨 |
| [0012](0012-order-events-as-outbox.md) | 주문 상태 전이를 사건으로 커밋하고 발행한다 | 승인됨 |
| [0013](0013-real-database-in-the-harness.md) | 실제 DB 위에서 검증한다 | 승인됨 |
| [0014](0014-split-api-and-worker-modules.md) | 실행 단위를 API와 워커로 나눈다 | 승인됨 |
| [0015](0015-outbound-adapters-per-collaborator.md) | 나가는 어댑터를 협력자별 모듈로 나눈다 | 승인됨 |
| [0016](0016-publish-order-events-to-kafka.md) | 주문 사건을 카프카로 발행한다 | 승인됨 |
| [0017](0017-publish-timeout-and-relay-isolation.md) | 발행 제한시간과 릴레이 실행 격리를 둔다 | 승인됨 |
| [0018](0018-durable-checkout-recovery.md) | 체크아웃 진행 상태를 저장하고 API 실행을 워커가 복구한다 | 승인됨 |
| [0019](0019-single-rest-client-factory.md) | 공용 팩토리가 서비스 간 HTTP 클라이언트를 만든다 | 승인됨 |
| [0020](0020-browser-session-cookie.md) | 브라우저 세션을 HttpOnly 쿠키로 운반한다 | 승인됨 |
| [0021](0021-translate-service-call-failures.md) | 서비스 간 호출 실패를 호출자 문맥으로 번역한다 | 승인됨 |
| [0022](0022-member-order-history.md) | 회원 주문 내역을 복합 커서와 고객용 조회 모델로 제공한다 | 승인됨 |
| [0023](0023-storefront-bff.md) | Storefront BFF가 화면 데이터를 조합한다 | 승인됨 |
| [0024](0024-cart-line-management.md) | 확인한 장바구니 버전으로 줄을 수정한다 | 승인됨 |
| [0025](0025-versioned-shipping-address-management.md) | 주소록 버전으로 관리하고 확인한 배송 정보로 주문한다 | 승인됨 |
| [0026](0026-order-price-breakdown.md) | 주문이 금액 구성을 확정하고 모든 주문 화면에 제공한다 | 승인됨 |

## ADR이 없는 합의

문서가 생기기 전에 정해져 지금도 유효한 방향이다. **재논의하지 않는다** — 뒤집으려면 새 근거가 필요하고, 뒤집을 때 ADR을 쓴다.

| 주제 | 결정 |
| --- | --- |
| 이메일 유일성 | 대소문자를 구분한다. 중복 계정 위험은 소유 인증으로 막는다 |
| 메일 발송 소유 | notification-service. 벤더 지식이 한 서비스에만 모인다 |
| 퍼블릭 API | `memberId`를 받지 않는다. 신원은 세션에서만 온다 |
| 테스트 상한 | `@SpringBootTest` + HTTP stub. 프로세스를 띄우는 e2e는 만들지 않는다 |
| 인프라 | 실제 외부 호출과 실 DB는 붙이지 않는다. 어댑터만 갈아끼우면 나갈 수 있는 상태가 목표 |
