package com.impati.commerce.payment.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.test.RequiresDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 승인·매입·취소 규칙을 고정한다 (PD-0011).
 *
 * <p>컨텍스트와 DB가 테스트 간에 공유되므로 각 테스트가 자기 주문 식별자를 쓴다. 한 주문에
 * 결제는 하나뿐이므로(PD-0011-R2) 식별자를 공유하면 서로의 결제를 돌려받는다.
 */
@SpringBootTest
@RequiresDatabase
class PaymentExecutorTest {

    private static final Money AMOUNT = Money.krw(58_000);
    private static final String OK_TOKEN = "card_test_success";

    @Autowired
    private PaymentExecutor payments;

    /** PD-0011-R1: 승인은 대금을 확보만 하고 청구를 확정하지 않는다. */
    @Test
    void authorizeDoesNotCapture() {
        var authorized = payments.authorize("ord_auth", "mem_a", AMOUNT, OK_TOKEN);

        assertThat(authorized.status()).isEqualTo("AUTHORIZED");
    }

    /** PD-0011-R1: 매입이 청구를 확정한다. */
    @Test
    void captureConfirmsTheCharge() {
        var authorized = payments.authorize("ord_capture", "mem_a", AMOUNT, OK_TOKEN);

        var captured = payments.capture(authorized.id());

        assertThat(captured.id()).isEqualTo(authorized.id());
        assertThat(captured.status()).isEqualTo("CAPTURED");
    }

    /** PD-0011-R2: 같은 주문의 승인 재요청은 새 결제를 만들지 않고 기존 결제를 돌려준다. */
    @Test
    void authorizingTheSameOrderTwiceReturnsTheSamePayment() {
        var first = payments.authorize("ord_twice", "mem_a", AMOUNT, OK_TOKEN);
        var second = payments.authorize("ord_twice", "mem_a", AMOUNT, OK_TOKEN);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
    }

    /**
     * PD-0011-R2: 이미 매입된 주문에 승인을 다시 요청해도 새 결제가 생기지 않는다.
     *
     * <p>타임아웃 후 재시도가 이중 청구가 되는 경로가 이것이다.
     */
    @Test
    void reauthorizingACapturedOrderDoesNotCreateASecondPayment() {
        var first = payments.authorize("ord_recapture", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(first.id());

        var second = payments.authorize("ord_recapture", "mem_a", AMOUNT, OK_TOKEN);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo("CAPTURED");
    }

    /**
     * PD-0011-R2: 승인 재요청은 발급사를 다시 묻지 않는다.
     *
     * <p>기존 결제를 먼저 찾아 돌려주므로 토큰 판정에 도달하지 않는다. 결과가 이미 정해진
     * 요청을 다시 판정하면 같은 주문의 응답이 요청마다 달라진다.
     */
    @Test
    void reauthorizingDoesNotConsultTheIssuerAgain() {
        var first = payments.authorize("ord_no_recheck", "mem_a", AMOUNT, OK_TOKEN);

        var second = payments.authorize("ord_no_recheck", "mem_a", AMOUNT, "card_test_decline");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo("AUTHORIZED");
    }

    /** PD-0011-R3: 승인은 취소할 수 있다. */
    @Test
    void authorizationCanBeCancelled() {
        var authorized = payments.authorize("ord_cancel", "mem_a", AMOUNT, OK_TOKEN);

        var cancelled = payments.cancel(authorized.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    /** PD-0011-R3, PD-0011-R7: 매입된 결제는 취소할 수 없다. 되돌리는 수단은 환불뿐이고 없다. */
    @Test
    void capturedPaymentCannotBeCancelled() {
        var authorized = payments.authorize("ord_no_cancel", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(authorized.id());

        assertThatThrownBy(() -> payments.cancel(authorized.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("captured payment cannot be cancelled");
    }

    /** PD-0011-R3: 취소된 결제는 매입할 수 없다. */
    @Test
    void cancelledPaymentCannotBeCaptured() {
        var authorized = payments.authorize("ord_no_capture", "mem_a", AMOUNT, OK_TOKEN);
        payments.cancel(authorized.id());

        assertThatThrownBy(() -> payments.capture(authorized.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no longer authorized");
    }

    /** PD-0011-R4: 매입 요청이 여러 번 도착해도 첫 결과를 유지한다. */
    @Test
    void captureIsIdempotent() {
        var authorized = payments.authorize("ord_idem_capture", "mem_a", AMOUNT, OK_TOKEN);

        var first = payments.capture(authorized.id());
        var second = payments.capture(authorized.id());

        assertThat(second.status()).isEqualTo("CAPTURED");
        assertThat(second).isEqualTo(first);
    }

    /** PD-0011-R4: 취소 요청이 여러 번 도착해도 첫 결과를 유지한다. */
    @Test
    void cancelIsIdempotent() {
        var authorized = payments.authorize("ord_idem_cancel", "mem_a", AMOUNT, OK_TOKEN);

        var first = payments.cancel(authorized.id());
        var second = payments.cancel(authorized.id());

        assertThat(second.status()).isEqualTo("CANCELLED");
        assertThat(second).isEqualTo(first);
    }

    /** PD-0011-R8: 매입된 결제는 환불할 수 있다. */
    @Test
    void capturedPaymentCanBeRefunded() {
        var authorized = payments.authorize("ord_refund", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(authorized.id());

        var refunded = payments.refund(authorized.id());

        assertThat(refunded.status()).isEqualTo("REFUNDED");
    }

    /** PD-0011-R8: 매입되지 않은 결제는 환불할 수 없다. 되돌릴 대금이 없다. */
    @Test
    void authorizedPaymentCannotBeRefunded() {
        var authorized = payments.authorize("ord_no_refund", "mem_a", AMOUNT, OK_TOKEN);

        assertThatThrownBy(() -> payments.refund(authorized.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only a captured payment can be refunded");
    }

    /** PD-0011-R8: 취소된 결제도 환불할 수 없다. */
    @Test
    void cancelledPaymentCannotBeRefunded() {
        var authorized = payments.authorize("ord_cancelled_refund", "mem_a", AMOUNT, OK_TOKEN);
        payments.cancel(authorized.id());

        assertThatThrownBy(() -> payments.refund(authorized.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only a captured payment can be refunded");
    }

    /** PD-0011-R4: 환불 요청이 여러 번 도착해도 첫 결과를 유지한다. */
    @Test
    void refundIsIdempotent() {
        var authorized = payments.authorize("ord_idem_refund", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(authorized.id());

        var first = payments.refund(authorized.id());
        var second = payments.refund(authorized.id());

        assertThat(second.status()).isEqualTo("REFUNDED");
        assertThat(second).isEqualTo(first);
    }

    /** PD-0011-R3: 환불된 결제는 다시 매입할 수 없다. */
    @Test
    void refundedPaymentCannotBeCaptured() {
        var authorized = payments.authorize("ord_refunded_capture", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(authorized.id());
        payments.refund(authorized.id());

        assertThatThrownBy(() -> payments.capture(authorized.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no longer authorized");
    }

    /**
     * PD-0011-R9: 매입 여부를 나중에 다시 물을 수 있다.
     *
     * <p>응답을 받지 못한 호출자가 결과를 확인하는 경로다. 이것이 없으면 모르는 상태를
     * 영원히 확정할 수 없다.
     */
    @Test
    void captureOutcomeCanBeLookedUpLater() {
        var authorized = payments.authorize("ord_lookup", "mem_a", AMOUNT, OK_TOKEN);
        payments.capture(authorized.id());

        assertThat(payments.get(authorized.id()).status()).isEqualTo("CAPTURED");
    }

    /** PD-0011-R5: 결제 수단은 카드로 고정한다. */
    @Test
    void paymentMethodIsAlwaysCard() {
        var authorized = payments.authorize("ord_method", "mem_a", AMOUNT, OK_TOKEN);

        assertThat(authorized.method()).isEqualTo("CARD");
    }

    /** PD-0011-R6: 거절은 시스템 오류와 구분되는 결과로 전달된다. */
    @Test
    void declinedAuthorizationIsNotASystemError() {
        assertThatThrownBy(() -> payments.authorize("ord_declined", "mem_a", AMOUNT, "card_test_decline"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("declined");
    }

    /** PD-0011-R6: 거절된 승인은 성립하지 않으므로 결제가 남지 않는다. */
    @Test
    void declinedAuthorizationLeavesNoPayment() {
        assertThatThrownBy(() -> payments.authorize("ord_declined_none", "mem_a", AMOUNT, "decline"))
                .isInstanceOf(DomainException.class);

        var retried = payments.authorize("ord_declined_none", "mem_a", AMOUNT, OK_TOKEN);
        assertThat(retried.status()).isEqualTo("AUTHORIZED");
    }
}
