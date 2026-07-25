package com.impati.commerce.member;

import com.impati.commerce.member.application.MemberService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@SpringBootApplication
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }

    /** 시간을 주입 가능하게 둔다. 만료 검사를 테스트에서 제어할 수 있어야 한다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 데모 회원 시드. {@code local} 프로파일에서만 동작한다.
     *
     * <p>알려진 비밀번호를 가진 계정이 운영에 존재할 수 없게 하려는 것이다. 프로파일이 없으면
     * 시드 자체가 빈으로 만들어지지 않는다.
     */
    @Bean
    @Profile("local")
    ApplicationRunner seedDemoMember(MemberService members) {
        return args -> {
            var member = members.seed("mem_demo", "demo@impati.test", "Demo Customer", "demo-password");
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
