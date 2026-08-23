# 세션 확인 실패가 인증 실패로 전달되어 사용자를 로그아웃시킨다

- **ID:** BL-0043
- **기록일:** 2026-08-23

## 배경

게이트웨이가 요청마다 member-service에 세션을 물어 신원을 확인한다 ([ADR-0001](../adr/0001-session-token-strategy.md)). 그 호출이 실패했을 때 실패를 **세션이 유효하지 않다**로 옮긴다.

```java
// MemberIdentity.require
} catch (RestClientResponseException exception) {
    throw unauthorized();   // 4xx도 5xx도 전부 401
}
```

`RestClientResponseException`은 4xx와 5xx를 모두 포함하므로 member-service가 500을 내면 게이트웨이는 401을 답한다. 연결 실패와 타임아웃은 `ResourceAccessException`이라 이 catch에 걸리지 않고, 게이트웨이의 `ApiExceptionHandler`도 그 타입을 다루지 않아 500으로 나간다.

**프론트가 두 경우 모두 세션 토큰을 지운다.** 첫 화면 진입에서 조건 없는 `catch`가 `session.clear()`를 부른다. 세션 자체는 서버에 14일 살아 있는데 클라이언트가 버린다.

| member-service 상태 | 게이트웨이 응답 | 프론트 결과 |
| --- | --- | --- |
| 정상 | 200 | 정상 |
| 5xx | 401 | 토큰 삭제 → 재로그인 |
| 다운·타임아웃 | 500 | 토큰 삭제 → 재로그인 |

기본 읽기 타임아웃이 3초이므로 member-service가 잠깐 느려지기만 해도 그 순간 페이지를 연 사용자 전원이 로그아웃된다. 장애가 끝나도 자동으로 복구되지 않는다 — 토큰이 이미 사라졌기 때문이다.

원인은 **요청의 문제와 우리 쪽의 문제를 구분할 자리가 없다는 것**이다. [BL-0037](bl-0037-downstream-failure-reported-as-conflict.md)이 형제 서비스 호출에서 지적한 것과 같은 계열이며, 그쪽은 `conflict`로 새고 여기서는 `unauthorized`로 샌다.

게이트웨이 테스트는 두 개뿐이고 이 경로를 아무것도 검증하지 않는다.

## 목표

member-service의 장애가 사용자에게 인증 실패로 전달되지 않는다. 세션이 유효하지 않은 것과 세션을 확인할 수 없는 것이 상태 코드로 구분되고, 확인할 수 없는 동안에도 사용자의 세션이 유지되어 장애가 끝나면 그대로 이어진다.

세션 확인의 가용성 결합 자체는 이 항목의 대상이 아니다. 그것은 [BL-0003](bl-0003-session-lookup-coupling.md)이며, 캐시냐 하이브리드냐를 정하고 폐기 즉시성을 얼마나 포기할지를 함께 결정해야 한다. 이 항목은 어느 방향을 고르든 남는 결함을 먼저 없앤다 — 캐시를 붙여도 미스와 만료에서 같은 코드가 돈다.
