package com.impati.commerce.order;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄 진입점을 자원으로 격리한다 (ADR-0017).
 *
 * <p>사건 릴레이는 브로커가 아플 때 한 주기가 스레드를 오래 붙잡는다. 그것은 브로커가 아플 때의
 * 정당한 배압이지만, 기본 스케줄러가 스레드 하나라 릴레이가 붙잡히면 같은 스케줄러의 결제
 * 미확인 정리도 함께 굶는다. 서로 무관해야 할 두 축이 자원으로 결합한다.
 *
 * <p>릴레이에 전용 스케줄러를 주고 나머지는 기본에 남긴다. 릴레이의 배압이 릴레이 안에 갇힌다.
 * 스케줄러 빈이 여럿이면 이름이 {@code taskScheduler}인 것이 기본으로 쓰이므로, 릴레이만
 * {@code @Scheduled(scheduler=...)}로 전용 빈을 가리킨다.
 */
@Configuration
public class WorkerSchedulingConfig {

    /** 릴레이를 제외한 스케줄 진입점(결제 미확인 정리 등)의 기본 스케줄러. */
    @Bean
    TaskScheduler taskScheduler() {
        return scheduler("order-worker-scheduler-");
    }

    /** 사건 릴레이 전용. 릴레이의 배압이 여기 갇혀 다른 진입점을 굶기지 않는다. */
    @Bean
    TaskScheduler orderEventRelayScheduler() {
        return scheduler("order-event-relay-");
    }

    private static TaskScheduler scheduler(String threadNamePrefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        return scheduler;
    }
}
