# 백로그

지금 구현하지 않는 문제와 작업 후보를 항목별로 남긴다. 각 항목은 `BL-NNNN` 추적 ID를 갖고, 그 ID가 백로그 파일과 작업 브랜치·커밋·리뷰를 잇는다.

항목을 추가할 때는 `capture-backlog` 스킬을 쓴다. 작업을 시작할 때는 `BL-NNNN 작업 시작`으로 `start-work` 스킬을 부른다.

## 다른 문서와의 경계

| 위치 | 담는 것 |
| --- | --- |
| `docs/backlog/` | 앞으로 할 후보. 배경과 목표만 |
| `docs/adr/` | 무엇을 왜 골랐나. 재검토 조건까지 |
| `problem/` | 겪은 문제와 기술 조사. 사용자가 요청할 때만 쓴다 |
| `docs/` | 설계 — 무엇을 만드는가 |
| `CLAUDE.md` | 작업 룰 — 어떻게 작업하는가 |
| `CHANGELOG.md` | 무엇을 왜 바꿨나 |

코드에 붙는 부채는 백로그가 아니라 코드의 `TODO` 주석에 남긴다. 정본은 grep이다.

```bash
make todo
```

## 지금 우선순위

위에서부터 먼저 한다. 이 순서는 항목 파일에 적지 않는다 — 순서는 바뀌고 항목은 남는다.

| 순서 | 항목 | 왜 이 순서인가 |
| --- | --- | --- |
| 1 | [BL-0001](bl-0001-gateway-bypass-block.md) 게이트웨이 우회 차단 | 인증을 붙여놓고 뒷문이 열려 있다. 다른 어떤 작업보다 먼저다 |
| 2 | [BL-0002](bl-0002-verification-token-referer-leak.md) 인증 링크 토큰의 Referer 유출 | 토큰이 외부 호스트로 나가는 경로가 실재한다 |
| 3 | [BL-0003](bl-0003-session-lookup-coupling.md) 세션 확인의 가용성 결합과 지연 | 한 서비스 장애가 전 서비스 인증 장애가 된다 |
| 4 | [BL-0004](bl-0004-member-service-outbox.md) member-service 아웃박스 | 서비스 간 호출이 DB 트랜잭션 안에 있다 |
| 5 | [BL-0005](bl-0005-token-storage-to-cookie.md) 프론트 토큰 보관을 쿠키로 | XSS로 토큰이 읽힌다 |
| 6 | [BL-0006](bl-0006-local-profile-smoke.md) local 프로파일 스모크 | 하네스가 못 보는 구간이다. 급하지 않다 |
| 7 | [BL-0007](bl-0007-password-policy-to-domain.md) 비밀번호 정책을 도메인으로 | 클래스 리뷰에서 나왔다. 지금 동작에는 문제가 없다 |
| 8 | [BL-0008](bl-0008-duplicate-signup-conflict-response.md) 동시 가입 경합 응답 | 위와 같다 |

## 순서를 정하지 않은 항목

정해야 할 때 정한다.

| 항목 | 성격 |
| --- | --- |
| [BL-0009](bl-0009-split-money-per-domain.md) Money 도메인별 분리 | 합의는 됐고 둘 위치가 미정 |
| [BL-0010](bl-0010-identifier-collision-risk.md) 식별자 생성 방식 | PK라 미루면 비싸진다 |
| [BL-0011](bl-0011-flow-encapsulation-direction.md) 흐름 캡슐화 방향 | 설계 방향 결정. ADR 대상 |
| [BL-0012](bl-0012-split-runtime-modules.md) 실행 모듈 분리 | BL-0001과 함께 판단 |
| [BL-0013](bl-0013-test-isolation-strategy.md) 테스트 격리 방식 | 지금은 작성자 규율에 의존 |
| [BL-0014](bl-0014-verify-docker-compose-path.md) docker-compose 경로 검증 | 한 번도 실행해보지 않았다 |
| [BL-0015](bl-0015-pre-commit-scans-working-tree.md) pre-commit이 working tree를 본다 | 알려진 한계였다 |
| [BL-0016](bl-0016-display-card-price-mismatch.md) 지면 카드 가격 | 알려진 한계였다 |
