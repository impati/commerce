package com.impati.commerce.gateway.adapter.in.scheduler;

import com.impati.commerce.gateway.support.MemberServiceAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 장애로 판정된 동안 member-service의 복구를 확인한다.
 *
 * <p>이것이 없으면 판정이 풀리지 않는다. 브레이커가 열리면 게이트웨이가 만료된 접근 토큰을
 * 통과시키므로 클라이언트가 갱신을 시도하지 않고, 갱신이 유일한 신호였다면 열린 순간 신호가
 * 끊긴다. 그러면 member-service가 되살아나도 완화 상태가 상한까지 이어져, 정책이 "장애로
 * 판정되는 동안"으로 한정한 완화가 장애가 끝난 뒤에도 계속된다 (ADR-0008).
 *
 * <p>닫혀 있을 때는 아무 일도 하지 않는다. 정상 상태에서 주기적으로 부르면 얻는 것 없이 부하만
 * 는다 — 그때는 갱신 트래픽이 이미 신호를 준다.
 */
@Component
public class MemberServiceProbe {
    private static final Logger log = LoggerFactory.getLogger(MemberServiceProbe.class);

    private final RestClient members;
    private final MemberServiceAvailability memberServiceAvailability;

    public MemberServiceProbe(RestClient memberRestClient, MemberServiceAvailability memberServiceAvailability) {
        this.members = memberRestClient;
        this.memberServiceAvailability = memberServiceAvailability;
    }

    @Scheduled(fixedDelayString = "${gateway.member-service.probe-interval}")
    public void probe() {
        if (!memberServiceAvailability.isUnavailable()) {
            return;
        }
        try {
            members.get().uri("/actuator/health").retrieve().toBodilessEntity();
            memberServiceAvailability.recordReachable();
        } catch (RuntimeException stillDown) {
            log.debug("member-service probe failed: {}", stillDown.getMessage());
        }
    }
}
