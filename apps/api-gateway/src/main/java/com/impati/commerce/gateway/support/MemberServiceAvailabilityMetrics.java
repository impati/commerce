package com.impati.commerce.gateway.support;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 장애 판정 상태를 지표로 내보낸다.
 *
 * <p>브레이커가 우리 쪽 결함으로 열린 채 방치되면 폐기가 조용히 상한만큼 늦어진다. 그 상황의
 * 대응책은 상한을 짧게 잡는 것이 아니라 이 지표에 경보를 거는 것이다 — 짧은 상한은 그 경우
 * "우리 결함으로 전원 로그아웃"을 만들고 정상적인 장기 장애에서도 사용자를 끊는다 (ADR-0008).
 *
 * <p>{@code /actuator/health}에는 넣지 않는다. 하위 의존성의 상태를 health에 넣으면 그것을 읽는
 * 오케스트레이터가 게이트웨이를 재시작하거나 로드밸런서에서 뺀다. member-service 장애가
 * 게이트웨이 장애로 번지는 것이며, 이 작업이 끊으려는 결합을 다른 경로로 되살린다.
 */
@Configuration
public class MemberServiceAvailabilityMetrics {
    @Bean
    Gauge memberServiceBreakerGauge(MeterRegistry registry, MemberServiceAvailability availability) {
        return Gauge.builder("gateway.member_service.unavailable", availability, it -> it.isUnavailable() ? 1 : 0)
                .description("1이면 member-service를 장애로 판정한 상태이며 만료된 접근 토큰을 상한까지 받는다")
                .register(registry);
    }
}
