package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아웃박스 저장소의 점유를 고정한다 (ADR-0010).
 *
 * <p>점유가 배타성의 유일한 수단이다. 깨지면 여러 인스턴스가 같은 항목을 집어 같은 메일을
 * 두 번 보내는데, 예외가 나지 않으므로 다른 테스트로는 드러나지 않는다.
 *
 * <p>재시도 간격을 확인하려면 시간을 앞으로 돌릴 수 있어야 하므로 {@link Clock}을 테스트가
 * 조작하는 구현으로 바꾼다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-vmail;DB_CLOSE_DELAY=-1",
        // 발송기를 사실상 끈다. 이 테스트는 점유되지 않은 항목을 남기고 그것이 후보에 있는지
        // 단정하는데, 발송기가 그 사이에 집으면 후보에서 빠져 단정이 깨진다.
        "member.verification-mail-dispatch-interval=3600000"
})
class JdbcVerificationMailRepositoryTest {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-25T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private VerificationMailRepository verificationMailRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    /** 왕복 테스트는 쓰기와 읽기가 같은 방향으로 틀리면 통과한다. 컬럼을 직접 읽어 막는다. */
    @Test
    void writesEachFieldToItsOwnColumn() {
        var mail = new VerificationMail(member("column"), "column@impati.dev", "raw-column-token");

        verificationMailRepository.save(mail);

        var row = jdbc.queryForMap(
                "select member_id, email, token, status, attempts, last_error, next_attempt_after"
                        + " from verification_mails where id = ?",
                mail.id()
        );
        assertThat(row.get("MEMBER_ID")).isEqualTo(mail.memberId());
        assertThat(row.get("EMAIL")).isEqualTo("column@impati.dev");
        assertThat(row.get("TOKEN")).isEqualTo("raw-column-token");
        assertThat(row.get("STATUS")).isEqualTo("PENDING");
        assertThat(row.get("ATTEMPTS")).isEqualTo(0);
        assertThat(row.get("LAST_ERROR")).isNull();
        assertThat(row.get("NEXT_ATTEMPT_AFTER")).isNull();
    }

    /** 점유는 한 번만 성립한다. 두 번째 호출이 값을 받으면 다른 인스턴스가 같은 메일을 또 보낸다. */
    @Test
    void claimsAtMostOnce() {
        var mail = new VerificationMail(member("once"), "once@impati.dev", "raw-once-token");
        verificationMailRepository.save(mail);

        var first = verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY);
        var second = verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY);

        assertThat(first).isPresent();
        assertThat(first.orElseThrow().token()).isEqualTo("raw-once-token");
        assertThat(second).isEmpty();
    }

    /**
     * 점유가 곧 백오프다. 실패해서 아무것도 쓰지 않아도 간격이 지나기 전에는 다시 집히지 않고,
     * 지나면 별도 복구 없이 다시 집힌다.
     */
    @Test
    void becomesClaimableAgainAfterRetryDelay() {
        var mail = new VerificationMail(member("backoff"), "backoff@impati.dev", "raw-backoff-token");
        verificationMailRepository.save(mail);
        verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY);

        clock.advance(RETRY_DELAY.minusSeconds(1));
        assertThat(candidates()).doesNotContain(mail.id());
        assertThat(verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY)).isEmpty();

        clock.advance(Duration.ofSeconds(2));
        assertThat(candidates()).contains(mail.id());
        assertThat(verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY)).isPresent();
    }

    /**
     * 항목 저장이 점유를 지우면 안 된다.
     *
     * <p>처리 중에 상태를 저장하는 것은 정상 경로다. 그때 {@code next_attempt_after}가 초기화되면
     * 자기가 걸어둔 점유를 자기가 풀어버려 다른 인스턴스가 곧바로 같은 항목을 집는다.
     */
    @Test
    void keepsClaimWhenItemIsSaved() {
        var mail = new VerificationMail(member("keep"), "keep@impati.dev", "raw-keep-token");
        verificationMailRepository.save(mail);
        var claimed = verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY).orElseThrow();

        claimed.markFailed("connection refused", 3);
        verificationMailRepository.save(claimed);

        assertThat(jdbc.queryForObject(
                "select next_attempt_after from verification_mails where id = ?",
                Object.class, mail.id()))
                .isNotNull();
        assertThat(verificationMailRepository.claimForDispatch(mail.id(), RETRY_DELAY)).isEmpty();
    }

    /** 종단 상태는 더 보낼 것이 없다. 후보로 돌아오면 발송이 끝나지 않는다. */
    @Test
    void excludesTerminalItemsFromCandidates() {
        var sent = new VerificationMail(member("sent"), "sent@impati.dev", "raw-sent-token");
        var failed = new VerificationMail(member("failed"), "failed@impati.dev", "raw-failed-token");
        verificationMailRepository.save(sent);
        verificationMailRepository.save(failed);

        sent.markSent();
        failed.markFailed("timeout", 1);
        verificationMailRepository.save(sent);
        verificationMailRepository.save(failed);

        assertThat(candidates()).doesNotContain(sent.id(), failed.id());
        assertThat(verificationMailRepository.claimForDispatch(sent.id(), RETRY_DELAY)).isEmpty();
        assertThat(jdbc.queryForObject(
                "select token from verification_mails where id = ?", String.class, sent.id()))
                .isNull();
    }

    /** 오래된 것부터 준다. 순서가 없으면 밀린 상황에서 앞쪽 항목이 계속 밀린다. */
    @Test
    void givesOldestCandidatesFirst() {
        var older = new VerificationMail(member("older"), "older@impati.dev", "raw-older-token");
        var newer = new VerificationMail(member("newer"), "newer@impati.dev", "raw-newer-token");
        verificationMailRepository.save(older);
        verificationMailRepository.save(newer);

        var mine = candidates().stream()
                .filter(id -> id.equals(older.id()) || id.equals(newer.id()))
                .toList();

        assertThat(mine).containsExactly(older.id(), newer.id());
    }

    /** 배치 상한이 없으면 밀린 건수가 한 주기의 길이를 정한다. */
    @Test
    void limitsCandidateCountToBatchSize() {
        verificationMailRepository.save(
                new VerificationMail(member("batch1"), "batch1@impati.dev", "raw-batch1-token"));
        verificationMailRepository.save(
                new VerificationMail(member("batch2"), "batch2@impati.dev", "raw-batch2-token"));

        assertThat(verificationMailRepository.findDispatchCandidates(1)).hasSize(1);
    }

    private List<String> candidates() {
        return verificationMailRepository.findDispatchCandidates(100);
    }

    private String member(String suffix) {
        var member = new Member(
                "mem_vmail_" + suffix,
                suffix + "@impati.dev",
                "Outbox Tester",
                new PasswordHash("$2a$10$fakehashforpersistencetest")
        );
        memberRepository.save(member);
        return member.id();
    }
}
