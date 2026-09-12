package com.impati.commerce.order.domain;

import com.impati.commerce.common.DomainException;

/** 회원 범위에서 체크아웃 접수와 최초 응답 복구를 식별하는 키. */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw DomainException.validation("Idempotency-Key is required and must be at most 128 characters");
        }
    }
}
