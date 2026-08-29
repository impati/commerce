package com.impati.commerce.notification.application.component;

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
     */
    public NotificationExecutor(
            NotificationRepository notificationRepository,
            MailSender mailSender,
            @Value("${notifications.verification-base-url}") String verificationBaseUrl,
            @Value("${notifications.dispatch-batch-size:20}") int batchSize,
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
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.verificationBaseUrl = verificationBaseUrl;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
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
    public NotificationDetails requestEmailVerification(
            String memberId, String email, String token, String idempotencyKey) {
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
     * <p>후보를 훑고 건별로 점유해 보낸다. 점유를 배치 전체에 미리 걸지 않는 이유는, 점유가
     * 덮어야 하는 시간이 그 한 건의 작업 시간이면 되기 때문이다 (ADR-0009). 미리 걸면 배치가
     * 길어질 때 앞쪽 건의 점유가 처리 중에 만료돼 다른 인스턴스가 같은 메일을 또 보낸다.
     *
     * <p>한 건의 실패가 다음 건을 막지 않는다. 실패는 attempts와 last_error로 남고, 한도를
     * 넘으면 FAILED가 되어 조회로 드러난다. 예외를 삼켜 사라지게 하지 않는다.
     */
    @Override
    public int dispatchPending() {
        var candidates = notificationRepository.findDispatchCandidates(batchSize);
        var settled = 0;
        for (var notificationId : candidates) {
            var claimed = notificationRepository.claimForDispatch(notificationId, retryDelay);
            if (claimed.isEmpty()) {
                continue;
            }
            if (deliverOnce(claimed.get())) {
                settled++;
            }
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
