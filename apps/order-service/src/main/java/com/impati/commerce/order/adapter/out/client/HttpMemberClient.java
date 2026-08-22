package com.impati.commerce.order.adapter.out.client;

import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.order.application.port.out.MemberClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpMemberClient implements MemberClient {
    private final RestClient restClient;

    public HttpMemberClient(RestClient memberRestClient) {
        this.restClient = memberRestClient;
    }

    @Override
    public MemberResponse member(String memberId) {
        return restClient.get().uri("/internal/members/{memberId}", memberId).retrieve().body(MemberResponse.class);
    }
}
