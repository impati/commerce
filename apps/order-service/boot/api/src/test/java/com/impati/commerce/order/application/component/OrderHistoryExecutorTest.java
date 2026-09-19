package com.impati.commerce.order.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.order.application.port.out.CheckoutProgressRepository;
import com.impati.commerce.order.application.port.out.CancellationProgressRepository;
import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderRepository;
import com.impati.commerce.order.application.port.out.ShippingClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderHistoryExecutorTest {
    /** [PD-0020-R9] 소유권이 없는 요청은 사건과 체크아웃 진행을 읽기 전 거절된다. */
    @Test
    void rejectsAnUnownedOrderBeforeReadingItsEventsOrCheckout() {
        var orderRepository = mock(OrderRepository.class);
        var progressRepository = mock(CheckoutProgressRepository.class);
        var cancellationRepository = mock(CancellationProgressRepository.class);
        var eventRepository = mock(OrderEventRepository.class);
        var shippingClient = mock(ShippingClient.class);
        when(orderRepository.findByIdAndMemberId("ord_private", "mem_other")).thenReturn(Optional.empty());
        var executor = new OrderHistoryExecutor(
                orderRepository, progressRepository, eventRepository, cancellationRepository, shippingClient);
        assertThatThrownBy(() -> executor.getOwned("mem_other", "ord_private"))
                .isInstanceOf(DomainException.class).hasMessage("order not found");
        verifyNoInteractions(progressRepository, eventRepository, cancellationRepository, shippingClient);
    }
}
