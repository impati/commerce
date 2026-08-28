package com.impati.commerce.member.application.port.out;

import com.impati.commerce.member.domain.MemberModels.VerificationMail;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 인증 메일 아웃박스 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>이 포트가 아웃박스의 전부다. 발송 자체는 {@link NotificationClient}가 하고, 여기는
 * 무엇을 보내야 하는지와 어디까지 시도했는지만 안다.
 */
public interface VerificationMailRepository {
    void save(VerificationMail mail);

    /**
     * 지금 보낼 수 있는 항목의 식별자. 오래된 것부터 준다.
     *
     * <p>점유하지 않는다. 여기서 얻은 식별자를 {@link #claimForDispatch}로 하나씩 집어야
     * 실제로 보낼 수 있다.
     *
     * <p>한 번에 가져오는 수를 제한하는 것은 밀린 건수가 한 주기의 길이를 정하지 않게 하려는
     * 것이다. 그 건수가 커지는 시점이 정확히 알림 서비스 장애 중이다.
     */
    List<String> findDispatchCandidates(int batchSize);

    /**
     * 한 건을 보내려고 점유한다 (ADR-0010).
     *
     * <p>이름이 {@code find}가 아닌 이유는 <b>쓰는 조회</b>이기 때문이다. 다음 시도 시각을
     * {@code retryDelay} 뒤로 밀어 다른 인스턴스가 같은 항목을 집지 못하게 하고, 그 밀어둔
     * 시각이 실패했을 때의 재시도 간격이 된다 — 실패 경로에 쓰기가 없어야 하므로 성공을
     * 전제하지 않는다.
     *
     * <p>점유에 실패하면 비어 있다. 다른 인스턴스가 이미 집었거나, 이미 종단 상태가 됐거나,
     * 아직 시도 시각이 아닌 경우다. 셋 모두 지금 할 일이 없다는 뜻이므로 구분하지 않는다.
     */
    Optional<VerificationMail> claimForDispatch(String mailId, Duration retryDelay);
}
