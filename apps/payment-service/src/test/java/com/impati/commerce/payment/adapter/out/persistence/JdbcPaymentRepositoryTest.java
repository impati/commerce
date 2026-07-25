package com.impati.commerce.payment.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.payment.application.PaymentRepository;
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
        var payment = new Payment("ord_pay", "mem_demo", Money.krw(58_000));

        payments.save(payment);
        var loaded = payments.findById(payment.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(payment.id());
        assertThat(loaded.orderId()).isEqualTo("ord_pay");
        assertThat(loaded.memberId()).isEqualTo("mem_demo");
        assertThat(loaded.amount()).isEqualTo(Money.krw(58_000));
        assertThat(loaded.method()).isEqualTo("CARD");
        assertThat(loaded.status()).isEqualTo("CAPTURED");
        assertThat(loaded.transactionId()).isEqualTo(payment.transactionId());
    }

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachFieldToItsOwnColumn() {
        var payment = new Payment("ord_column", "mem_column", Money.krw(91_000));

        payments.save(payment);

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
        assertThat(row.get("STATUS")).isEqualTo("CAPTURED");
        assertThat(row.get("TRANSACTION_ID")).isEqualTo(payment.transactionId());
    }

    @Test
    void returnsEmptyForUnknownPayment() {
        assertThat(payments.findById("pay_never_saved")).isEmpty();
    }
}
