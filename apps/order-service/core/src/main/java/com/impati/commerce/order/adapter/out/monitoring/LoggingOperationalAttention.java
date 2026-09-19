package com.impati.commerce.order.adapter.out.monitoring;

import com.impati.commerce.order.application.port.out.OperationalAttention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOperationalAttention implements OperationalAttention {
    private static final Logger log = LoggerFactory.getLogger(LoggingOperationalAttention.class);

    @Override
    public void required(String orderId, String stage, String reason) {
        log.error("order_attention_required order={} stage={} reason={}", orderId, stage, reason);
    }
}
