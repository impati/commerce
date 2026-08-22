package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.common.ApiContracts.VerifyEmailRequest;
import com.impati.commerce.member.application.port.in.RegistrationUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입과 이메일 소유 확인.
 *
 * <p>가입과 인증 토큰 확인은 세션 없이 도달해야 하므로 퍼블릭이다. 재발송만 세션을 요구한다 —
 * 이메일만으로 재발송을 허용하면 남의 주소로 메일을 반복 발송시킬 수 있다.
 */
@RestController
@RequestMapping("/members")
public class RegistrationController {
    private final RegistrationUseCase registrations;

    public RegistrationController(RegistrationUseCase registrations) {
        this.registrations = registrations;
    }

    @PostMapping
    MemberResponse register(@RequestBody RegisterMemberRequest request) {
        return MemberResponseMapper.from(
                registrations.register(request.email(), request.name(), request.password()));
    }

    @PostMapping("/verifications")
    MemberResponse verifyEmail(@RequestBody VerifyEmailRequest request) {
        return MemberResponseMapper.from(registrations.verifyEmail(request.token()));
    }

    @PostMapping("/verifications/resend")
    void resendVerification(@RequestHeader("X-Member-Id") String memberId) {
        registrations.resendVerification(memberId);
    }
}
