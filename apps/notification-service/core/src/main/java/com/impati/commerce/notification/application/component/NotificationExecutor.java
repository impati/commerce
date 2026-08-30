package com.impati.commerce.notification.application.component;

import com.impati.commerce.notification.application.port.in.NotificationDetails;
import com.impati.commerce.notification.application.port.in.NotificationUseCase;
import com.impati.commerce.notification.application.port.in.OutboxEntry;
import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림을 받아 적고 조회한다. <b>보내지 않는다.</b>
 *
 * <p>발송은 {@link MailDispatchExecutor}가 갖는다. 나뉜 이유는 둘이 다른 실행 단위에서 돌기
 * 때문이고(ADR-0014), 나뉘어야 메일 벤더를 아는 것이 보내는 쪽 하나로 좁혀지기 때문이다 —
 * 한 클래스가 겸하면 받는 쪽 컨텍스트도 {@code MailSender} 빈을 요구한다 (ADR-0015).
 */
@Component
public class NotificationExecutor implements NotificationUseCase {
    private final NotificationRepository notificationRepository;
    private final String verificationBaseUrl;

    public NotificationExecutor(
            NotificationRepository notificationRepository,
            @Value("${notifications.verification-base-url}") String verificationBaseUrl
    ) {
        this.notificationRepository = notificationRepository;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    /**
     * 사건을 기록한다. 발송하지 않는다 (PD-0009-R1).
     *
     * <p>같은 멱등 키로 다시 오면 먼저 적힌 것을 그대로 돌려준다. 부르는 쪽은 응답을 못 받았을
     * 때 재시도할 수밖에 없으므로, 그 재시도가 두 줄이 되지 않게 하는 것이 여기의 일이다
     * (ADR-0012).
     *
     * <p>{@code @Transactional}을 붙이지 않는다. 저장소 호출이 하나뿐이라 묶을 것이 없고,
     * {@link NotificationRepository#saveIfAbsent}는 제약 위반 뒤에 이어서 읽어야 하므로
     * 트랜잭션 안에서 돌면 안 된다.
     */
    @Override
    public NotificationDetails record(
            String eventType, String memberId, String subject, String body, String idempotencyKey) {
        var notification = Notification.recorded(eventType, memberId, subject, body, idempotencyKey);
        return NotificationMapper.toDetails(notificationRepository.saveIfAbsent(notification));
    }

    /**
     * 인증 메일을 아웃박스에 적는다. 발송은 하지 않는다.
     *
     * <p>여기서 바로 발송하면 메일 시스템 장애가 가입 실패가 된다. 기록만 커밋하고 발송은
     * {@link MailDispatchExecutor}가 별도로 가져간다.
     *
     * <p>같은 멱등 키로 다시 오면 먼저 적힌 것을 그대로 돌려준다 (ADR-0011).
     *
     * <p>{@code @Transactional}을 붙이지 않는다. {@link NotificationRepository#saveIfAbsent}는
     * 제약 위반 뒤에 이어서 읽어야 하므로 트랜잭션 안에서 돌면 안 된다.
     *
     * <p>토큰은 쿼리가 아니라 프래그먼트에 담는다. 프래그먼트는 브라우저가 서버로 보내지 않으므로
     * 정적 호스트와 중간 프록시의 접근 로그에 남지 않는다. 근거는 ADR-0006.
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

    @Transactional(readOnly = true)
    @Override
    public List<NotificationDetails> listFor(String memberId) {
        return notificationRepository.findByMemberId(memberId).stream().map(NotificationMapper::toDetails).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<OutboxEntry> outbox() {
        return notificationRepository.findAll().stream().map(NotificationMapper::toOutboxEntry).toList();
    }
}
