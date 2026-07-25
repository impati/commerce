package com.impati.commerce.order.adapter.out.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 응답하지 않는 상대에게 호출이 매달리지 않는지 확인한다.
 *
 * <p>타임아웃이 없으면 상대 서비스가 응답을 멈출 때 호출자의 스레드가 그대로 잡힌다.
 * checkout saga에서는 결제 서비스 하나가 멈추면 주문 전체가 멈추고 보상도 돌지 않는다.
 * 설정이 실제로 적용되는지는 측정으로만 알 수 있으므로 테스트로 고정한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-timeout;DB_CLOSE_DELAY=-1",
        "clients.http.read-timeout=300ms"
})
class RestClientTimeoutTest {
    private static ServerSocket silentServer;
    private static ExecutorService acceptor;

    @Autowired
    private CommerceClients clients;

    @BeforeAll
    static void startSilentServer() throws IOException {
        silentServer = new ServerSocket(0);
        acceptor = Executors.newSingleThreadExecutor();
        acceptor.submit(() -> {
            while (!silentServer.isClosed()) {
                try {
                    // 연결은 받아주고 응답은 하지 않는다. read 타임아웃이 걸리는 상황이다.
                    silentServer.accept();
                } catch (IOException stopped) {
                    return;
                }
            }
        });
    }

    @AfterAll
    static void stopSilentServer() throws IOException {
        acceptor.shutdownNow();
        silentServer.close();
    }

    @DynamicPropertySource
    static void pointMemberClientAtSilentServer(DynamicPropertyRegistry registry) {
        registry.add("clients.member.url", () -> "http://localhost:" + silentServer.getLocalPort());
    }

    @Test
    void failsFastWhenPeerNeverResponds() {
        var startedAt = System.nanoTime();

        assertThatThrownBy(() -> clients.member("mem_demo"))
                .isInstanceOf(ResourceAccessException.class);

        var elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        assertThat(elapsedMillis)
                .describedAs("read 타임아웃 300ms가 적용되면 즉시 끊긴다. 여유를 두고 2초 이내를 본다")
                .isLessThan(2_000);
    }
}
