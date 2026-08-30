package com.impati.commerce.notification;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code persistence}의 통합 테스트를 위한 부트 클래스. 운영에는 없다 (ADR-0014).
 *
 * <p>이 컨텍스트는 core와 persistence의 빈만 스캔한다. 실행 단위가 클래스패스에 없기 때문이며,
 * 저장소 어댑터를 고립해서 검증하려는 것이 그 의도다.
 */
@SpringBootApplication
public class PersistenceTestApplication {
}
