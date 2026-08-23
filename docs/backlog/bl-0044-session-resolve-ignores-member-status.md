# 세션 확인이 회원 상태를 보지 않는다

- **ID:** BL-0044
- **기록일:** 2026-08-23

## 배경

게이트웨이가 신원을 확인할 때 부르는 [SessionExecutor.resolveSession](../../apps/member-service/src/main/java/com/impati/commerce/member/application/component/SessionExecutor.java)이 세션 자신의 상태만 본다.

```java
var session = sessionRepository.findByTokenHash(secureTokens.hash(rawToken))
        .filter(candidate -> candidate.isUsable(clock.instant()))   // 폐기 여부와 만료만 본다
        .orElseThrow(() -> DomainException.notFound("session is not valid"));
return new SessionOwner(session.memberId());
```

`isUsable`은 `revokedAt == null && now <= expiresAt`이다. **회원이 어떤 상태인지는 조회하지도 않는다.**

지금 드러나지 않는 이유는 회원을 막을 수단이 없기 때문이다. `Member`의 상태는 `PENDING_VERIFICATION`과 `ACTIVE` 둘이고 전이는 `activate()` 한 방향뿐이다. 차단도 탈퇴도 없으므로 "막힌 회원의 세션이 살아 있다"는 상황 자체가 성립하지 않는다.

**문제는 그 수단을 붙이는 순간 조용히 성립한다는 것이다.** 차단이나 탈퇴를 추가하면 상태는 바뀌는데 이미 발급된 세션은 그대로 통과한다. 최대 14일([PD-0002-R4](../policy/pd-0002-login-rejection-and-session-lifetime.md)) 동안 차단된 계정으로 주문할 수 있다. 세션을 함께 폐기하는 코드를 잊지 않고 넣어야만 막히는데, 그것은 규율에 의존하는 방식이고 규율은 샌다.

폐기 지연 정책과는 별개다. 상한을 5분으로 잡든 0으로 잡든, **반영되는 경로가 없으면 아무 시간이 지나도 반영되지 않는다.**

## 목표

회원이 더 이상 서비스를 쓸 수 없는 상태가 되면 그 회원의 기존 세션이 통하지 않는다. 그것이 세션을 폐기해서인지 확인 시점에 상태를 봐서인지, 그리고 그 판단을 어디가 갖는지가 정해져 있다.

차단·탈퇴 기능 자체를 만드는 것은 이 항목의 범위가 아니다. 만들 때 이 구멍에 빠지지 않도록 확인 경로를 먼저 세우는 것이 목표다.
