package com.impati.commerce.order.application.port.in;

import java.time.OffsetDateTime;

public record OrderTimelineEntry(String type, OffsetDateTime occurredAt) {
}
