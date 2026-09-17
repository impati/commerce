package com.impati.commerce.gateway;

import com.impati.commerce.gateway.adapter.in.web.BrowserSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gateway.session-cookie.secure=true")
@AutoConfigureMockMvc
class BrowserSessionCookieTest {
    private static final String ORIGIN = "http://localhost:5173";
    private static final String LOGIN = "http://localhost:8101/members/login";
    private static final String REFRESH = "http://localhost:8101/internal/members/sessions/refresh";
    private static final String LOGOUT = "http://localhost:8101/members/logout";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockServerRestClientCustomizer restClientCustomizer;

    @BeforeEach
    void reset() {
        restClientCustomizer.getServer().reset();
    }

    @Test
    void 로그인은_세션_토큰을_HttpOnly_쿠키로만_돌려준다() throws Exception {
        var sessionExpiresAt = Instant.now().plus(Duration.ofDays(14));
        restClientCustomizer.getServer()
                .expect(requestTo(LOGIN))
                .andExpect(method(POST))
                .andRespond(withSuccess(("""
                        {
                          "sessionToken": "session_secret",
                          "sessionExpiresAt": "%s",
                          "accessToken": "access_short",
                          "accessTokenExpiresAt": "2099-01-01T00:05:00Z"
                        }
                        """).formatted(sessionExpiresAt), MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/login")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@impati.test\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").value("access_short"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("impati_session=session_secret"),
                        containsString("Path=/"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict")
                )));
    }

    @Test
    void 세션_쿠키로_접근_토큰을_갱신한다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(REFRESH))
                .andExpect(method(POST))
                .andExpect(content().json("{\"token\":\"session_secret\"}"))
                .andRespond(withSuccess("""
                        {"accessToken":"access_new","accessTokenExpiresAt":"2099-01-01T00:05:00Z"}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/sessions/refresh")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(BrowserSession.COOKIE_NAME, "session_secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_new"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/sessions/refresh", "/logout"})
    void 허용하지_않은_origin의_세션_요청은_거절한다(String path) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(BrowserSession.COOKIE_NAME, "session_secret")))
                .andExpect(status().isForbidden());

        restClientCustomizer.getServer().verify();
    }

    @Test
    void 로그아웃은_세션을_폐기하고_쿠키를_삭제한다() throws Exception {
        restClientCustomizer.getServer()
                .expect(requestTo(LOGOUT))
                .andExpect(method(POST))
                .andExpect(content().json("{\"token\":\"session_secret\"}"))
                .andRespond(withSuccess());

        mockMvc.perform(post("/logout")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(BrowserSession.COOKIE_NAME, "session_secret")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("impati_session="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict")
                )));
    }

    @TestConfiguration
    static class StubDownstream {
        @Bean
        MockServerRestClientCustomizer restClientCustomizer() {
            return new GatewayRestClientStubs();
        }
    }
}
