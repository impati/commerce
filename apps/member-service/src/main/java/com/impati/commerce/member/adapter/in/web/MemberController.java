package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.LoginRequest;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.common.ApiContracts.SessionResponse;
import com.impati.commerce.common.ApiContracts.VerifyEmailRequest;
import com.impati.commerce.member.application.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 API.
 *
 * <p>회원 자신을 가리키는 경로는 {@code memberId}를 받지 않는다. 게이트웨이가 세션을 검증해
 * {@code X-Member-Id}로 신원을 넘긴다. 경로로 받으면 남의 id를 넣어 조회하는 것을 서비스마다
 * 막아야 한다.
 */
@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @PostMapping
    MemberResponse register(@RequestBody RegisterMemberRequest request) {
        return members.register(request.email(), request.name(), request.password());
    }

    @PostMapping("/verifications")
    MemberResponse verifyEmail(@RequestBody VerifyEmailRequest request) {
        return members.verifyEmail(request.token());
    }

    @PostMapping("/verifications/resend")
    void resendVerification(@RequestHeader("X-Member-Id") String memberId) {
        members.resendVerification(memberId);
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        return members.login(request.email(), request.password());
    }

    /** 세션 확인. 게이트웨이 전용이며 외부에 노출하지 않는다. */
    @PostMapping("/sessions/resolve")
    SessionResponse resolveSession(@RequestBody VerifyEmailRequest request) {
        return members.resolveSession(request.token());
    }

    @PostMapping("/logout")
    void logout(@RequestBody VerifyEmailRequest request) {
        members.logout(request.token());
    }

    @GetMapping("/me")
    MemberResponse me(@RequestHeader("X-Member-Id") String memberId) {
        return members.get(memberId);
    }

    @PostMapping("/me/addresses")
    AddressResponse addAddress(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestBody AddAddressRequest request
    ) {
        return members.addAddress(
                memberId,
                request.alias(),
                request.recipient(),
                request.phone(),
                request.line1(),
                request.city(),
                request.postalCode(),
                request.defaultAddress()
        );
    }

    /** order-service가 배송지를 읽기 위한 내부 경로. 게이트웨이는 이 경로를 노출하지 않는다. */
    @GetMapping("/internal/{memberId}")
    MemberResponse internalGet(@org.springframework.web.bind.annotation.PathVariable String memberId) {
        return members.get(memberId);
    }
}
