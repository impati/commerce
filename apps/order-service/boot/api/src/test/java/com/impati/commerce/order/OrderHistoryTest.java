package com.impati.commerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.CheckoutProgress;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.test.RequiresDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RequiresDatabase
class OrderHistoryTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderChanges orderChanges;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderEventRepository orderEventRepository;
    @Autowired CheckoutProgressRepository checkoutProgressRepository;
    @Autowired JdbcTemplate jdbc;

    /** [PD-0020-R6, PD-0020-R9] 동률과 새 주문 삽입에도 페이지는 중복·누락 없이 회원 조건을 유지한다. */
    @Test
    void pagesSameTimestampWithoutDuplicatesOrOtherMembers() throws Exception {
        var member = unique("mem");
        var prefix = unique("ord");
        var instant = Instant.parse("2026-09-16T03:00:00.123456Z");
        var a = save(prefix + "_a", member, instant, "Product A");
        var b = save(prefix + "_b", member, instant, "Product B");
        var c = save(prefix + "_c", member, instant, "Product C");
        var older = save(prefix + "_older", member, instant.minusSeconds(1), "Older");
        save(unique("ord"), unique("mem"), instant, "Other member");

        var first = mockMvc.perform(get("/orders").header("X-Member-Id", member).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(c.id()))
                .andExpect(jsonPath("$.items[1].id").value(b.id()))
                .andExpect(jsonPath("$.items[0].representativeProductName").value("Product C"))
                .andReturn();
        var cursor = objectMapper.readTree(first.getResponse().getContentAsString()).path("nextCursor").asText();
        save(unique("ord"), member, instant.plusSeconds(1), "New during paging");
        mockMvc.perform(get("/orders").header("X-Member-Id", member).param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(a.id()))
                .andExpect(jsonPath("$.items[1].id").value(older.id()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    /** [PD-0020-R1, PD-0020-R2, PD-0020-R3, PD-0020-R4] outcome FAILED만으로 보상 중을 확정 실패로 표시하지 않는다. */
    @Test
    void exposesEveryAcceptedOrderUsingCustomerStates() throws Exception {
        var member = unique("mem");
        for (var stage : new String[] { "ACCEPTED", "COMPENSATING", "ATTENTION_REQUIRED", "COMPLETED", "FAILED" }) {
            var order = save(unique("ord"), member, Instant.parse("2026-09-16T03:00:00Z"), stage);
            if (stage.equals("COMPLETED")) {
                order.attachPayment("pay_private"); order.markPaid(); order.attachShipment("shp_private", "TRK-visible");
            } else if (!stage.equals("ACCEPTED")) order.failCheckout("private internal error");
            orderChanges.commit(order);
            progress(order, stage);
            var expected = switch (stage) {
                case "COMPLETED" -> "SUCCEEDED";
                case "FAILED" -> "FAILED";
                case "ATTENTION_REQUIRED" -> "CHECKING";
                default -> "PROCESSING";
            };
            var response = mockMvc.perform(get("/orders/{id}", order.id()).header("X-Member-Id", member))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutResult").value(expected))
                    .andExpect(jsonPath("$.orderStatus").value(stage.equals("COMPLETED") ? "FULFILLING" : null))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("private internal error", "private error", "PRIVATE_CODE");
            if (stage.equals("FAILED")) assertThat(response).contains("CHECKOUT_FAILED");
            else assertThat(response).doesNotContain("CHECKOUT_FAILED");
        }
        mockMvc.perform(get("/orders").header("X-Member-Id", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
    }

    /** [PD-0020-R5, PD-0020-R7, PD-0020-R8] 내용·시각·운송장만 노출하고 발행 상태와 내부 payload는 보내지 않는다. */
    @Test
    void showsSnapshotAndTimelineRegardlessOfPublishState() throws Exception {
        var member = unique("mem");
        var created = Instant.parse("2026-09-16T03:00:00.123456Z");
        var order = save(unique("ord"), member, created, "Snapshot product");
        // 복원한 주문도 주입된 시계로 실제 전이 시각을 기록해야 한다.
        order = Order.restore(order.id(), member, order.lines(), order.priceBreakdown(), order.shippingAddress(), order.status(),
                null, null, null, order.createdAt(), Clock.fixed(created.plusSeconds(60), ZoneOffset.UTC));
        order.attachReservation("rsv_private"); order.attachPayment("pay_private"); order.markPaid();
        order.attachShipment("shp_private", "TRK-visible"); orderChanges.commit(order);
        progress(order, "COMPLETED");
        var event = orderEventRepository.findByOrderIdAndMemberId(order.id(), member).getFirst();
        event.markFailed("private publisher failure", 1); orderEventRepository.savePublishResult(event);

        var response = mockMvc.perform(get("/orders/{id}", order.id()).header("X-Member-Id", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderedAt").value("2026-09-16T03:00:00.123456Z"))
                .andExpect(jsonPath("$.lines[0].productName").value("Snapshot product"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal.amount").value(20_000))
                .andExpect(jsonPath("$.priceBreakdown.productAmount.amount").value(25_000))
                .andExpect(jsonPath("$.priceBreakdown.shippingFee.amount").value(3_000))
                .andExpect(jsonPath("$.priceBreakdown.totalAmount.amount").value(28_000))
                .andExpect(jsonPath("$.total.amount").value(28_000))
                .andExpect(jsonPath("$.shippingAddress.recipient").value("Snapshot Recipient"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-visible"))
                .andExpect(jsonPath("$.timeline[0].type").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.timeline[1].type").value("ORDER_PAID"))
                .andExpect(jsonPath("$.timeline[1].occurredAt").value("2026-09-16T03:01:00.123456Z"))
                .andExpect(jsonPath("$.timeline[2].type").value("SHIPMENT_CREATED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("pay_private", "shp_private", "rsv_private", "publishStatus",
                "attempts", "lastError", "failureCode", "private publisher failure", "memberId");
        assertThat(jdbc.queryForObject("select cast(created_at as char) from orders where id = ?", String.class, order.id()))
                .isEqualTo("2026-09-16 03:00:00.123456");
        assertThat(jdbc.queryForObject("select cast(occurred_at as char) from order_events where id = ?", String.class, event.id()))
                .isEqualTo("2026-09-16 03:00:00.123456");
        assertThat(orderRepository.findById(order.id()).orElseThrow().createdAt()).isEqualTo(order.createdAt());
        mockMvc.perform(get("/orders").header("X-Member-Id", member))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(3))
                .andExpect(jsonPath("$.items[0].additionalProductCount").value(1))
                .andExpect(jsonPath("$.items[0].priceBreakdown.productAmount.amount").value(25_000))
                .andExpect(jsonPath("$.items[0].priceBreakdown.shippingFee.amount").value(3_000))
                .andExpect(jsonPath("$.items[0].priceBreakdown.totalAmount.amount").value(28_000))
                .andExpect(jsonPath("$.items[0].total.amount").value(28_000));

        order = Order.restore(order.id(), member, order.lines(), order.priceBreakdown(), order.shippingAddress(), order.status(),
                order.paymentId(), order.shipmentId(), order.inventoryReservationId(), order.createdAt(),
                Clock.fixed(created.plusSeconds(120), ZoneOffset.UTC));
        order.markDelivered();
        orderChanges.commit(order);
        mockMvc.perform(get("/orders/{id}", order.id()).header("X-Member-Id", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutResult").value("SUCCEEDED"))
                .andExpect(jsonPath("$.orderStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.orderedAt").value("2026-09-16T03:00:00.123456Z"))
                .andExpect(jsonPath("$.timeline[3].type").value("ORDER_DELIVERED"))
                .andExpect(jsonPath("$.timeline[3].occurredAt").value("2026-09-16T03:02:00.123456Z"));
    }

    /** [PD-0020-R9] 타 회원 주문의 존재도 알아낼 수 없다. */
    @Test
    void treatsAnotherMembersOrderExactlyLikeMissingOrder() throws Exception {
        var order = save(unique("ord"), unique("mem"), Instant.now(), "Private");
        var requester = unique("mem");
        var other = mockMvc.perform(get("/orders/{id}", order.id()).header("X-Member-Id", requester))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = mockMvc.perform(get("/orders/{id}", "ord_missing").header("X-Member-Id", requester))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(other).isEqualTo(missing);
        assertThat(orderEventRepository.findByOrderIdAndMemberId(order.id(), requester)).isEmpty();
    }

    @Test
    void handlesEmptyPagesAndRejectsInvalidPagingInput() throws Exception {
        var member = unique("mem");
        mockMvc.perform(get("/orders").header("X-Member-Id", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.nextCursor").isEmpty());
        for (var size : new String[] { "0", "-1", "101" }) {
            mockMvc.perform(get("/orders").header("X-Member-Id", member).param("size", size)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/orders").header("X-Member-Id", member).param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest());
    }

    private Order save(String id, String member, Instant at, String product) {
        var order = Order.create(id, member, List.of(
                new OrderLine("sku_a", "prd_a", product, "Option A", 2, Money.krw(10_000)),
                new OrderLine("sku_b", "prd_b", "Second product", "Option B", 1, Money.krw(5_000))),
                new Address("addr_snapshot", "home", "Snapshot Recipient", "010-1234-5678", "Snapshot road", "Seoul", "12345", true),
                Clock.fixed(at, ZoneOffset.UTC));
        orderChanges.commit(order);
        return order;
    }

    private void progress(Order order, String stage) {
        var outcome = stage.equals("COMPLETED") ? "SUCCEEDED" : stage.equals("ACCEPTED") ? "PROCESSING" : "FAILED";
        checkoutProgressRepository.insertIfAbsent(CheckoutProgress.restore(order.id(), order.memberId(), unique("key"),
                "a".repeat(64), null, 1, stage, outcome, "PRIVATE_CODE", "REFUNDING", "private error",
                "rsv_private", "pay_private", "shp_private", "TRK-visible", null, null, 0));
    }

    private static String unique(String prefix) { return prefix + "_" + UUID.randomUUID().toString().replace("-", ""); }
}
