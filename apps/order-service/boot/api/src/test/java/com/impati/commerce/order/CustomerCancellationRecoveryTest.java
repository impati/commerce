package com.impati.commerce.order;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
import com.impati.commerce.common.ApiContracts.ReservationLine;
import com.impati.commerce.common.ApiContracts.ReservationResponse;
import com.impati.commerce.order.application.component.CancellationChanges;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.in.CancellationRecoveryUseCase;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.InventoryClient;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.PaymentClient;
import com.impati.commerce.order.application.port.out.ShippingClient;
import com.impati.commerce.order.domain.CancellationProgress;
import com.impati.commerce.order.domain.CancellationProgress.Stage;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.CheckoutRequestFingerprint;
import com.impati.commerce.order.domain.IdempotencyKey;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import com.impati.commerce.test.RequiresDatabase;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "orders.event-publish-interval=3600000")
@RequiresDatabase
@AutoConfigureMockMvc
class CustomerCancellationRecoveryTest {
    private static final String MEMBER_ID = "mem_cancel_recovery";
    private static final String RESERVATION_ID = "rsv_cancel_recovery";
    private static final String PAYMENT_ID = "pay_cancel_recovery";
    private static final String SHIPMENT_ID = "shp_cancel_recovery";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrderChanges orderChanges;
    @Autowired
    private CancellationChanges cancellationChanges;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CheckoutProgressRepository checkoutProgressRepository;
    @Autowired
    private CancellationProgressRepository cancellationProgressRepository;
    @Autowired
    private CancellationRecoveryUseCase recovery;

    @MockBean
    private InventoryClient inventoryClient;
    @MockBean
    private PaymentClient paymentClient;
    @MockBean
    private ShippingClient shippingClient;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from cancellation_progress");
        jdbc.update("delete from checkout_progress");
        jdbc.update("delete from order_events");
        jdbc.update("delete from order_lines");
        jdbc.update("delete from orders");
    }

    /** [PD-0024-R7] 저장된 각 취소 단계는 새 임차와 새 객체로 재개되어 하나의 완료 결과로 끝난다. */
    @ParameterizedTest
    @EnumSource(value = Stage.class, names = {"SHIPMENT_CANCELLED", "PAYMENT_REFUNDED", "INVENTORY_RESTORED"})
    void workerResumesEveryPersistedCancellationStage(Stage persistedStage) {
        var order = fulfilledOrder("ord_recover_" + persistedStage.name().toLowerCase());
        persistCancellationStage(order, persistedStage);
        stubRemainingEffects(order, persistedStage);

        assertThat(recovery.recover(10)).isEqualTo(1);

        assertThat(cancellationProgressRepository.findByOrderId(order.id()).orElseThrow().stage())
                .isEqualTo(Stage.COMPLETED);
        assertThat(orderRepository.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(jdbc.queryForList(
                "select type from order_events where order_id = ? order by seq", String.class, order.id()))
                .containsExactly("ORDER_CREATED", "ORDER_PAID", "SHIPMENT_CREATED",
                        "ORDER_CANCELLATION_REQUESTED", "ORDER_CANCELLED");
        verifyAlreadyCompletedEffectsAreNotRepeated(order.id(), persistedStage);
    }

    /** [PD-0024-R1] 완료되지 않은 체크아웃은 주문 상태가 갖춰져 있어도 고객 취소를 시작하지 않는다. */
    @Test
    void rejectsCancellationUnlessCheckoutCompletedSuccessfully() throws Exception {
        var order = fulfilledOrder("ord_checkout_not_completed");
        var checkout = new CheckoutProgress(order.id(), MEMBER_ID, new IdempotencyKey("checkout-not-completed"),
                new CheckoutRequestFingerprint("a".repeat(64)), "card_token", 1);
        assertThat(checkoutProgressRepository.insertIfAbsent(checkout)).isTrue();

        mockMvc.perform(post("/orders/{orderId}/cancellation", order.id()).header("X-Member-Id", MEMBER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cancellation_not_allowed"));

        assertThat(cancellationProgressRepository.findByOrderId(order.id())).isEmpty();
        verifyNoInteractions(shippingClient, paymentClient, inventoryClient);
    }

    private void persistCancellationStage(Order order, Stage stage) {
        var progress = new CancellationProgress(order.id(), MEMBER_ID);
        assertThat(cancellationProgressRepository.insertIfAbsent(progress)).isTrue();
        progress = cancellationProgressRepository.claim(order.id(), Duration.ofMinutes(1)).orElseThrow();
        order.requestCancellation();
        progress.advance(stage);
        cancellationChanges.commit(order, progress, progress.leaseGeneration());
        jdbc.update("update cancellation_progress set lease_until = date_sub(now(6), interval 1 second) where order_id = ?",
                order.id());
    }

    private void stubRemainingEffects(Order order, Stage stage) {
        if (stage == Stage.SHIPMENT_CANCELLED) {
            when(paymentClient.paymentForOrder(order.id())).thenReturn(Optional.of(new PaymentResponse(
                    PAYMENT_ID, order.id(), MEMBER_ID, order.total(), "CARD", "CAPTURED")));
            when(paymentClient.refundPayment(PAYMENT_ID)).thenReturn(new PaymentResponse(
                    PAYMENT_ID, order.id(), MEMBER_ID, order.total(), "CARD", "REFUNDED"));
        }
        if (stage == Stage.SHIPMENT_CANCELLED || stage == Stage.PAYMENT_REFUNDED) {
            when(inventoryClient.reservationForOrder(order.id())).thenReturn(Optional.of(new ReservationResponse(
                    RESERVATION_ID, order.id(), "COMMITTED", List.of(new ReservationLine("sku_recovery", 2)))));
        }
    }

    private void verifyAlreadyCompletedEffectsAreNotRepeated(String orderId, Stage stage) {
        verifyNoInteractions(shippingClient);
        if (stage == Stage.SHIPMENT_CANCELLED) {
            verify(paymentClient).paymentForOrder(orderId);
            verify(paymentClient).refundPayment(PAYMENT_ID);
        } else {
            verifyNoInteractions(paymentClient);
        }
        if (stage == Stage.SHIPMENT_CANCELLED || stage == Stage.PAYMENT_REFUNDED) {
            verify(inventoryClient).reservationForOrder(orderId);
            verify(inventoryClient).restoreReservation(RESERVATION_ID);
        } else {
            verifyNoInteractions(inventoryClient);
        }
    }

    private Order fulfilledOrder(String orderId) {
        var order = Order.create(orderId, MEMBER_ID,
                List.of(new OrderLine("sku_recovery", "prd_recovery", "Product", "Option", 2, Money.krw(10_000))),
                new Address("addr", "home", "Customer", "010", "1 Main", "Seoul", "04524", true),
                LocalDateTime.now());
        order.attachReservation(RESERVATION_ID);
        order.attachPayment(PAYMENT_ID);
        order.markPaid();
        order.attachShipment(SHIPMENT_ID, "TRK-recovery");
        orderChanges.commit(order);
        return order;
    }
}
