package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.InspectOrderReturn;
import com.impati.commerce.common.ApiContracts.OrderReturnResponse;
import com.impati.commerce.order.application.port.in.OrderReturnUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/returns")
public class InternalReturnController {
    private final OrderReturnUseCase returns;
    public InternalReturnController(OrderReturnUseCase returns) { this.returns = returns; }

    @PostMapping("/{returnId}/inspection")
    OrderReturnResponse inspect(@PathVariable String returnId, @RequestBody InspectOrderReturn request) {
        return OrderReturnResponseMapper.from(returns.inspect(returnId, request.disposition(), request.condition()));
    }
}
