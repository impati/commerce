package com.impati.commerce.order.application.model;

import java.time.OffsetDateTime;

public record OrderTimelineEntry(String type, OffsetDateTime occurredAt) {
}
