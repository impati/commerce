package com.impati.commerce.order;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code client-member}의 테스트를 위한 부트 클래스. 운영에는 없다 (ADR-0015).
 *
 * <p>스캔을 클라이언트 패키지로 좁힌다. {@code core}에는 {@code OrderChanges}처럼 저장소를
 * 요구하는 빈이 있는데, 클라이언트 하나를 검증하는 데 그것까지 띄울 이유가 없다. 좁히지 않으면
 * 이 테스트가 클라이언트가 아니라 서비스 절반을 띄우게 된다.
 */
@SpringBootApplication(scanBasePackages = "com.impati.commerce.order.adapter.out.client")
public class ClientTestApplication {
}
