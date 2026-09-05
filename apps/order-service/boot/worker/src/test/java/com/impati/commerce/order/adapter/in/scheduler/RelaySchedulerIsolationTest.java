package com.impati.commerce.order.adapter.in.scheduler;

import com.impati.commerce.order.WorkerSchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 릴레이가 전용 스케줄러에서 돌고 결제 정리는 기본에 남는지 확인한다 (ADR-0017).
 *
 * <p>격리가 무너지는 방식은 조용하다 — 릴레이의 {@code scheduler} 지정이 사라지거나 전용 빈이
 * 없어지면 둘이 다시 한 스레드를 공유하고, 브로커 저하가 결제 정리를 굶긴다. 타이밍으로는
 * 그것을 안정적으로 잡을 수 없으므로 배선을 직접 고정한다.
 */
class RelaySchedulerIsolationTest {

    /** 릴레이는 전용 스케줄러를 가리킨다. */
    @Test
    void relayIsPinnedToItsOwnScheduler() throws NoSuchMethodException {
        var scheduled = OrderEventRelay.class.getDeclaredMethod("publish").getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler()).isEqualTo("orderEventRelayScheduler");
    }

    /**
     * 결제 정리는 스케줄러를 지정하지 않아 기본에 남는다.
     *
     * <p>릴레이만 옮기고 정리는 그대로 두는 것이 격리의 요점이다. 정리까지 전용 빈으로 옮기면
     * 무엇으로부터 격리한 것인지 사라진다.
     */
    @Test
    void reconciliationStaysOnTheDefaultScheduler() throws NoSuchMethodException {
        var scheduled = PaymentReconciliationScheduler.class
                .getDeclaredMethod("reconcile").getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler()).isEmpty();
    }

    /** 두 스케줄러가 실제로 갈린다 — 기본 하나와 릴레이 전용 하나, 스레드도 따로. */
    @Test
    void twoDistinctSchedulersAreWired() {
        new ApplicationContextRunner()
                .withUserConfiguration(WorkerSchedulingConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("taskScheduler");
                    assertThat(context).hasBean("orderEventRelayScheduler");
                    var relay = (ThreadPoolTaskScheduler) context.getBean("orderEventRelayScheduler");
                    var base = (TaskScheduler) context.getBean("taskScheduler");
                    assertThat(relay).isNotSameAs(base);
                    assertThat(relay.getThreadNamePrefix()).isEqualTo("order-event-relay-");
                });
    }
}
