package com.impati.commerce.gateway;

import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.test.web.client.SimpleRequestExpectationManager;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestExpectationManager;

/** 서로 다른 시간 제한을 갖는 HTTP 클라이언트도 하나의 기대 요청 목록으로 검증한다. */
final class GatewayRestClientStubs extends MockServerRestClientCustomizer {
    GatewayRestClientStubs() {
        this(new SimpleRequestExpectationManager());
    }

    private GatewayRestClientStubs(RequestExpectationManager expectations) {
        super(() -> expectations);
    }

    @Override
    public MockRestServiceServer getServer() {
        return getServers().values().iterator().next();
    }
}
