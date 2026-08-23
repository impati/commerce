package com.impati.commerce.gateway.support;

import com.impati.commerce.common.ApiContracts.SessionResponse;
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.common.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 세션 토큰을 회원 신원으로 바꾼다.
 *
 * <p>토큰 검증 지식은 게이트웨이에만 있다. 하위 서비스는 토큰을 모르고 {@code X-Member-Id}만
 * 신뢰한다. 그래서 하위 서비스는 게이트웨이 뒤에만 있어야 한다 — 직접 노출되면 헤더를 위조해
 * 아무 회원으로 행세할 수 있다. 이 경계는 코드가 아니라 배포 토폴로지가 강제한다: 게이트웨이만
 * 퍼블릭 인그레스이고 나머지는 프라이빗망에 둔다 (ADR-0002). 로컬에서 8101~8109가 열려 있는
 * 것은 프로세스를 나란히 띄운 결과이지 운영 토폴로지가 아니다.
 *
 * <p>불투명 토큰이므로 요청마다 member-service를 부른다. 폐기가 즉시 되는 대가다.
 *
 * <p>TODO 세션 확인에 캐시가 없어 요청마다 홉이 하나 붙는다. 짧은 TTL 캐시가 필요하고, 그 TTL이
 * 폐기 지연 상한이 된다. BL-0003.
 */
@Component
public class MemberIdentity {
    private static final String BEARER = "Bearer ";

    private final RestClient members;

    public MemberIdentity(RestClient memberRestClient) {
        this.members = memberRestClient;
    }

    /** 인증이 필요한 경로에서 쓴다. 토큰이 없거나 유효하지 않으면 401이다. */
    public String require(String authorizationHeader) {
        var token = bearerToken(authorizationHeader);
        try {
            var session = members.post()
                    .uri("/internal/members/sessions/resolve")
                    .body(new SessionTokenRequest(token))
                    .retrieve()
                    .body(SessionResponse.class);
            if (session == null) {
                throw unauthorized();
            }
            return session.memberId();
        } catch (RestClientResponseException exception) {
            throw unauthorized();
        }
    }

    public String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            throw unauthorized();
        }
        var token = authorizationHeader.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            throw unauthorized();
        }
        return token;
    }

    private DomainException unauthorized() {
        return new DomainException("unauthorized", "authentication is required", 401);
    }
}
