package com.impati.commerce.payment.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.payment.application.port.out.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-repo;DB_CLOSE_DELAY=-1")
class JdbcPaymentRepositoryTest {
    @Autowired
    private PaymentRepository payments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsPayment() {
        var payment = new Payment("ord_pay", "mem_demo", Money.krw(58_000), "txn_fixture", "CARD");

        payments.insertIfAbsent(payment);
        var loaded = payments.findById(payment.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(payment.id());
        assertThat(loaded.orderId()).isEqualTo("ord_pay");
        assertThat(loaded.memberId()).isEqualTo("mem_demo");
        assertThat(loaded.amount()).isEqualTo(Money.krw(58_000));
        assertThat(loaded.method()).isEqualTo("CARD");
        assertThat(loaded.status()).isEqualTo("AUTHORIZED");
        assertThat(loaded.transactionId()).isEqualTo("txn_fixture");
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachFieldToItsOwnColumn() {
        var payment = new Payment("ord_column", "mem_column", Money.krw(91_000), "txn_fixture", "CARD");

        payments.insertIfAbsent(payment);

        var row = jdbc.queryForMap(
                "select order_id, member_id, amount, currency, method, status, transaction_id"
                        + " from payments where id = ?",
                payment.id()
        );
        assertThat(row.get("ORDER_ID")).isEqualTo("ord_column");
        assertThat(row.get("MEMBER_ID")).isEqualTo("mem_column");
        assertThat(row.get("AMOUNT")).isEqualTo(91_000L);
        assertThat(row.get("CURRENCY")).isEqualTo("KRW");
        assertThat(row.get("METHOD")).isEqualTo("CARD");
        assertThat(row.get("STATUS")).isEqualTo("AUTHORIZED");
        assertThat(row.get("TRANSACTION_ID")).isEqualTo("txn_fixture");
    }

    @Test
    void returnsEmptyForUnknownPayment() {
        assertThat(payments.findById("pay_never_saved")).isEmpty();
    }

    @Test
    void findsPaymentByOrderId() {
        var payment = new Payment("ord_by_order", "mem_demo", Money.krw(12_000), "txn_fixture", "CARD");

        payments.insertIfAbsent(payment);

        assertThat(payments.findByOrderId("ord_by_order").orElseThrow().id()).isEqualTo(payment.id());
    }

    /**
     * PD-0011-R2: 한 주문에 결제는 하나다. 응용 계층의 조회는 동시 요청을 막지 못하므로
     * 마지막 판정을 유일 제약이 한다.
     */
    @Test
    void refusesASecondPaymentForTheSameOrder() {
        var first = new Payment("ord_unique", "mem_demo", Money.krw(30_000), "txn_fixture", "CARD");
        var second = new Payment("ord_unique", "mem_demo", Money.krw(30_000), "txn_fixture", "CARD");

        assertThat(payments.insertIfAbsent(first)).isTrue();
        assertThat(payments.insertIfAbsent(second)).isFalse();
        assertThat(payments.findByOrderId("ord_unique").orElseThrow().id()).isEqualTo(first.id());
    }

    /** 상태만 바뀐다. 나머지 값은 승인 시점에 확정된다. */
    @Test
    void updateChangesOnlyTheStatusColumn() {
        var payment = new Payment("ord_update", "mem_demo", Money.krw(45_000), "txn_fixture", "CARD");
        payments.insertIfAbsent(payment);

        payment.capture();
        payments.update(payment);

        var row = jdbc.queryForMap(
                "select order_id, member_id, amount, status, transaction_id from payments where id = ?",
                payment.id()
        );
        assertThat(row.get("STATUS")).isEqualTo("CAPTURED");
        assertThat(row.get("ORDER_ID")).isEqualTo("ord_update");
        assertThat(row.get("MEMBER_ID")).isEqualTo("mem_demo");
        assertThat(row.get("AMOUNT")).isEqualTo(45_000L);
        assertThat(row.get("TRANSACTION_ID")).isEqualTo("txn_fixture");
    }
}
