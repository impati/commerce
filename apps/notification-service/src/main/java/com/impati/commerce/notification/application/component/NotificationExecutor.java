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

import java.util.List;

@Component
public class NotificationExecutor implements NotificationUseCase {
    private static final Logger log = LoggerFactory.getLogger(NotificationExecutor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int DISPATCH_BATCH = 20;

    private final NotificationRepository notifications;
    private final MailSender mailSender;
    private final String verificationBaseUrl;

    public NotificationExecutor(
            NotificationRepository notifications,
            MailSender mailSender,
            @Value("${notifications.verification-base-url}") String verificationBaseUrl
    ) {
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Transactional
    @Override
    public NotificationDetails record(String eventType, String memberId, String subject, String body) {
        var notification = new Notification(eventType, memberId, subject, body);
        notifications.save(notification);
        return NotificationMapper.toDetails(notification);
    }

    /**
     * 인증 메일을 아웃박스에 적는다. 발송은 하지 않는다.
     *
     * <p>여기서 바로 발송하면 메일 시스템 장애가 가입 실패가 된다. 기록만 커밋하고 발송은
     * {@link #dispatchPending()}이 별도로 가져간다.
     */
    @Transactional
    @Override
    public NotificationDetails requestEmailVerification(String memberId, String email, String token) {
        var notification = Notification.mail(
                "EmailVerificationRequested",
                memberId,
                email,
                "이메일 주소를 확인해주세요",
                "아래 링크로 이메일 소유를 확인해주세요.\n" + verificationBaseUrl + "?token=" + token
        );
        notifications.save(notification);
        return NotificationMapper.toDetails(notification);
    }

    /**
     * 아웃박스를 비운다.
     *
     * <p>한 건의 실패가 다음 건을 막지 않게 건별로 처리한다. 실패는 attempts와 last_error로
     * 남고, 한도를 넘으면 FAILED가 되어 조회로 드러난다. 예외를 삼켜 사라지게 하지 않는다.
     */
    @Override
    public int dispatchPending() {
        var pending = notifications.findPendingMail(DISPATCH_BATCH);
        var sent = 0;
        for (var notification : pending) {
            try {
                mailSender.send(notification.recipient(), notification.subject(), notification.body());
                notification.markSent();
                sent++;
            } catch (RuntimeException failure) {
                notification.markFailed(failure.getMessage(), MAX_ATTEMPTS);
                log.warn("mail delivery failed id={} attempts={}", notification.id(), notification.attempts());
            }
            notifications.save(notification);
        }
        return sent;
    }

    @Transactional(readOnly = true)
    @Override
    public List<NotificationDetails> listFor(String memberId) {
        return notifications.findByMemberId(memberId).stream().map(NotificationMapper::toDetails).toList();
    }

    /** 로컬 데모에서 발송함을 들여다본다. 인증 토큰을 확인할 유일한 경로다. */
    @Transactional(readOnly = true)
    @Override
    public List<OutboxEntry> outbox() {
        return notifications.findAll().stream().map(NotificationMapper::toOutboxEntry).toList();
    }
}
