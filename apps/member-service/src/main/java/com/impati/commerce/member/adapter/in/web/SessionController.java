package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.LoginRequest;
import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.member.application.port.in.SessionUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인과 로그아웃.
 *
 * <p>세션 확인({@code resolve})은 여기 없다. 그건 게이트웨이만 부르는 내부 경로이므로
 * {@link InternalMemberController}에 있다.
 */
@RestController
@RequestMapping("/members")
public class SessionController {
    private final SessionUseCase sessionUseCase;

    public SessionController(SessionUseCase sessionUseCase) {
        this.sessionUseCase = sessionUseCase;
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        return MemberResponseMapper.from(sessionUseCase.login(request.email(), request.password()));
    }

    /** 토큰을 가진 사람만 자기 세션을 폐기할 수 있으므로 별도 신원 확인이 필요하지 않다. */
    @PostMapping("/logout")
    void logout(@RequestBody SessionTokenRequest request) {
        sessionUseCase.logout(request.token());
    }
}
