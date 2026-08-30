package com.impati.commerce.notification.application.component;

import com.impati.commerce.common.Ids;
import com.impati.commerce.notification.application.port.in.MailDispatchUseCase;
import com.impati.commerce.notification.application.port.out.MailSender;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 아웃박스를 비운다. <b>메일 벤더를 아는 유일한 응용 컴포넌트다.</b>
 *
 * <p>받는 것과 나뉜 이유는 둘이 다른 실행 단위에서 돌기 때문이다 (ADR-0014). 겸하고 있었을
 * 때는 받는 쪽 컨텍스트도 {@code MailSender} 빈을 요구했고, 그래서 메일 어댑터를 워커로 옮길
 * 수 없었다 (ADR-0015).
 *
 * <p>발송이 쓰는 넷 — 벤더, 배치 크기, 재시도 간격, 최대 시도 — 을 받는 쪽은 하나도 쓰지
 * 않는다. 겹치는 것은 저장소뿐이다.
 */
@Component
public class MailDispatchExecutor implements MailDispatchUseCase {
    private static final Logger log = LoggerFactory.getLogger(MailDispatchExecutor.class);

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
     * 처리하기 전에 임차가 만료되면 아직 처리 중인 건을 다른 인스턴스가 집는다.
     */
    public MailDispatchExecutor(
            NotificationRepository notificationRepository,
            MailSender mailSender,
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
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 보낼 수 있는 것들을 한 번에 점유하고 건별로 처리한다. 점유가 한 문장이므로 후보 건수와
     * 무관하게 왕복이 하나다 (ADR-0011).
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
     *
     * <p>수락 여부를 <b>모르면 이 주기는 보내지 않는다.</b> 모르는 상태에서 보내는 쪽을 고르면
     * 조회 장애가 곧 중복 발송이 된다. 점유가 다음 시도 시각을 이미 밀어두었으므로 이 건은
     * 최소 간격 뒤에 다시 판단되고, 그동안 시도 횟수를 쓰지 않는다.
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
}
