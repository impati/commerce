package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.member.application.port.in.MemberUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 자신의 프로필과 배송지. 세션이 있는 사용자만 도달한다.
 *
 * <p>{@code memberId}를 경로나 본문으로 받지 않는다. 게이트웨이가 세션을 검증해
 * {@code X-Member-Id}로 신원을 넘긴다. 받도록 두면 남의 id를 넣는 것을 서비스마다 막아야 한다.
 *
 * <p>같은 서비스의 다른 컨트롤러는 접근 등급이 다르다 — 퍼블릭은 {@link RegistrationController},
 * {@link SessionController}, 내부 전용은 {@link InternalMemberController}다.
 */
@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberUseCase memberUseCase;

    public MemberController(MemberUseCase memberUseCase) {
        this.memberUseCase = memberUseCase;
    }

    @GetMapping("/me")
    MemberResponse me(@RequestHeader("X-Member-Id") String memberId) {
        return MemberResponseMapper.from(memberUseCase.get(memberId));
    }

    @PostMapping("/me/addresses")
    AddressResponse addAddress(
            @RequestHeader("X-Member-Id") String memberId,
            @RequestBody AddAddressRequest request
    ) {
        return MemberResponseMapper.from(
                memberUseCase.addAddress(memberId, MemberResponseMapper.toNewAddress(request), request.expectedVersionAsLong()));
    }
}
