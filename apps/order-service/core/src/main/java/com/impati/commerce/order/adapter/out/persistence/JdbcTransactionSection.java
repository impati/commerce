package com.impati.commerce.order.adapter.out.persistence;

import com.impati.commerce.order.application.port.out.TransactionSection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 함께 성립해야 하는 구간을 DB 트랜잭션으로 실현한다.
 *
 * <p>응용 계층은 무엇이 한 단위인지만 말하고 이 클래스가 그것을 무엇으로 지킬지 정한다.
 * 참여자가 같은 DB를 공유하므로 지금은 트랜잭션이 맞다. 그것이 깨지는 날 — 예컨대 사건이
 * 다른 저장소로 가는 날 — 바뀌는 것은 이 클래스이고 응용 계층은 그대로다.
 */
@Component
class JdbcTransactionSection implements TransactionSection {
    private final TransactionTemplate transactionTemplate;

    JdbcTransactionSection(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(Runnable body) {
        transactionTemplate.executeWithoutResult(status -> body.run());
    }
}
