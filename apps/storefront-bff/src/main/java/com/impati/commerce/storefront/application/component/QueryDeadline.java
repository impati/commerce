package com.impati.commerce.storefront.application.component;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 모든 조회가 공유하는 마감 시각. 시스템 시각 변경에 영향을 받지 않는다. */
final class QueryDeadline {
    private final long startedAt;
    private final LongSupplier nanoTime;
    private final long budgetNanos;

    QueryDeadline(Duration budget) {
        this(budget, System::nanoTime);
    }

    QueryDeadline(Duration budget, LongSupplier nanoTime) {
        if (budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("query budget must be positive");
        }
        this.budgetNanos = budget.toNanos();
        this.nanoTime = nanoTime;
        this.startedAt = nanoTime.getAsLong();
    }

    <T> T await(Future<T> query) throws InterruptedException, ExecutionException, TimeoutException {
        var remaining = Math.max(0, budgetNanos - (nanoTime.getAsLong() - startedAt));
        return query.get(remaining, TimeUnit.NANOSECONDS);
    }

    boolean expired() {
        return nanoTime.getAsLong() - startedAt >= budgetNanos;
    }
}
