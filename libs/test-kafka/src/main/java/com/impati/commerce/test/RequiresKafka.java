package com.impati.commerce.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 실제 브로커 컨테이너가 있어야 도는 테스트.
 *
 * <p>{@code -PexcludeTags=infra}로 빼면 도커 없이 도는 것만 남는다. {@link RequiresDatabase}와
 * 같은 태그를 쓴다 — 빠른 레인의 기준은 "어떤 인프라인가"가 아니라 "도커가 필요한가"다.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("infra")
public @interface RequiresKafka {
}
