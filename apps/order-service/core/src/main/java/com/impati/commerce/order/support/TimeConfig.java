package com.impati.commerce.order.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각을 주입해서 쓴다.
 *
 * <p>점유와 재시도 간격이 전부 시각 판정이므로, 주입하지 않으면 "실패한 뒤 간격이 지나기
 * 전에는 다시 집히지 않는다"를 테스트가 고정할 수 없다.
 *
 * <p>{@code core}에 있는 이유는 API와 워커가 둘 다 저장소를 쓰기 때문이다. 진입점마다 두면
 * 두 벌이 되고, 두 벌이 되면 어긋난다.
 */
@Configuration
public class TimeConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
