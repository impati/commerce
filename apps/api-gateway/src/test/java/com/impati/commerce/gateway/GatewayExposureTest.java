package com.impati.commerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트웨이가 내부 경로를 외부에 그대로 뚫지 않았는지 본다.
 *
 * <p>[ADR-0003] 노출하지 않을 경로는 하위 서비스에서 {@code /internal} 프리픽스 아래에 있다.
 * 게이트웨이가 같은 모양의 외부 경로를 열면 프리픽스가 선언한 등급이 무의미해진다.
 *
 * <p>이 테스트가 잡지 못하는 것이 있다. 어떤 경로가 내부 등급이어야 하는지는 사람이 판단하며,
 * {@code apps/*}가 서로 의존하지 않으므로 어떤 테스트도 "이 경로를 형제 서비스만 부른다"를 알
 * 수 없다. 여기서 잡히는 것은 <strong>내부 경로를 게이트웨이에 뚫는 사고</strong> 하나다.
 */
@SpringBootTest
class GatewayExposureTest {
    /** actuator도 같은 타입의 빈을 등록하므로 컨트롤러용 매핑을 이름으로 고른다. */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void 게이트웨이는_internal_경로를_외부에_노출하지_않는다() {
        var exposed = externalPatterns();

        assertThat(exposed).isNotEmpty();
        assertThat(exposed).noneMatch(pattern -> pattern.contains("/internal"));
    }

    private List<String> externalPatterns() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternsCondition() == null
                        ? info.getPathPatternsCondition().getPatternValues().stream()
                        : info.getPatternsCondition().getPatterns().stream())
                .toList();
    }
}
