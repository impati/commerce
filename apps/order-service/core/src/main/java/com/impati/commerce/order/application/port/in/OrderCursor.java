package com.impati.commerce.order.application.port.in;

import com.impati.commerce.common.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 페이지 위치일 뿐 권한이 아니다. 조회에는 항상 회원 조건이 함께 적용되어야 한다. */
public record OrderCursor(LocalDateTime createdAt, String orderId) {
    public OrderCursor {
        if (createdAt == null || createdAt.getYear() < 1000 || createdAt.getYear() > 9999
                || createdAt.getNano() % 1_000 != 0
                || orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw DomainException.validation("invalid order cursor");
        }
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (createdAt + "|" + orderId).getBytes(StandardCharsets.UTF_8));
    }

    public static OrderCursor decode(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > 512) {
            throw DomainException.validation("invalid order cursor");
        }
        try {
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new OrderCursor(LocalDateTime.parse(parts[0]), parts[1]);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException error) {
            throw DomainException.validation("invalid order cursor");
        }
    }
}
