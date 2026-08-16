package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.SessionResponse;
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.member.application.MemberService;
import com.impati.commerce.member.application.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스만 부르는 경로. 게이트웨이는 이 프리픽스를 외부에 노출하지 않는다.
 *
 * <p>여기 있는 경로는 임의의 {@code memberId}로 회원을 조회하거나 세션 토큰을 신원으로 바꾼다.
 * 사용자에게 열리면 남의 정보를 읽거나 탈취한 토큰을 검증하는 데 쓸 수 있다.
 *
 * <p><strong>이 클래스가 존재하는 이유는 그 등급을 프리픽스 하나로 모으는 것이다.</strong>
 * 서비스 간 공유 시크릿 검증을 붙일 때 {@code /members/internal/**} 하나에 걸면 되고, 다음에
 * 내부 경로를 추가하는 사람이 검증을 빠뜨릴 수 없다. 이전에는 내부 경로가 퍼블릭 경로와 같은
 * 컨트롤러에 섞여 있었고 보호 수단이 주석뿐이었다. BL-0001이 이 검증을 붙이는 일이다.
 */
@RestController
@RequestMapping("/members/internal")
public class InternalMemberController {
    private final MemberService members;
    private final SessionService sessions;

    public InternalMemberController(MemberService members, SessionService sessions) {
        this.members = members;
        this.sessions = sessions;
    }

    /** 게이트웨이가 세션 토큰을 회원 식별자로 바꾼다. */
    @PostMapping("/sessions/resolve")
    SessionResponse resolveSession(@RequestBody SessionTokenRequest request) {
        return sessions.resolveSession(request.token());
    }

    /** order-service가 배송지를 읽기 위한 경로. */
    @GetMapping("/{memberId}")
    MemberResponse get(@PathVariable String memberId) {
        return members.get(memberId);
    }
}
