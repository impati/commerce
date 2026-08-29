package com.impati.commerce.notification.application.component;

import com.impati.commerce.common.Ids;
import com.impati.commerce.notification.application.port.in.NotificationDetails;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
public class NotificationExecutor implements NotificationUseCase {
    private static final Logger log = LoggerFactory.getLogger(NotificationExecutor.class);

    /**
     * 한 건을 처리하는 최악 시간.
     *
     * <p>수락 조회와 발송 두 번의 외부 호출이고, 각각 common-http 기본 타임아웃(연결 1초 ·
     * 읽기 3초)에 묶여 있다. 임차가 배치 전체를 덮는지 판정하는 기준이며, 타임아웃 기본값이
     * 바뀌면 이 값도 함께 움직여야 한다.
     */
    private static final Duration WORST_CASE_PER_ITEM = Duration.ofSeconds(8);

    private final NotificationRepository notificationRepository;
    private final MailSender mailSender;
    private final String verificationBaseUrl;
    private final int batchSize;
    private final Duration retryDelay;
    private final int maxAttempts;

    /**
     * 설정값이 잘못되면 기동을 실패시킨다.
     *
     * <p>{@code batchSize}가 0이면 후보가 항상 비어 아무것도 집지 않는다. {@code retryDelay}가
     * 0이면 점유가 즉시 만료돼 백오프가 사라지고, 인스턴스가 여럿일 때 배타성도 함께 사라진다.
     * {@code maxAttempts}가 0이면 첫 시도에서 곧바로 포기한다. 셋 다 <b>조용히 잘못 도는</b>
     * 실패이며 그 결과는 인증 메일이 안 가거나 두 번 가는 것이다.
     *
     * <p>넷째로 <b>임차가 배치 전체를 덮는지</b> 본다 (PD-0016-R6). 한 번에 집은 건을 다
     * 처리하기 전에 임차가 만료되면 아직 처리 중인 건을 다른 인스턴스가 집는다. 이 관계가
     * 깨지면 일괄 점유의 전제가 무너지므로 설정 두 값의 조합으로 깨뜨릴 수 없게 막는다.
     */
    public NotificationExecutor(
            NotificationRepository notificationRepository,
            MailSender mailSender,
            @Value("${notifications.verification-base-url}") String verificationBaseUrl,
            @Value("${notifications.dispatch-batch-size:5}") int batchSize,
            @Value("${notifications.dispatch-retry-delay:60s}") Duration retryDelay,
            @Value("${notifications.dispatch-max-attempts:3}") int maxAttempts
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "notifications.dispatch-batch-size must be positive but was " + batchSize);
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "notifications.dispatch-retry-delay must be positive but was " + retryDelay);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "notifications.dispatch-max-attempts must be positive but was " + maxAttempts);
        }
        var leaseNeeded = WORST_CASE_PER_ITEM.multipliedBy(batchSize);
        if (retryDelay.compareTo(leaseNeeded) < 0) {
            throw new IllegalArgumentException(
                    "notifications.dispatch-retry-delay must cover the whole batch: batch-size "
                            + batchSize + " needs at least " + leaseNeeded + " but was " + retryDelay);
        }
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.verificationBaseUrl = verificationBaseUrl;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    /** {@code @Transactional}을 붙이지 않는다. 저장소 호출이 하나뿐이라 묶을 것이 없다. */
    @Override
    public NotificationDetails record(String eventType, String memberId, String subject, String body) {
        var notification = new Notification(eventType, memberId, subject, body);
        notificationRepository.save(notification);
        return NotificationMapper.toDetails(notification);
    }

    /**
     * 인증 메일을 아웃박스에 적는다. 발송은 하지 않는다.
     *
     * <p>여기서 바로 발송하면 메일 시스템 장애가 가입 실패가 된다. 기록만 커밋하고 발송은
     * {@link #dispatchPending()}이 별도로 가져간다.
     *
     * <p>같은 멱등 키로 다시 오면 먼저 적힌 것을 그대로 돌려준다. 부르는 쪽은 응답을 못 받았을
     * 때 재시도할 수밖에 없으므로, 그 재시도가 메일 두 통이 되지 않게 하는 것이 여기의 일이다
     * (ADR-0011).
     *
     * <p>{@code @Transactional}을 붙이지 않는다. 저장소 호출이 하나뿐이라 묶을 것이 없고,
     * {@link NotificationRepository#saveIfAbsent}는 제약 위반 뒤에 이어서 읽어야 하므로
     * 트랜잭션 안에서 돌면 안 된다.
     *
     * <p>토큰은 쿼리가 아니라 프래그먼트에 담는다. 프래그먼트는 브라우저가 서버로 보내지 않으므로
     * 정적 호스트와 중간 프록시의 접근 로그에 남지 않는다. 쿼리에 담으면 토큰보다 오래 사는
     * 로그에 평문으로 쌓이고, 링크 스캐너처럼 JS를 실행하지 않는 요청은 토큰을 소진하지도 않은
     * 채 로그만 남긴다. 근거는 ADR-0006.
     */
    @Override
    public NotificationDetails requestEmailVerification(String memberId, String email, String token, String idempotencyKey) {
        var notification = Notification.mail(
                "EmailVerificationRequested",
                memberId,
                email,
                "이메일 주소를 확인해주세요",
                "아래 링크로 이메일 소유를 확인해주세요.\n" + verificationBaseUrl + "#token=" + token,
                idempotencyKey
        );
        return NotificationMapper.toDetails(notificationRepository.saveIfAbsent(notification));
    }

    /**
     * 아웃박스를 비운다.
     *
     * <p>보낼 수 있는 것들을 한 번에 점유하고 건별로 처리한다. 점유가 한 문장이므로 후보
     * 건수와 무관하게 왕복이 하나다 (ADR-0011).
     *
     * <p>임차는 이 묶음 전체를 처리하는 동안 유지돼야 한다. 처리가 길어져 임차가 만료되면 다른
     * 인스턴스가 남은 건을 집을 수 있는데, 그때도 발송 전에 수락 여부를 확인하므로 중복으로
     * 이어지지는 않는다.
     *
     * <p>한 건의 실패가 다음 건을 막지 않는다. 실패는 attempts와 last_error로 남고, 한도를
     * 넘으면 FAILED가 되어 조회로 드러난다. 예외를 삼켜 사라지게 하지 않는다.
     */
    @Override
    public int dispatchPending() {
        var dispatchId = Ids.newId("dsp");
        var claimed = notificationRepository.claimForDispatch(dispatchId, batchSize, retryDelay);
        var settled = 0;
        for (var notification : claimed) {
            try {
                if (deliverOnce(notification)) {
                    settled++;
                }
            } catch (RuntimeException failure) {
                // 결과를 적는 것까지 실패한 경우다. 점유가 이미 시각을 밀어두었으므로 이 건은
                // 최소 간격 뒤에 다시 집힌다 — 여기서 복구할 것이 없고, 나머지 건을 계속한다.
                log.error("notification dispatch aborted, claim keeps the backoff id={}",
                        notification.id(), failure);
            }
        }
        if (!claimed.isEmpty()) {
            log.info("notification dispatch id={} claimed={} settled={}",
                    dispatchId, claimed.size(), settled);
        }
        return settled;
    }

    /**
     * 한 통을 보내고 결과를 기록한다.
     *
     * <p>보내기 전에 벤더가 이미 수락했는지 묻는다. 이전 시도가 벤더까지 도달한 뒤 결과를
     * 적기 전에 끊겼으면 그 알림은 PENDING으로 남아 있는데, 그것을 다시 보내면 중복이다.
     * 수락돼 있으면 보내지 않고 종단시킨다.
     *
     * <p>수락 여부를 <b>모르면 이 주기는 보내지 않는다.</b> 모르는 상태에서 보내는 쪽을 고르면
     * 조회 장애가 곧 중복 발송이 된다. 점유가 다음 시도 시각을 이미 밀어두었으므로 이 건은
     * 최소 간격 뒤에 다시 판단되고, 그동안 시도 횟수를 쓰지 않는다 — 보내보지 않았으므로
     * 실패한 것이 아니다.
     */
    private boolean deliverOnce(Notification notification) {
        boolean accepted;
        try {
            accepted = mailSender.wasAccepted(notification.id());
        } catch (RuntimeException failure) {
            log.warn("mail acceptance unknown, not sending this cycle id={}", notification.id(), failure);
            return false;
        }
        if (accepted) {
            log.info("mail already accepted by vendor, settling without resend id={}", notification.id());
            notification.markSent();
            notificationRepository.save(notification);
            return true;
        }

        try {
            mailSender.send(
                    notification.recipient(), notification.subject(), notification.body(), notification.id());
            notification.markSent();
        } catch (RuntimeException failure) {
            notification.markFailed(failure.getMessage(), maxAttempts);
            log.warn("mail delivery failed id={} attempts={}", notification.id(), notification.attempts());
        }
        notificationRepository.save(notification);
        return notification.isSent();
    }

    @Transactional(readOnly = true)
    @Override
    public List<NotificationDetails> listFor(String memberId) {
        return notificationRepository.findByMemberId(memberId).stream().map(NotificationMapper::toDetails).toList();
    }

    /** 로컬 데모에서 발송함을 들여다본다. 인증 토큰을 확인할 유일한 경로다. */
    @Transactional(readOnly = true)
    @Override
    public List<OutboxEntry> outbox() {
        return notificationRepository.findAll().stream().map(NotificationMapper::toOutboxEntry).toList();
    }
}
