package com.impati.commerce.order.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 같은 멱등 키가 최초 체크아웃과 같은 입력으로 반복됐는지 판별하는 지문. */
public record CheckoutRequestFingerprint(String value) {
    public static CheckoutRequestFingerprint from(String paymentToken, String addressId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, paymentToken);
            update(digest, addressId);
            return new CheckoutRequestFingerprint(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
