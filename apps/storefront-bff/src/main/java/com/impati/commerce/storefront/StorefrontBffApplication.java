package com.impati.commerce.storefront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

@SpringBootApplication(exclude = RestClientAutoConfiguration.class)
public class StorefrontBffApplication {
    public static void main(String[] args) { SpringApplication.run(StorefrontBffApplication.class, args); }
}
