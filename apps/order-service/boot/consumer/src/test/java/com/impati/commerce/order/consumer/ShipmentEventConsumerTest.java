package com.impati.commerce.order.consumer;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.ShipmentEventMessage;
import com.impati.commerce.order.application.component.OrderChanges;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.domain.OrderModels.Address;
import com.impati.commerce.order.domain.OrderModels.Order;
import com.impati.commerce.order.domain.OrderModels.OrderEventType;
import com.impati.commerce.order.domain.OrderModels.OrderLine;
import com.impati.commerce.order.domain.OrderModels.OrderStatus;
import com.impati.commerce.test.RequiresDatabase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDatabase
class ShipmentEventConsumerTest {
    @Autowired ShipmentEventConsumer consumer;
    @Autowired OrderChanges orderChanges;
    @Autowired OrderRepository orders;
    @Autowired OrderEventRepository events;

    @Test
    void appliesEachShipmentEventExactlyOnce() {
        var id = UUID.randomUUID().toString().replace("-", "");
        var order = new Order("mem_" + id, List.of(new OrderLine("sku", "prd", "상품", "옵션", 1,
                Money.krw(10_000))), new Address("addr", "home", "고객", "010", "서울", "서울", "12345", true));
        order.attachPayment("pay_" + id);
        order.markPaid();
        order.attachShipment("shp_" + id, null);
        orderChanges.commit(order);
        var payload = Map.of("shipmentStatus", "DELIVERED", "carrierCode", "PRIMARY",
                "carrierName", "기본 택배사", "trackingNumber", "TRK-" + id);
        var message = new ShipmentEventMessage("sev_" + id, "SHIPMENT_DELIVERED", "shp_" + id,
                order.id(), order.memberId(), OffsetDateTime.parse("2026-09-24T03:00:00Z"), payload);

        consumer.consume(message);
        consumer.consume(message);

        assertThat(orders.findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(events.findByOrderIdAndMemberId(order.id(), order.memberId()).stream()
                .filter(event -> event.type() == OrderEventType.ORDER_DELIVERED)).hasSize(1);
    }
}
