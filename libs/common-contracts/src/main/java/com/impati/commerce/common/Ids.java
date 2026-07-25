package com.impati.commerce.common;

import java.util.UUID;

/**
 * 접두사가 붙은 식별자.
 *
 * <p>TODO UUID를 12자(48비트)로 잘라 쓴다. 약 1,700만 건에서 충돌 확률이 50%이고 시간 정렬이
 * 되지 않아 인덱스 지역성이 나쁘다. ULID나 UUIDv7이 맞다. PK라서 미루면 비싸진다.
 */
public final class Ids {
    private Ids() {
    }

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

