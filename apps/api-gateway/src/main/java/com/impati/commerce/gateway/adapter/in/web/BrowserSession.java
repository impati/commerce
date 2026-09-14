package com.impati.commerce.gateway.adapter.in.web;

import com.impati.commerce.common.ApiContracts.LoginResponse;
import com.impati.commerce.common.DomainException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 브라우저와 게이트웨이 사이의 장기 세션 운반을 맡는다. */
@Component
public class BrowserSession {
    public static final String COOKIE_NAME = "impati_session";

    private final Clock clock;
    private final boolean secure;
    private final Set<String> allowedOrigins;

    public BrowserSession(
            Clock clock,
            @Value("${gateway.session-cookie.secure:true}") boolean secure,
            @Value("${gateway.browser.allowed-origins}") String allowedOrigins
    ) {
        this.clock = clock;
        this.secure = secure;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException("gateway.browser.allowed-origins must not be empty");
        }
    }

    public void requireTrustedOrigin(String origin) {
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new DomainException("forbidden", "request origin is not allowed", 403);
        }
    }

    public String requireToken(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            throw new DomainException("unauthorized", "authentication is required", 401);
        }
        return cookieValue;
    }

    public void start(LoginResponse issued, HttpServletResponse response) {
        var expiresAt = Instant.parse(issued.sessionExpiresAt());
        var maxAge = Duration.between(clock.instant(), expiresAt);
        if (maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        add(response, ResponseCookie.from(COOKIE_NAME, issued.sessionToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build());
    }

    public void clear(HttpServletResponse response) {
        add(response, ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build());
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
