package com.impati.commerce.order;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code core}의 통합 테스트를 위한 부트 클래스. 운영에는 없다 (ADR-0014).
 *
 * <p>{@code core}에는 실행 단위가 없으므로 {@code @SpringBootApplication}도 없다. 그런데
 * {@code @SpringBootTest}는 그것을 요구한다.
 *
 * <p><b>이 컨텍스트는 core의 빈만 스캔한다.</b> api와 worker가 클래스패스에 없기 때문이며,
 * 그것이 원하는 바다 — 저장소 어댑터와 변경 단위를 고립해서 검증한다. 컨트롤러나 스케줄러가
 * 함께 뜨면 무엇을 검증하는지가 흐려진다.
 */
@SpringBootApplication
public class CoreTestApplication {
}
