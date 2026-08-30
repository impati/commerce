package com.impati.commerce.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 실제 DB 컨테이너가 있어야 도는 테스트.
 *
 * <p>{@code -PexcludeTags=infra}로 빼면 도커 없이 도는 것만 남는다. pre-commit이 그 형태로 돌고,
 * 전체는 작업이 끝나는 시점에 {@code make verify}가 돈다 (ADR-0013).
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("infra")
public @interface RequiresDatabase {
}
