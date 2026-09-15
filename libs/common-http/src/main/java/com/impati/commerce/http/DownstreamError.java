package com.impati.commerce.http;

/** 다운스트림의 오류 응답에서 호출자가 계약으로 판단할 수 있는 최소 정보. */
public record DownstreamError(int status, String code, String message) {
    public boolean hasCode(String expected) {
        return expected.equals(code);
    }
}
