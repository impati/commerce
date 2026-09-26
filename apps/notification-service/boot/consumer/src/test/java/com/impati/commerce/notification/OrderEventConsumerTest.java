package com.impati.commerce.notification;

import com.impati.commerce.common.ApiContracts.OrderEventMessage;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.RequiresKafka;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * 사건이 실제 브로커를 거쳐 알림이 되는지 확인한다 (ADR-0016).
 *
 * <p>대역이 아니라 실물을 쓰는 이유는 검증 대상이 브로커의 동작이기 때문이다 — 직렬화가 맞는지,
 * 리스너가 붙는지, 같은 사건이 두 번 와도 한 줄인지. 임베디드는 이것을 흉내만 낸다.
 */
@SpringBootTest
@RequiresDatabase
@RequiresKafka
class OrderEventConsumerTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @SpyBean
    private NotificationUseCase notificationUseCase;

    @Value("${commerce.kafka.order-events-topic}")
    private String topic;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void recordsANotificationFromAPaidEvent() {
        var event = new OrderEventMessage(
                "evt_consume_paid", "ORDER_PAID", "ord_consume_1", "mem_consume_1", Map.of());

        send(event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf("mem_consume_1"))
                        .extracting(subject -> subject)
                        .containsExactly("Order paid"));
    }

    /** 사건에 실린 사실이 문구에 들어간다. 문구를 만드는 것은 발행자가 아니라 소비자다. */
    @Test
    void rendersTheTrackingNumberFromThePayload() {
        var event = new OrderEventMessage(
                "evt_consume_shipped", "SHIPMENT_REGISTERED", "ord_consume_2", "mem_consume_2",
                Map.of("trackingNumber", "TRK-CONSUME"));

        send(event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(bodiesOf("mem_consume_2")).anyMatch(body -> body.contains("TRK-CONSUME")));
    }

    /**
     * 같은 사건이 두 번 와도 알림은 한 줄이다.
     *
     * <p>브로커가 at-least-once이므로 중복은 정상이다. 그것을 없애는 것은 결과를 아는 수신측의
     * 일이며, 사건 id가 멱등 키다.
     */
    @Test
    void theSameEventTwiceRecordsOneNotification() {
        var event = new OrderEventMessage(
                "evt_consume_dup", "ORDER_DELIVERED", "ord_consume_3", "mem_consume_3", Map.of());

        send(event);
        send(event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf("mem_consume_3")).hasSize(1));
        // 잠시 더 기다려도 늘지 않는다. 두 번째가 늦게 도착해 뒤늦게 한 줄을 더 만들면 안 된다.
        assertThat(notificationsOf("mem_consume_3")).hasSize(1);
    }

    /**
     * 알림이 없는 사건은 흘려보낸다.
     *
     * <p>사건을 남기는 기준은 소비자가 아니라 상태 전이이므로 소비자가 없는 사건이 있는 것은
     * 정상이다. 모르는 종류도 같다 — 발행자가 새 사건을 더하는 것이 소비자를 깨뜨리면 안 된다.
     */
    @Test
    void anEventWithNoNotificationIsSkipped() {
        send(new OrderEventMessage(
                "evt_consume_created", "ORDER_CREATED", "ord_consume_4", "mem_consume_4", Map.of()));
        send(new OrderEventMessage(
                "evt_consume_unknown", "SOMETHING_NEW", "ord_consume_4", "mem_consume_4", Map.of()));
        send(new OrderEventMessage(
                "evt_consume_cancel_requested", "ORDER_CANCELLATION_REQUESTED", "ord_consume_4", "mem_consume_4", Map.of()));
        // 뒤이어 온 것이 처리되면 앞의 둘이 오프셋을 막지 않았다는 뜻이다.
        send(new OrderEventMessage(
                "evt_consume_after", "ORDER_PAID", "ord_consume_4", "mem_consume_4", Map.of()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf("mem_consume_4")).containsExactly("Order paid"));
    }

    /** [PD-0024-R9] 요청 중간 사건은 알리지 않고 완료 사건만 고객 알림이 된다. */
    @Test
    void notifiesOnlyWhenCustomerCancellationCompletes() {
        send(new OrderEventMessage(
                "evt_cancel_requested", "ORDER_CANCELLATION_REQUESTED", "ord_cancel", "mem_cancel", Map.of()));
        send(new OrderEventMessage(
                "evt_cancel_completed", "ORDER_CANCELLED", "ord_cancel", "mem_cancel",
                Map.of("reason", "CUSTOMER_REQUESTED")));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf("mem_cancel")).containsExactly("Order cancellation completed"));
    }

    /** 체크아웃 실패는 고객 취소 완료와 다른 사건·문구를 사용한다. */
    @Test
    void distinguishesCheckoutFailureFromCustomerCancellation() {
        send(new OrderEventMessage(
                "evt_checkout_failed", "CHECKOUT_FAILED", "ord_failed", "mem_failed", Map.of()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf("mem_failed")).containsExactly("Purchase failed"));
    }

    /** [PD-0027-R12] 접수·환불 시작·완료·운영 확인은 각각 독립된 고객 알림이다. */
    @Test
    void notifiesEachObservableReturnStage() {
        var memberId = "mem_return_notifications";
        send(new OrderEventMessage("evt_return_accepted", "RETURN_ACCEPTED", "ord_return", memberId, Map.of()));
        send(new OrderEventMessage("evt_return_refund", "RETURN_REFUND_STARTED", "ord_return", memberId, Map.of()));
        send(new OrderEventMessage("evt_return_attention", "RETURN_ATTENTION_REQUIRED", "ord_return", memberId, Map.of()));
        send(new OrderEventMessage("evt_return_completed", "RETURN_COMPLETED", "ord_return", memberId, Map.of()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf(memberId)).containsExactlyInAnyOrder(
                        "Return accepted", "Return picked up and refund started",
                        "Return needs confirmation", "Return completed"));
    }

    /**
     * 처리에 실패하면 오프셋이 올라가지 않고 같은 레코드가 다시 온다.
     *
     * <p>이것이 없으면 실패한 사건이 사라진다. 스프링 카프카의 <b>기본 동작이 그쪽</b>이라 —
     * 몇 번 재시도한 뒤 로그만 남기고 오프셋을 올린다 — 아무것도 하지 않으면 조용한 유실을
     * 고른 것이 된다 (ADR-0016).
     *
     * <p>재시도가 무한이므로 여기서 한 번만 실패시킨다. 영구 실패를 넣으면 이 테스트가 끝나지
     * 않는데, 그 <b>끝나지 않는 것이 의도한 동작</b>이다.
     */
    @Test
    void aFailedRecordComesBackInsteadOfBeingSkipped() {
        var failedOnce = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (failedOnce.compareAndSet(false, true)) {
                throw new IllegalStateException("database down");
            }
            return invocation.callRealMethod();
        }).when(notificationUseCase).record(any(), eq("mem_consume_5"), any(), any(), any());

        send(new OrderEventMessage(
                "evt_consume_retry", "ORDER_PAID", "ord_consume_5", "mem_consume_5", Map.of()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationsOf("mem_consume_5")).containsExactly("Order paid"));
        assertThat(failedOnce).isTrue();
    }

    private List<String> notificationsOf(String memberId) {
        return notificationRepository.findByMemberId(memberId).stream()
                .map(notification -> notification.subject())
                .toList();
    }

    private List<String> bodiesOf(String memberId) {
        return notificationRepository.findByMemberId(memberId).stream()
                .map(notification -> notification.body())
                .toList();
    }

    private void send(OrderEventMessage event) {
        try (var producer = new KafkaProducer<String, OrderEventMessage>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName()
        ))) {
            producer.send(new ProducerRecord<>(topic, event.orderId(), event)).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("produce interrupted", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("produce failed", failure);
        }
    }
}
