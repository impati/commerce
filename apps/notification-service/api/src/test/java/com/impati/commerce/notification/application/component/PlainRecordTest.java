package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Channel;
import com.impati.commerce.notification.domain.NotificationModels.DeliveryStatus;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기록만 남기는 알림은 발송 대상이 아니다 (PD-0009-R2).
 *
 * <p>받는 쪽 테스트다. 예전에는 발송 테스트와 한 파일에 있었지만, 수신과 발송이 다른 실행
 * 단위가 되면서 갈렸다 (ADR-0015).
 */
@SpringBootTest
@RequiresDatabase
class PlainRecordTest {
    @Autowired
    private NotificationUseCase notificationUseCase;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void plainRecordIsNotQueuedForDelivery() {
        notificationUseCase.record("OrderPaid", "mem_plain", "Order paid",
                "Order ord_plain has been paid.", "evt_plain");

        var recorded = notificationRepository.findAll().stream()
                .filter(candidate -> "Order paid".equals(candidate.subject()))
                .findFirst()
                .orElseThrow();
        assertThat(recorded.channel()).isEqualTo(Channel.NONE);
        assertThat(recorded.deliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }
}
