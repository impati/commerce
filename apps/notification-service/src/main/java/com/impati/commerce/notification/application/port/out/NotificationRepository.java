package com.impati.commerce.notification.application.port.out;

import com.impati.commerce.notification.domain.NotificationModels.Notification;

import java.time.Duration;
import java.util.List;

/**
 * 알림 저장소 포트. 구현은 {@code adapter/out/persistence}에 둔다.
 *
 * <p>조회로 얻은 객체를 변형한 것만으로 저장됐다고 가정하지 말 것. 변경했으면 {@link #save}를
 * 명시적으로 부른다.
 */
public interface NotificationRepository {
    void save(Notification notification);

    /**
     * 같은 멱등 키의 알림이 없을 때만 기록하고, 저장된 것을 돌려준다 (ADR-0011).
     *
     * <p>이미 있으면 새로 만들지 않고 <b>먼저 기록된 것</b>을 돌려준다. 그것이 멱등 수신의
     * 정의다 — 두 번째 요청은 첫 번째의 결과를 본다.
     *
     * <p>승자를 정하는 것은 조회가 아니라 유니크 제약이다. 조회로 먼저 확인하고 없으면 넣는
     * 방식은 동시에 들어온 두 요청이 둘 다 "없음"을 읽는 경합을 남긴다.
     *
     * <p><b>구현은 이 호출을 하나의 트랜잭션으로 묶지 않는다.</b> 제약 위반 뒤에 이어서 읽어야
     * 하는데, 실패한 문장이 트랜잭션을 abort 상태로 만드는 DB에서는 그 읽기가 함께 실패한다.
     * 중복을 처리하려고 만든 경로가 정확히 중복일 때 깨진다.
     */
    Notification saveIfAbsent(Notification notification);

    /** 기록된 순서를 유지한다. */
    List<Notification> findAll();

    /** 특정 회원의 알림만. 전체 목록은 다른 회원의 주문 내용을 노출한다. */
    List<Notification> findByMemberId(String memberId);

    /**
     * 지금 보낼 수 있는 메일 알림을 한 번에 점유하고 그 묶음을 돌려준다 (ADR-0011).
     *
     * <p>이름이 {@code find}가 아닌 이유는 <b>쓰는 조회</b>이기 때문이다. 다음 시도 시각을
     * {@code retryDelay} 뒤로 밀어 다른 인스턴스가 같은 항목을 집지 못하게 하고, 그 밀어둔
     * 시각이 실패했을 때의 재시도 간격이 된다 — 실패 경로에 쓰기가 없어야 하므로 성공을
     * 전제하지 않는다.
     *
     * <p>{@code dispatchId}는 이 주기가 집은 묶음을 가리킨다. 같은 값으로 두 번 부르지 않는다.
     *
     * <p>한 번에 집는 수를 제한하는 것은 밀린 건수가 한 주기의 길이를 정하지 않게 하려는
     * 것이다. 그 건수가 커지는 시점이 정확히 메일 시스템 장애 중이다. 임차는 이 묶음 전체를
     * 처리하는 동안 유지돼야 하므로 {@code retryDelay}는 배치 길이보다 넉넉해야 한다.
     */
    List<Notification> claimForDispatch(String dispatchId, int batchSize, Duration retryDelay);
}
