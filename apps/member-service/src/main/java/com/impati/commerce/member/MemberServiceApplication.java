package com.impati.commerce.member;

import com.impati.commerce.member.application.MemberService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner seedDemoMember(MemberService members) {
        return args -> {
            var member = members.seed("mem_demo", "demo@impati.test", "Demo Customer");
            members.addAddress(
                    member.id(),
                    "home",
                    "Demo Customer",
                    "010-0000-0000",
                    "123 Commerce Road",
                    "Seoul",
                    "04524",
                    true
            );
        };
    }
}
