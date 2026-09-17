package com.impati.commerce.storefront.application.component;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueryDeadlineTest {
    @Test
    void laterQueriesReceiveOnlyTheRemainingBudget() throws Exception {
        var now = new AtomicLong();
        var deadline = new QueryDeadline(Duration.ofSeconds(2), now::get);
        Future<String> first = mock(Future.class);
        Future<String> second = mock(Future.class);

        deadline.await(first);
        now.set(Duration.ofMillis(1200).toNanos());
        deadline.await(second);

        verify(first).get(Duration.ofSeconds(2).toNanos(), TimeUnit.NANOSECONDS);
        verify(second).get(Duration.ofMillis(800).toNanos(), TimeUnit.NANOSECONDS);
        now.set(Duration.ofSeconds(2).toNanos());
        assertThat(deadline.expired()).isTrue();
    }
}
