package com.impati.commerce.member.adapter.in.seed;

import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.application.port.out.PasswordHasher;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 회원 시드. {@code local} 프로파일에서만 존재한다.
 *
 * <p>이 클래스가 하는 일은 <strong>id를 지정해서, 정해진 비밀번호로, 이메일 소유 확인을 건너뛰고
 * 활성 계정을 만드는 것</strong>이다. 운영에 있어서는 안 되는 능력이므로 클래스 자체를
 * 프로파일로 막는다. 이전에는 이 로직이 {@code MemberService.seed}로 모든 환경에 존재했고
 * 호출자만 프로파일로 막혀 있었다 — 같은 앱의 다른 코드가 부르는 것을 막을 수 없는 상태였다.
 *
 * <p>파일 DB는 데이터가 남으므로 이미 있으면 아무것도 하지 않는다.
 *
 * <p>앱 시작이 트리거인 진입점이므로 컨트롤러와 같은 등급이고 {@code adapter/in}에 산다.
 */
@Component
@Profile("local")
public class LocalDemoSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    private static final String DEMO_ID = "mem_demo";
    private static final String DEMO_EMAIL = "demo@impati.test";
    private static final String DEMO_NAME = "Demo Customer";
    private static final String DEMO_PASSWORD = "demo-password";

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;

    LocalDemoSeeder(MemberRepository members, PasswordHasher passwordHasher) {
        this.members = members;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (members.findByEmail(DEMO_EMAIL).isPresent()) {
            return;
        }

        var member = new Member(DEMO_ID, DEMO_EMAIL, DEMO_NAME, passwordHasher.hash(DEMO_PASSWORD));
        member.activate();
        member.addAddress(new Address(
                "home",
                DEMO_NAME,
                "010-0000-0000",
                "123 Commerce Road",
                "Seoul",
                "04524",
                true
        ));
        members.save(member);
        log.info("seeded demo member memberId={}", DEMO_ID);
    }
}
