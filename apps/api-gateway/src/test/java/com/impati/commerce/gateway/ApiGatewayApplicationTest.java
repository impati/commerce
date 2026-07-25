package com.impati.commerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 컨텍스트가 뜨는지만 확인하는 스모크 테스트.
 *
 * <p>저장소 포트의 구현이 사라지거나 둘로 늘어나면, 설정값이 빠지면 여기서 깨진다.
 * 서비스별 시나리오 테스트가 생기면 이 파일은 지워도 된다.
 */
@SpringBootTest
class ApiGatewayApplicationTest {
    @Test
    void contextLoads() {
    }
}
