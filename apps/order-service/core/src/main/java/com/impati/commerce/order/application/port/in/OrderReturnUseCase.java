package com.impati.commerce.order.application.port.in;

import com.impati.commerce.order.application.model.OrderReturnDetails;
import com.impati.commerce.order.domain.OrderModels.Address;
import java.time.LocalDate;

public interface OrderReturnUseCase {
    OrderReturnDetails request(String memberId, String orderId, String reason, String description,
            LocalDate awareDate, Address pickupAddress);
    OrderReturnDetails getOwned(String memberId, String orderId);
    OrderReturnDetails withdraw(String memberId, String orderId);
    OrderReturnDetails reschedule(String memberId, String orderId, Address pickupAddress);
    OrderReturnDetails inspect(String returnId, String disposition, String condition);
}
