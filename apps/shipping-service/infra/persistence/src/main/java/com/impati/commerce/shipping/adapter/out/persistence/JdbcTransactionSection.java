package com.impati.commerce.shipping.adapter.out.persistence;

import com.impati.commerce.shipping.application.port.out.TransactionSection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
class JdbcTransactionSection implements TransactionSection {
    private final TransactionTemplate transactions;

    JdbcTransactionSection(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T required(Supplier<T> body) {
        return transactions.execute(status -> body.get());
    }
}
