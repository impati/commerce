package com.impati.commerce.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션을 확인할 수 없는 것과 세션이 유효하지 않은 것을 게이트웨이가 구분하는지 본다.
 *
 * <p>둘을 같은 401로 답하면 우리 쪽 장애가 사용자에게 "세션이 만료됐다"로 전달되고, 그 말을 믿은
 * 클라이언트가 서버에 살아 있는 세션의 토큰을 버린다. member-service의 몇 초짜리 지연이 전원
 * 재로그인이 되는 경로이며 BL-0043이 그것을 다룬다.
 *
 * <p>member-service는 {@link org.springframework.test.web.client.MockRestServiceServer}로 stub한다.
 * 게이트웨이만 실제로 뜨고 컨트롤러 → {@code MemberIdentity} → 예외 변환 → HTTP 상태까지 실제
 * 코드가 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionResolveFailureTest {
    private static final String RESOLVE = "http://localhost:8101/internal/members/sessions/resolve";
    private static final String TOKEN = "Bearer tok_resolve_failure";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockServerRestClientCustomizer restClientCustomizer;

    @BeforeEach
    void resetStubs() {
        restClientCustomizer.getServer().reset();
    }

    /** 세션이 없거나 만료됐거나 폐기됐을 때 member-service가 내는 것이 404다. 이것만 인증 실패다. */
    @Test
    void 세션이_유효하지_않으면_401이다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(RESOLVE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/me").header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());
    }

    /**
     * member-service가 고장 난 것은 사용자 세션의 문제가 아니다.
     *
     * <p>이 단언이 이 테스트의 요점이다. 401로 답하면 클라이언트가 토큰을 버린다.
     */
    @Test
    void member_service가_5xx면_503이다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(RESOLVE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(get("/me").header("Authorization", TOKEN))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * 연결 실패와 타임아웃도 마찬가지다.
     *
     * <p>이쪽은 {@code ResourceAccessException}이라 응답 상태 기반 분기에 걸리지 않는다. 변환하지
     * 않으면 500으로 새어 나가고, 그것 역시 클라이언트가 토큰을 버리는 경로였다.
     */
    @Test
    void member_service에_닿지_못하면_503이다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(RESOLVE))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        mockMvc.perform(get("/me").header("Authorization", TOKEN))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * 404가 아닌 4xx는 게이트웨이가 잘못된 요청을 보냈다는 뜻이다.
     *
     * <p>사용자 세션과 무관하므로 401도 503도 아니다. 401로 뭉치면 계약이 어긋난 배포를
     * "세션 만료"로 읽게 된다.
     */
    @Test
    void 요청_자체가_거절되면_500이다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(RESOLVE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/me").header("Authorization", TOKEN))
                .andExpect(status().isInternalServerError());
    }

    /**
     * 확인에 성공하면 그 신원이 하위 서비스로 넘어간다.
     *
     * <p>실패 경로만 고정하면 전부 503으로 답해도 통과하므로 성공 경로를 함께 둔다. 확인 결과가
     * {@code X-Member-Id}로 실제로 실려 나가는 것까지 본다 — 게이트웨이가 하는 일이 그것이다.
     */
    @Test
    void 세션이_유효하면_신원을_넘긴다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(RESOLVE))
                .andRespond(withSuccess("{\"memberId\":\"mem_ok\"}", MediaType.APPLICATION_JSON));
        restClientCustomizer.getServer()
                .expect(requestTo("http://localhost:8101/members/me"))
                .andExpect(header("X-Member-Id", "mem_ok"))
                .andRespond(withSuccess(
                        "{\"id\":\"mem_ok\",\"email\":\"ok@impati.dev\",\"name\":\"ok\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/me").header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class StubMemberService {
        @Bean
        MockServerRestClientCustomizer restClientCustomizer() {
            return new MockServerRestClientCustomizer();
        }
    }
}
