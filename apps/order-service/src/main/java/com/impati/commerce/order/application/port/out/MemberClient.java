package com.impati.commerce.order.application.port.out;

import com.impati.commerce.common.ApiContracts.MemberResponse;

/** member-service 호출 포트. 구현은 {@code adapter/out/client}에 둔다. */
public interface MemberClient {
    MemberResponse member(String memberId);
}
