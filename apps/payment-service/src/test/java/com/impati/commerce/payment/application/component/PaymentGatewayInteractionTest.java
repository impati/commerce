package com.impati.commerce.payment.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.payment.application.port.out.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네 동작이 실제로 대행사를 거치는지, 그리고 대행사가 소유하는 값이 응답에서 오는지 고정한다.
 *
 * <p>로컬 대역 대신 호출을 기록하는 대역을 넣는다. 대행사를 거치지 않고 상태만 바꾸는 구현으로
 * 되돌아가면 여기서 깨진다 — 그 상태가 BL-0032의 출발점이었다.
 */
@SpringBootTest
@RequiresDatabase
class PaymentGatewayInteractionTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "payment-gateway");
    }
    private static final Money AMOUNT = Money.krw(58_000);

    @TestConfiguration
    static class RecordingGatewayConfiguration {
        @Bean
        @Primary
        RecordingPaymentGateway recordingPaymentGateway() {
            return new RecordingPaymentGateway();
        }
    }

    /**
     * 호출을 기록하는 대역.
     *
     * <p>거래 식별자와 결제 수단을 로컬 대역과 다른 값으로 돌려준다. 그래야 저장된 결제의 그
     * 값들이 대행사 응답에서 왔다는 것이 드러난다 — 도메인이 만든 값이면 여기 적힌 값과 다르다.
     */
    static class RecordingPaymentGateway implements PaymentGateway {
        static final String TRANSACTION_ID = "txn_from_gateway";
        static final String METHOD = "GATEWAY_METHOD";

        final List<String> calls = new ArrayList<>();
        boolean approve = true;

        @Override
        public Authorization authorize(String orderId, Money amount, String paymentToken) {
            calls.add("authorize:" + orderId);
            return approve
                    ? Authorization.approved(TRANSACTION_ID, METHOD)
                    : Authorization.declined("insufficient funds");
        }

        @Override
        public void capture(String transactionId) {
            calls.add("capture:" + transactionId);
        }

        @Override
        public void cancel(String transactionId) {
            calls.add("cancel:" + transactionId);
        }

        @Override
        public void refund(String transactionId) {
            calls.add("refund:" + transactionId);
        }
    }

    @Autowired
    private PaymentExecutor payments;

    @Autowired
    private RecordingPaymentGateway recordingPaymentGateway;

    @BeforeEach
    void resetGateway() {
        recordingPaymentGateway.calls.clear();
        recordingPaymentGateway.approve = true;
    }

    @Test
    void authorizeAsksTheGateway() {
        payments.authorize("ord_gw_auth", "mem_a", AMOUNT, "any-token");

        assertThat(recordingPaymentGateway.calls).containsExactly("authorize:ord_gw_auth");
    }

    /** 거래 식별자와 결제 수단은 대행사가 정한다. 도메인이 만들지 않는다. */
    @Test
    void storesTheTransactionIdAndMethodTheGatewayReturned() {
        var authorized = payments.authorize("ord_gw_values", "mem_a", AMOUNT, "any-token");

        assertThat(authorized.transactionId()).isEqualTo(RecordingPaymentGateway.TRANSACTION_ID);
        assertThat(authorized.method()).isEqualTo(RecordingPaymentGateway.METHOD);
    }

    /** 거절 판정은 대행사가 한다. 응용 계층은 그 결과를 도메인 언어로 옮길 뿐이다. */
    @Test
    void declineComesFromTheGatewayNotFromTheApplication() {
        recordingPaymentGateway.approve = false;

        assertThatThrownBy(() -> payments.authorize("ord_gw_declined", "mem_a", AMOUNT, "any-token"))
                .hasMessageContaining("insufficient funds");
    }

    @Test
    void captureAsksTheGatewayWithTheTransactionId() {
        var authorized = payments.authorize("ord_gw_capture", "mem_a", AMOUNT, "any-token");
        recordingPaymentGateway.calls.clear();

        payments.capture(authorized.id());

        assertThat(recordingPaymentGateway.calls).containsExactly("capture:" + RecordingPaymentGateway.TRANSACTION_ID);
    }

    @Test
    void cancelAsksTheGatewayWithTheTransactionId() {
        var authorized = payments.authorize("ord_gw_cancel", "mem_a", AMOUNT, "any-token");
        recordingPaymentGateway.calls.clear();

        payments.cancel(authorized.id());

        assertThat(recordingPaymentGateway.calls).containsExactly("cancel:" + RecordingPaymentGateway.TRANSACTION_ID);
    }

    @Test
    void refundAsksTheGatewayWithTheTransactionId() {
        var authorized = payments.authorize("ord_gw_refund", "mem_a", AMOUNT, "any-token");
        payments.capture(authorized.id());
        recordingPaymentGateway.calls.clear();

        payments.refund(authorized.id());

        assertThat(recordingPaymentGateway.calls).containsExactly("refund:" + RecordingPaymentGateway.TRANSACTION_ID);
    }

    /** PD-0011-R4: 이미 끝난 일은 대행사에 다시 요청하지 않는다. */
    @Test
    void repeatedCaptureDoesNotAskTheGatewayAgain() {
        var authorized = payments.authorize("ord_gw_idem", "mem_a", AMOUNT, "any-token");
        payments.capture(authorized.id());
        recordingPaymentGateway.calls.clear();

        payments.capture(authorized.id());

        assertThat(recordingPaymentGateway.calls).isEmpty();
    }

    /** PD-0011-R2: 승인 재요청은 대행사에도 다시 묻지 않는다. */
    @Test
    void reauthorizeDoesNotAskTheGatewayAgain() {
        payments.authorize("ord_gw_reauth", "mem_a", AMOUNT, "any-token");
        recordingPaymentGateway.calls.clear();

        payments.authorize("ord_gw_reauth", "mem_a", AMOUNT, "any-token");

        assertThat(recordingPaymentGateway.calls).isEmpty();
    }

    /** 대행사가 거절하면 결제가 남지 않는다. 남으면 그 주문은 영영 승인받지 못한다. */
    @Test
    void declinedAuthorizationLeavesNoPayment() {
        recordingPaymentGateway.approve = false;
        assertThatThrownBy(() -> payments.authorize("ord_gw_retry", "mem_a", AMOUNT, "any-token"));

        recordingPaymentGateway.approve = true;
        assertThat(payments.authorize("ord_gw_retry", "mem_a", AMOUNT, "any-token").status())
                .isEqualTo("AUTHORIZED");
    }
}
