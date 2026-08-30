package com.impati.commerce.order.adapter.out.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 협력자별 {@link RestClient}를 만든다. <b>빌더를 주입받는 유일한 자리다.</b>
 *
 * <p>{@code RestClient.Builder}는 주입 지점마다 새 인스턴스가 온다. 클라이언트 모듈이 각자
 * 주입받으면 빌더가 모듈 수만큼 생기고, 그러면 빌더 단위로 동작하는 테스트 스텁
 * (MockServerRestClientCustomizer)이 여러 개로 갈라져 "단일 서버를 돌려줄 수 없다"로 깨진다.
 * 커스터마이저로 거는 공통 정책도 어디에 걸렸는지 추적할 수 없게 된다.
 *
 * <p>그래서 이 빈이 빌더를 한 번 주입받아 들고, 클라이언트 모듈은 이것을 주입받아 쓴다.
 * 어느 협력자를 부르는지는 각 모듈이 정하고 이 모듈은 모른다 (ADR-0015).
 */
@Component
public class CommerceRestClients {
    private final RestClient.Builder builder;

    public CommerceRestClients(RestClient.Builder builder) {
        this.builder = builder;
    }

    public RestClient forBaseUrl(String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
