package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확인 링크가 토큰을 프래그먼트에 담는지 고정한다.
 *
 * <p>이 테스트가 없으면 `#`를 `?`로 되돌려도 하네스가 통과한다. 형식을 지키는 것은 문서가
 * 아니라 여기다. 무엇을 왜 골랐는지는 ADR-0006에 있다.
 *
 * <p>발송은 대상이 아니므로 스케줄러 주기를 길게 두고 {@link MailSender}를 대역으로 막는다.
 */
@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000",
        "notifications.verification-base-url=https://shop.impati.dev/verify"
})
@RequiresDatabase
class VerificationLinkTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "notification-verification-link");
    }
    @Autowired
    private NotificationUseCase notificationUseCase;

    @MockBean
    private MailSender mailSender;

    /**
     * 토큰이 프래그먼트에 담긴다. 프래그먼트는 서버로 전송되지 않아 접근 로그에 남지 않는다.
     *
     * <p>링크 전체를 비교한다. `contains`로는 쿼리와 프래그먼트를 구분하지 못한다.
     */
    @Test
    void putsTokenInFragment() {
        notificationUseCase.requestEmailVerification(
                "mem_link", "link@impati.dev", "tok_link", "vmail_link");

        assertThat(bodyOf("link@impati.dev"))
                .contains("https://shop.impati.dev/verify#token=tok_link");
    }

    /**
     * 토큰이 쿼리스트링에 담기지 않는다.
     *
     * <p>위 단언과 짝이다. 링크를 하나 더 덧붙이는 식으로 형식이 늘어나도 쿼리 경로가 되살아나면
     * 여기서 걸린다.
     */
    @Test
    void neverPutsTokenInQueryString() {
        notificationUseCase.requestEmailVerification(
                "mem_query", "query@impati.dev", "tok_query", "vmail_query");

        assertThat(bodyOf("query@impati.dev")).doesNotContain("?token=");
    }

    private String bodyOf(String recipient) {
        return notificationUseCase.outbox().stream()
                .filter(entry -> recipient.equals(entry.recipient()))
                .map(OutboxEntry::body)
                .findFirst()
                .orElseThrow();
    }
}
