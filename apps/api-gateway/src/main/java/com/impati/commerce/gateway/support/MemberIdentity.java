package com.impati.commerce.gateway.support;

import com.impati.commerce.common.ApiContracts.SessionResponse;
import com.impati.commerce.common.ApiContracts.SessionTokenRequest;
import com.impati.commerce.common.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
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
 * <p>TODO 세션 확인이 member-service에 요청마다 의존한다. 캐시를 붙이면 지연과 부하는 줄지만
 * 미스와 콜드 캐시에서는 여전히 의존하므로 결합 자체는 하이브리드로 가야 끊긴다. 어느 쪽이든
 * 폐기가 즉시에서 수명 내로 바뀌므로 그 지연을 먼저 정해야 한다. BL-0003.
 */
@Component
public class MemberIdentity {
    private static final String BEARER = "Bearer ";

    private final RestClient members;

    public MemberIdentity(RestClient memberRestClient) {
        this.members = memberRestClient;
    }

    /**
     * 인증이 필요한 경로에서 쓴다. 토큰이 없거나 유효하지 않으면 401이다.
     *
     * <p><b>세션이 유효하지 않은 것과 세션을 확인할 수 없는 것을 구분한다.</b> 둘을 같은 401로
     * 답하면 우리 쪽 장애를 사용자에게 "당신 세션이 만료됐다"고 알리는 것이 되고, 그 말을 믿은
     * 클라이언트가 멀쩡한 토큰을 버린다. 서버에 14일 살아 있는 세션이 member-service의 몇 초짜리
     * 지연 때문에 사라진다. 확인할 수 없는 동안에는 503으로 답해 재시도하면 된다는 것을 알린다.
     */
    public String require(String authorizationHeader) {
        var token = bearerToken(authorizationHeader);
        SessionResponse session;
        try {
            session = members.post()
                    .uri("/internal/members/sessions/resolve")
                    .body(new SessionTokenRequest(token))
                    .retrieve()
                    .body(SessionResponse.class);
        } catch (RestClientResponseException exception) {
            throw resolveFailed(exception);
        } catch (ResourceAccessException exception) {
            // 연결 실패와 타임아웃. 세션에 대해 아무것도 알아내지 못했다.
            throw DomainException.unavailable("session could not be resolved");
        }
        if (session == null) {
            throw unauthorized();
        }
        return session.memberId();
    }

    /**
     * member-service의 응답 상태를 게이트웨이의 응답으로 옮긴다.
     *
     * <p>404만 인증 실패다 — 세션이 없거나 만료됐거나 폐기됐을 때 member-service가 내는 것이
     * 404이고, 넷을 구분하지 않는 것은 정해진 규칙이다 (PD-0002-R7). 5xx는 member-service가
     * 고장 난 것이고, 그 밖의 4xx는 게이트웨이가 잘못된 요청을 보냈다는 뜻이라 사용자 세션과
     * 무관하다. 뒤의 둘을 401로 뭉치면 원인이 사라진다.
     */
    private DomainException resolveFailed(RestClientResponseException exception) {
        var status = exception.getStatusCode();
        if (status.value() == 404) {
            return unauthorized();
        }
        if (status.is5xxServerError()) {
            return DomainException.unavailable("session could not be resolved");
        }
        return new DomainException("internal_error", "session resolve request was rejected", 500);
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
