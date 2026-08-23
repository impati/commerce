package com.impati.commerce.gateway.adapter.in.scheduler;

import com.impati.commerce.gateway.adapter.out.client.GatewayClients;
import com.impati.commerce.gateway.support.MemberServiceAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장애로 판정된 동안 member-service의 복구를 확인한다.
 *
 * <p>이것이 없으면 판정이 풀리지 않는다. 브레이커가 열리면 게이트웨이가 만료된 접근 토큰을
 * 통과시키므로 클라이언트가 갱신을 시도하지 않고, 갱신이 유일한 신호였다면 열린 순간 신호가
 * 끊긴다. 그러면 member-service가 되살아나도 완화 상태가 상한까지 이어져, 정책이 "장애로
 * 판정되는 동안"으로 한정한 완화가 장애가 끝난 뒤에도 계속된다 (ADR-0008).
 *
 * <p><b>보호하는 동작을 그대로 부른다.</b> 갱신 경로에 아무 토큰이나 넣어 보내고, 무엇이든
 * 답이 오면 닿은 것이다 — 살아 있으면 "그런 세션 없음"으로 거절하고 죽어 있으면 전송이 실패한다.
 * 판정은 {@link GatewayClients#refresh}가 이미 기록하므로 여기서는 부르고 삼키기만 한다.
 * "닿았다"의 정의가 한 곳에만 있게 하려는 것이다.
 *
 * <p>다른 엔드포인트를 프로브하지 않는 이유가 있다. 여는 신호와 닫는 신호가 서로 다른 코드
 * 경로면 한쪽만 고장 났을 때 판정이 어긋난다. 세션 저장소가 막혀 갱신만 실패하는 경우가 그렇다.
 *
 * <p>닫혀 있을 때는 아무 일도 하지 않는다. 정상 상태에서는 갱신 트래픽이 이미 신호를 준다.
 */
@Component
public class MemberServiceProbe {
    private static final Logger log = LoggerFactory.getLogger(MemberServiceProbe.class);

    /** 어떤 세션과도 일치하지 않는 값. 갱신 경로를 태우기 위한 것이며 성공을 기대하지 않는다. */
    private static final String PROBE_TOKEN = "probe-token-never-issued";

    private final GatewayClients clients;
    private final MemberServiceAvailability memberServiceAvailability;

    public MemberServiceProbe(GatewayClients clients, MemberServiceAvailability memberServiceAvailability) {
        this.clients = clients;
        this.memberServiceAvailability = memberServiceAvailability;
    }

    @Scheduled(fixedDelayString = "${gateway.member-service.probe-interval}")
    public void probe() {
        if (!memberServiceAvailability.isUnavailable()) {
            return;
        }
        try {
            clients.refresh(PROBE_TOKEN);
        } catch (RuntimeException answered) {
            // 거절도 답이다. 닿았는지 아닌지는 refresh가 이미 판정에 반영했다.
            log.debug("member-service probe returned: {}", answered.getMessage());
        }
    }
}
