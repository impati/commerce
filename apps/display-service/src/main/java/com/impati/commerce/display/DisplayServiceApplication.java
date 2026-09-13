package com.impati.commerce.display;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;

@SpringBootApplication(exclude = RestClientAutoConfiguration.class)
public class DisplayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DisplayServiceApplication.class, args);
    }
}
