# 게이트웨이 우회 차단

- **ID:** BL-0001
- **기록일:** 2026-08-16

## 배경

서비스가 8101~8109로 직접 노출돼 있다. 위험이 둘이다.

**쓰기 방향** — 게이트웨이를 건너뛰고 `X-Member-Id`를 위조하면 아무 회원으로 행세할 수 있다.

**읽기 방향** — 게이트웨이가 TLS를 끝내면 그 뒤는 평문 HTTP다. 이 구간에 접근할 수 있으면 `Authorization` 헤더와 `X-Member-Id`를 그대로 읽는다. 암호화가 애초에 없는 구간이므로 토큰 탈취 경로 중 가장 현실적이다.

인증을 붙여놓고 뒷문이 열려 있는 상태다. [ADR-0001](../adr/0001-session-token-strategy.md)은 게이트웨이가 유일한 진입점이라는 전제 위에 서 있는데, 그 전제가 코드로 강제되지 않는다.

관련 코드: [MemberIdentity](../../apps/api-gateway/src/main/java/com/impati/commerce/gateway/support/MemberIdentity.java), [InternalMemberController](../../apps/member-service/src/main/java/com/impati/commerce/member/adapter/in/web/InternalMemberController.java)

## 목표

게이트웨이를 우회한 요청이 하위 서비스의 내부 경로에 닿지 못한다.

로컬에서 표현할 수 있는 방법은 서비스 간 공유 시크릿 헤더 검증이다. 게이트웨이가 붙이고 서비스가 확인한다. member-service는 내부 전용 경로를 `/members/internal/**` 하나로 모아뒀으므로 그 프리픽스에 걸면 되고, 다른 서비스도 같은 모양으로 모아야 한다. compose 네트워크에서 포트 노출을 제거하는 것은 로컬 프로세스 실행에서는 표현되지 않는다. 운영이라면 내부 구간도 mTLS로 암호화하는 것이 답이다.

[BL-0054](../bl-0054-split-api-and-worker-modules.md)의 member external/internal 분리가 이 목표의 대안 해법이기도 하다 — 공유 시크릿은 코드로 막는 것이고 별도 프로세스는 네트워크로 막는 것이라 후자가 강하다. 같은 일을 두 번 하지 않도록 함께 판단한다.
