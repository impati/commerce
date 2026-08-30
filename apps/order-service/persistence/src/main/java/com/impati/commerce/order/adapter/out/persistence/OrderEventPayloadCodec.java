package com.impati.commerce.order.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 사건 페이로드를 저장 형식으로 옮긴다.
 *
 * <p>도메인은 페이로드를 문자열 맵으로만 알고 어떤 형식으로 눕는지 모른다. 그 변환이 한 곳에만
 * 있어야 저장과 복원이 서로 다른 방향으로 틀리지 않는다.
 *
 * <p>JSON인 이유는 사건 종류가 늘어도 스키마가 안 바뀌기 때문이다. 컬럼으로 두면 사건 하나
 * 추가가 마이그레이션 하나가 되어 "새 전이 = 새 사건"이라는 규칙을 쓸 수 없게 된다 (ADR-0012).
 */
@Component
class OrderEventPayloadCodec {
    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    OrderEventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String encode(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception failure) {
            // 문자열 맵이 직렬화되지 않는 경우는 없다. 삼키면 빈 페이로드가 저장되어 사건이
            // 사실을 잃은 채 발행되므로, 뜨는 편이 낫다.
            throw new IllegalStateException("order event payload cannot be encoded", failure);
        }
    }

    Map<String, String> decode(String payload) {
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (Exception failure) {
            throw new IllegalStateException("order event payload cannot be decoded: " + payload, failure);
        }
    }
}
