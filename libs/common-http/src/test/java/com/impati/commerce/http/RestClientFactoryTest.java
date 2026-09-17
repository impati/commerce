package com.impati.commerce.http;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestClientFactoryTest {
    @Test
    void routeReadTimeoutAllowsASlowResponseWithoutChangingTheDefault() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(workers);
            server.createContext("/slow", exchange -> {
                try {
                    Thread.sleep(300);
                    var body = "received".getBytes();
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            try {
                var beans = new DefaultListableBeanFactory();
                beans.registerSingleton("timeouts", new HttpClientTimeoutCustomizer(
                        new HttpClientTimeoutProperties(Duration.ofSeconds(1), Duration.ofMillis(50))));
                var factory = new RestClientFactory(beans.getBeanProvider(RestClientCustomizer.class));
                var url = "http://127.0.0.1:" + server.getAddress().getPort();
                var normal = factory.forBaseUrl(url);
                var route = factory.forBaseUrl(url, Duration.ofSeconds(2));

                assertThrows(ResourceAccessException.class,
                        () -> normal.get().uri("/slow").retrieve().body(String.class));
                assertEquals("received", route.get().uri("/slow").retrieve().body(String.class));
                assertThrows(ResourceAccessException.class,
                        () -> normal.get().uri("/slow").retrieve().body(String.class));
            } finally {
                server.stop(0);
            }
        }
    }
}
