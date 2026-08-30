package com.impati.commerce.notification.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트가 시각을 앞으로 미는 시계.
 *
 * <p>점유와 재시도 간격이 전부 시각 판정이므로, 시계를 제어하지 않으면 "간격 전에는 다시 집히지
 * 않는다"를 실제 시간을 기다리지 않고 확인할 수 없다. 벽시계에 의존하는 단정은 느리거나 흔들린다.
 */
public class MutableClock extends Clock {
    private volatile Instant instant = Instant.parse("2026-08-29T00:00:00Z");

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    /** 시각을 앞으로 민다. 점유가 만료되게 하려면 재시도 간격보다 크게 민다. */
    public void advance(java.time.Duration amount) {
        instant = instant.plus(amount);
    }
}
