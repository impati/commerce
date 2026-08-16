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
 * 게이트웨이가 노출하지 않는 경로 (ADR-0003).
 *
 * <p>여기 있는 경로는 임의의 {@code memberId}로 회원을 조회하거나 세션 토큰을 신원으로 바꾼다.
 * 사용자에게 열리면 남의 정보를 읽거나 탈취한 토큰을 검증하는 데 쓸 수 있다.
 *
 * <p><strong>이 클래스가 존재하는 이유는 그 등급을 프리픽스 하나로 모으는 것이다.</strong>
 * 이전에는 내부 경로가 퍼블릭 경로와 같은 컨트롤러에 섞여 있어 어디까지가 외부에 열려도 되는
 * 경로인지 읽어서는 알 수 없었다. 프리픽스로 모아두면 게이트웨이 라우팅과 인그레스 규칙이
 * 무엇을 노출하지 말아야 하는지가 한 줄로 드러난다. 프리픽스를 최상위에 두는 것은 그 규칙이
 * 경로 중간의 와일드카드 없이 하나의 패턴으로 표현되게 하기 위해서다.
 *
 * <p>이 경로를 부를 수 있는 범위는 코드가 아니라 배포 토폴로지가 정한다 — 프라이빗망 안에서만
 * 닿는다 (ADR-0002). 애플리케이션은 호출자를 확인하지 않으므로, 프라이빗망 안의 어떤 서비스든
 * 이 경로를 부를 수 있다는 점은 받아들인 제약이다.
 */
@RestController
@RequestMapping("/internal/members")
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
