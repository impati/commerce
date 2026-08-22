package com.impati.commerce.payment.adapter.out.gateway;

import com.impati.commerce.common.ApiContracts.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 대역의 동작을 고정한다.
 *
 * <p>벤더를 붙이면 이 클래스는 통째로 사라진다. 그때까지 데모와 테스트가 밟는 거절 경로가
 * 여기에만 있으므로, 토큰이 바뀌면 README와 프론트 시드도 함께 어긋난다.
 */
class FakePaymentGatewayTest {
    private final FakePaymentGateway gateway = new FakePaymentGateway();

    @Test
    void approvesAndAssignsATransactionId() {
        var authorization = gateway.authorize("ord_1", Money.krw(1_000), "card_test_success");

        assertThat(authorization.approved()).isTrue();
        assertThat(authorization.transactionId()).isNotBlank();
        assertThat(authorization.method()).isEqualTo("CARD");
        assertThat(authorization.declineReason()).isNull();
    }

    /** 거절은 예외가 아니라 값이다. 사유가 함께 온다 (ADR-0005). */
    @Test
    void declinesWithAReasonInsteadOfThrowing() {
        var authorization = gateway.authorize("ord_2", Money.krw(1_000), "card_test_decline");

        assertThat(authorization.approved()).isFalse();
        assertThat(authorization.declineReason()).isNotBlank();
        assertThat(authorization.transactionId()).isNull();
    }

    /** 데모와 README가 쓰는 토큰. 바뀌면 시드 데이터가 어긋난다. */
    @Test
    void recognizesTheDocumentedDemoTokens() {
        assertThat(gateway.authorize("ord_3", Money.krw(1_000), "card_test_success").approved()).isTrue();
        assertThat(gateway.authorize("ord_4", Money.krw(1_000), "card_test_decline").approved()).isFalse();
    }

    /** 승인마다 다른 거래 식별자가 나온다. 같으면 대사에서 두 결제를 구분할 수 없다. */
    @Test
    void assignsADistinctTransactionIdPerAuthorization() {
        var first = gateway.authorize("ord_5", Money.krw(1_000), "card_test_success");
        var second = gateway.authorize("ord_6", Money.krw(1_000), "card_test_success");

        assertThat(first.transactionId()).isNotEqualTo(second.transactionId());
    }

    /**
     * 거래 식별자 기준으로 멱등하다 (ADR-0005).
     *
     * <p>이 성질 위에 체크아웃의 재시도가 서 있다. 두 번째 호출이 실패하면 응답을 받지 못한
     * 호출자가 결과를 확정할 방법이 없어진다.
     */
    @Test
    void repeatsTheSameOperationWithoutFailing() {
        var authorization = gateway.authorize("ord_7", Money.krw(1_000), "card_test_success");

        gateway.capture(authorization.transactionId());
        gateway.capture(authorization.transactionId());
        gateway.refund(authorization.transactionId());
        gateway.refund(authorization.transactionId());
    }
}
