package com.impati.commerce.member.application.component;

import com.impati.commerce.member.application.port.in.VerificationMailDispatchSummary;
import com.impati.commerce.member.application.port.in.VerificationMailDispatchUseCase;
import com.impati.commerce.member.application.port.out.NotificationClient;
import com.impati.commerce.member.application.port.out.VerificationMailRepository;
import com.impati.commerce.member.domain.MemberModels.VerificationMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 아웃박스에 쌓인 인증 메일을 내보낸다 (ADR-0010).
 *
 * <p>기록하는 {@link RegistrationExecutor}와 나뉘어 있는 이유는 협력자가 겹치지 않기
 * 때문이다. 기록하는 쪽은 회원 저장소와 토큰 발급기를 쓰고, 여기는 아웃박스와 알림
 * 클라이언트만 쓴다.
 *
 * <p>여기서만 알림 서비스를 부른다. 그래서 그 호출이 어떤 DB 트랜잭션에도 들어가지 않는다는
 * 것이 클래스 경계로 드러난다.
 */
@Component
public class VerificationMailDispatchExecutor implements VerificationMailDispatchUseCase {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailDispatchExecutor.class);

    private final VerificationMailRepository verificationMailRepository;
    private final NotificationClient notificationClient;
    private final int batchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    /**
     * 설정값이 잘못되면 기동을 실패시킨다.
     *
     * <p>{@code batchSize}가 0이면 후보가 항상 비어 아무것도 집지 않는다. {@code retryDelay}가
     * 0이면 점유가 즉시 만료돼 백오프가 사라지고 장애 중 재시도 증폭이 되살아난다.
     * {@code maxAttempts}가 0이면 첫 시도에서 곧바로 포기한다. 셋 다 <b>조용히 잘못 도는</b>
     * 실패이며, 그 결과는 인증 메일이 안 가는 것 — 즉 가입한 사람이 로그인하지 못하는 것이다.
     */
    public VerificationMailDispatchExecutor(
            VerificationMailRepository verificationMailRepository,
            NotificationClient notificationClient,
            @Value("${member.verification-mail-batch-size:50}") int batchSize,
            @Value("${member.verification-mail-retry-delay:60s}") Duration retryDelay,
            @Value("${member.verification-mail-max-attempts:3}") int maxAttempts
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "member.verification-mail-batch-size must be positive but was " + batchSize);
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "member.verification-mail-retry-delay must be positive but was " + retryDelay);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "member.verification-mail-max-attempts must be positive but was " + maxAttempts);
        }
        this.verificationMailRepository = verificationMailRepository;
        this.notificationClient = notificationClient;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 후보를 훑고 건별로 점유해 보낸다.
     *
     * <p>점유를 배치 전체에 미리 걸지 않고 처리 직전에 하나씩 거는 이유는, 점유가 덮어야 하는
     * 시간이 그 한 건의 작업 시간이면 되기 때문이다 (ADR-0009). 미리 걸면 배치가 길어질 때
     * 앞쪽 건의 점유가 처리 중에 만료돼 다른 인스턴스가 같은 메일을 또 보낸다.
     *
     * <p>한 건의 실패가 나머지를 막지 않는다. 실패한 건은 PENDING으로 남아 다음 주기의
     * 대상이 되고, 점유가 시각을 이미 밀어두었으므로 최소 간격 안에는 다시 집히지 않는다.
     */
    @Override
    public VerificationMailDispatchSummary dispatchPending() {
        var candidates = verificationMailRepository.findDispatchCandidates(batchSize);
        var claimed = 0;
        var sent = 0;
        for (var mailId : candidates) {
            var mail = verificationMailRepository.claimForDispatch(mailId, retryDelay);
            if (mail.isEmpty()) {
                continue;
            }
            claimed++;
            try {
                if (dispatch(mail.get())) {
                    sent++;
                }
            } catch (RuntimeException failure) {
                // 시도 결과를 적는 것까지 실패한 경우다. 점유가 이미 시각을 밀어두었으므로
                // 이 건은 최소 간격 뒤에 다시 집힌다 — 여기서 복구할 것이 없다.
                log.error("verification mail dispatch aborted, claim keeps the backoff id={}", mailId, failure);
            }
        }
        if (claimed > 0) {
            log.info("verification mail dispatch candidates={} claimed={} sent={}",
                    candidates.size(), claimed, sent);
        }
        return new VerificationMailDispatchSummary(candidates.size(), claimed, sent);
    }

    /**
     * 한 통을 보내고 결과를 기록한다.
     *
     * <p>실패를 삼키지 않는다. 시도 횟수와 마지막 오류가 남고, 한도를 넘기면 FAILED가 되어
     * 조회로 드러난다. 이것이 이전 구현과의 차이다 — 그때는 실패가 로그 한 줄로 사라졌다.
     *
     * <p><b>전이는 최대 한 번, 저장도 한 번이다.</b> {@code try}가 전송만 감싸므로
     * {@code markFailed}는 항상 PENDING인 항목에만 도달한다. 그래서 전송에 성공한 메일이
     * 실패로 기록될 수 없다. 저장이 실패하면 예외가 그대로 올라가 <b>아무것도 기록되지
     * 않고</b>, 그 항목은 PENDING으로 남아 다음 주기가 처음부터 다시 한다 — 저장되지 않은
     * 것은 일어나지 않은 것이다.
     *
     * <p>남는 것은 전송 결과를 모르는 경우다. 읽기 타임아웃은 "안 갔다"가 아니므로 여기서
     * 실패로 보이는 건이 실제로는 도달했을 수 있다. 아웃박스가 푸는 문제가 아니다 (ADR-0010).
     */
    private boolean dispatch(VerificationMail mail) {
        try {
            notificationClient.requestEmailVerification(mail.memberId(), mail.email(), mail.token(), mail.id());
            mail.markSent();
        } catch (RuntimeException failure) {
            mail.markFailed(failure.getMessage(), maxAttempts);
            if (mail.isPending()) {
                log.warn("verification mail not sent, will retry id={} attempts={}",
                        mail.id(), mail.attempts(), failure);
            } else {
                log.error("verification mail given up id={} memberId={} attempts={}",
                        mail.id(), mail.memberId(), mail.attempts(), failure);
            }
        }
        verificationMailRepository.save(mail);
        return mail.isSent();
    }
}
