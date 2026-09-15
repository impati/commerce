package com.impati.commerce.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ServiceCallAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ServiceCallExecutor serviceCallExecutor(ObjectMapper objectMapper) {
        return new ServiceCallExecutor(objectMapper);
    }
}
