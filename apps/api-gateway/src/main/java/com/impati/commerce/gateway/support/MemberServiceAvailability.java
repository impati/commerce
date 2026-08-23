package com.impati.commerce.gateway.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * member-service가 장애인지 판정한다.
 *
 * <p>열린 동안 게이트웨이는 만료된 접근 토큰도 상한까지 받아준다 (PD-0014-R9, ADR-0008).
 * 그래서 이 판정은 "member-service가 아프다"가 아니라 <b>"지금 폐기 반영이 늦어도 되는가"</b>를
 * 정하는 것이다. 잘못 열리면 폐기가 조용히 늦어지므로 여는 조건을 좁게 잡는다.
 *
 * <p><b>전송 실패와 5xx만 실패로 센다.</b> 폐기된 세션의 갱신 거절(401·404)은 정상 동작이며,
 * 그것을 실패로 세면 로그아웃한 사용자 몇 명이 전체를 완화 모드로 민다.
 *
 * <p>인스턴스마다 각자 판단한다. 판정을 공유하려면 저장소나 브로커가 필요하고, 각자 자기가 겪은
 * 실패로 판단하는 것이 서킷브레이커의 통상적인 모양이다. 대가는 ADR-0008의 남은 위험에 있다.
 *
 * <p>닫는 것은 이 클래스가 하지 않는다. 열린 동안에는 갱신 트래픽 자체가 사라지므로 — 게이트웨이가
 * 만료 토큰을 통과시키니 클라이언트가 갱신하지 않는다 — 바깥의 프로브가 성공을 알려줘야 닫힌다.
 */
@Component
public class MemberServiceAvailability {
    private static final Logger log = LoggerFactory.getLogger(MemberServiceAvailability.class);

    private final int failureThreshold;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean unavailable = new AtomicBoolean();

    public MemberServiceAvailability(@Value("${gateway.member-service.failure-threshold}") int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    /** 장애로 판정된 상태인가. 만료 완화와 프로브 구동이 이 값을 본다. */
    public boolean isUnavailable() {
        return unavailable.get();
    }

    /** member-service가 응답했다. 실패 누적을 지우고, 열려 있었다면 닫는다. */
    public void recordReachable() {
        consecutiveFailures.set(0);
        if (unavailable.compareAndSet(true, false)) {
            log.info("member-service reachable again; expiry tolerance disabled");
        }
    }

    /** member-service에 닿지 못했거나 5xx를 받았다. 한도를 채우면 연다. */
    public void recordUnreachable() {
        if (consecutiveFailures.incrementAndGet() < failureThreshold) {
            return;
        }
        if (unavailable.compareAndSet(false, true)) {
            log.warn("member-service unreachable {} times in a row; tolerating expired access tokens",
                    failureThreshold);
        }
    }
}
