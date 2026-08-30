package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.domain.ShippingModels.Address;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDatabase
class JdbcShipmentRepositoryTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "shipping-repo");
    }
    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsShipmentWithAddress() {
        var shipment = newShipment("ord_round");

        shipmentRepository.save(shipment);
        var loaded = shipmentRepository.findById(shipment.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(shipment.id());
        assertThat(loaded.orderId()).isEqualTo("ord_round");
        assertThat(loaded.memberId()).isEqualTo("mem_demo");
        assertThat(loaded.status()).isEqualTo("READY");
        assertThat(loaded.trackingNumber()).isEqualTo(shipment.trackingNumber());
        assertThat(loaded.address().recipient()).isEqualTo("Demo Customer");
        assertThat(loaded.address().postalCode()).isEqualTo("04524");
    }

    @Test
    void savesStatusTransitions() {
        var shipment = newShipment("ord_transition");
        shipmentRepository.save(shipment);

        shipment.ship();
        shipmentRepository.save(shipment);
        assertThat(shipmentRepository.findById(shipment.id()).orElseThrow().status()).isEqualTo("IN_TRANSIT");

        shipment.deliver();
        shipmentRepository.save(shipment);
        assertThat(shipmentRepository.findById(shipment.id()).orElseThrow().status()).isEqualTo("DELIVERED");
    }

    /** 저장하지 않은 변경은 반영되지 않는다. 인메모리 맵에서는 성립하지 않던 성질이다. */
    @Test
    void discardsChangesThatWereNotSaved() {
        var shipment = newShipment("ord_unsaved");
        shipmentRepository.save(shipment);

        shipment.ship();

        assertThat(shipmentRepository.findById(shipment.id()).orElseThrow().status()).isEqualTo("READY");
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachAddressFieldToItsOwnColumn() {
        var shipment = newShipment("ord_column");
        shipmentRepository.save(shipment);

        var row = jdbc.queryForMap(
                "select order_id, member_id, status, tracking_number, ship_address_id, ship_alias,"
                        + " ship_recipient, ship_phone, ship_line1, ship_city, ship_postal_code"
                        + " from shipments where id = ?",
                shipment.id()
        );
        assertThat(row.get("order_id")).isEqualTo("ord_column");
        assertThat(row.get("member_id")).isEqualTo("mem_demo");
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("tracking_number")).isEqualTo(shipment.trackingNumber());
        assertThat(row.get("ship_address_id")).isEqualTo("addr_demo");
        assertThat(row.get("ship_alias")).isEqualTo("home");
        assertThat(row.get("ship_recipient")).isEqualTo("Demo Customer");
        assertThat(row.get("ship_phone")).isEqualTo("010-0000-0000");
        assertThat(row.get("ship_line1")).isEqualTo("123 Commerce Road");
        assertThat(row.get("ship_city")).isEqualTo("Seoul");
        assertThat(row.get("ship_postal_code")).isEqualTo("04524");
    }

    @Test
    void returnsEmptyForUnknownShipment() {
        assertThat(shipmentRepository.findById("shp_never_saved")).isEmpty();
    }

    private Shipment newShipment(String orderId) {
        return new Shipment(orderId, "mem_demo", new Address(
                "addr_demo",
                "home",
                "Demo Customer",
                "010-0000-0000",
                "123 Commerce Road",
                "Seoul",
                "04524",
                true
        ));
    }
}
