package com.impati.commerce.notification.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.out.MailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 사건 알림도 같은 요청을 여러 번 받으면 한 번만 기록하는지 확인한다 (ADR-0012).
 *
 * <p>발신측이 재시도를 붙이는 순간 이 구멍이 실제로 벌어진다. 인증 메일 경로는 ADR-0011이
 * 이미 닫았고, 여기서 닫는 것이 나머지 절반이다.
 *
 * <p>스케줄러가 배경에서 대기 항목을 집어가면 결과가 흔들리므로 주기를 아주 길게 둔다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-order-idempotent;DB_CLOSE_DELAY=-1",
        "notifications.dispatch-interval=3600000"
})
class IdempotentOrderEventReceiveTest {
    @Autowired
    private NotificationUseCase notificationUseCase;

    @MockBean
    private MailSender mailSender;

    /** 같은 키로 두 번 받아도 알림은 하나다. */
    @Test
    void sameKeyRecordsOnce() {
        var first = notificationUseCase.record(
                "OrderPaid", "mem_evt", "Order paid", "Order ord_1 has been paid.", "evt_paid_1");
        var second = notificationUseCase.record(
                "OrderPaid", "mem_evt", "Order paid", "Order ord_1 has been paid.", "evt_paid_1");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(notificationUseCase.listFor("mem_evt")).hasSize(1);
    }

    /**
     * 두 번째 요청은 먼저 기록된 것을 돌려준다.
     *
     * <p>발신자가 성공으로 읽고 종단 상태로 넘어가야 재시도 고리가 끝난다. 거절로 돌려주면
     * 발신측마다 "이 실패는 성공"이라는 예외 처리가 필요해지고, 그 처리를 빠뜨린 발신자는
     * 영원히 재시도한다.
     */
    @Test
    void duplicateReturnsTheFirstRecord() {
        var first = notificationUseCase.record(
                "OrderCancelled", "mem_evt_dup", "Order cancelled", "first body", "evt_cancel_1");
        var second = notificationUseCase.record(
                "OrderCancelled", "mem_evt_dup", "Order cancelled", "second body", "evt_cancel_1");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.body()).isEqualTo("first body");
    }

    /** 다른 사건은 다른 키이므로 각각 기록된다. */
    @Test
    void differentKeysRecordSeparately() {
        notificationUseCase.record("OrderPaid", "mem_evt_two", "Order paid", "body", "evt_two_paid");
        notificationUseCase.record(
                "ShipmentCreated", "mem_evt_two", "Shipment ready", "body", "evt_two_shipped");

        assertThat(notificationUseCase.listFor("mem_evt_two")).hasSize(2);
    }

    /**
     * 키가 없으면 거절한다.
     *
     * <p>선택값으로 두면 키를 빠뜨린 호출자가 조용히 예전 동작으로 돌아가고, 그 사실이 알림
     * 목록에 같은 사건이 두 줄로 쌓일 때까지 드러나지 않는다.
     */
    @Test
    void rejectsMissingKey() {
        assertThatThrownBy(() -> notificationUseCase.record(
                "OrderPaid", "mem_evt_nokey", "Order paid", "body", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> notificationUseCase.record(
                "OrderPaid", "mem_evt_nokey", "Order paid", "body", " "))
                .isInstanceOf(DomainException.class);

        assertThat(notificationUseCase.listFor("mem_evt_nokey")).isEmpty();
    }
}
