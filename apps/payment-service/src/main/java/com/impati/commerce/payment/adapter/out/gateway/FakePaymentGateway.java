package com.impati.commerce.payment.adapter.out.gateway;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.Ids;
import com.impati.commerce.payment.application.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 대행사에 실제로 요청하지 않고 로그로 남기는 로컬 대역.
 *
 * <p>운영에서는 벤더 API 어댑터로 교체한다. 애플리케이션과 도메인은 이 클래스의 존재를 모르며,
 * 외부 호출을 하지 않는다는 사실도 알지 못한다.
 *
 * <p>거절 판정은 토큰 문자열로 흉내낸다. 벤더를 붙이면 이 판정이 통째로 사라지고 응답을 옮기는
 * 코드만 남는다 — 판정하는 곳이 여기 하나이므로 응용 계층은 바뀌지 않는다. 데모와 테스트가
 * 쓰는 토큰은 README에 있다.
 *
 * <p>거래 상태를 들고 있는 것은 <b>멱등을 흉내내기 위해서</b>다 (ADR-0005). 실제 벤더에서는
 * 그쪽이 거래를 소유하므로 이 맵이 사라진다. 프로세스가 재시작되면 비는데, 로컬 대역이므로
 * 그대로 둔다.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    private static final Set<String> DECLINE_TOKENS = Set.of("card_test_decline", "decline", "fail");
    private static final String CARD = "CARD";

    private final Map<String, String> transactions = new ConcurrentHashMap<>();

    @Override
    public Authorization authorize(String orderId, Money amount, String paymentToken) {
        if (DECLINE_TOKENS.contains(paymentToken)) {
            log.info("gateway declined order={} amount={}", orderId, amount.amount());
            return Authorization.declined("issuer declined the card");
        }
        var transactionId = Ids.newId("txn");
        transactions.put(transactionId, "AUTHORIZED");
        log.info("gateway authorized order={} amount={} transaction={}", orderId, amount.amount(), transactionId);
        return Authorization.approved(transactionId, CARD);
    }

    @Override
    public void capture(String transactionId) {
        move(transactionId, "CAPTURED");
    }

    @Override
    public void cancel(String transactionId) {
        move(transactionId, "CANCELLED");
    }

    @Override
    public void refund(String transactionId) {
        move(transactionId, "REFUNDED");
    }

    /** 같은 상태를 다시 요청해도 같은 결과다. 호출자는 응답만 보고 몇 번째 요청인지 알 수 없다. */
    private void move(String transactionId, String to) {
        transactions.put(transactionId, to);
        log.info("gateway {} transaction={}", to.toLowerCase(), transactionId);
    }
}
