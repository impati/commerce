package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-repo;DB_CLOSE_DELAY=-1",
        "notifications.dispatch-interval=3600000"
})
class JdbcNotificationRepositoryTest {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** 알림 목록은 기록 순서를 유지해야 한다. id가 랜덤이므로 정렬 컬럼이 없으면 순서가 깨진다. */
    @Test
    void keepsInsertionOrder() {
        var first = new Notification("OrderPaid", "mem_order", "first", "body 1");
        var second = new Notification("ShipmentCreated", "mem_order", "second", "body 2");
        var third = new Notification("OrderDelivered", "mem_order", "third", "body 3");

        notificationRepository.save(first);
        notificationRepository.save(second);
        notificationRepository.save(third);

        var subjects = notificationRepository.findAll().stream()
                .filter(notification -> notification.memberId().equals("mem_order"))
                .map(Notification::subject)
                .toList();
        assertThat(subjects).containsExactly("first", "second", "third");
    }

    @Test
    void writesEachFieldToItsOwnColumn() {
        var notification = Notification.mail(
                "EmailVerificationRequested", "mem_column", "column@impati.dev",
                "subject text", "body text", "vmail_column");

        notificationRepository.save(notification);

        var row = jdbc.queryForMap("""
                        select idempotency_key, event_type, member_id, subject, body, recipient
                          from notifications
                         where id = ?
                        """,
                notification.id()
        );
        assertThat(row.get("IDEMPOTENCY_KEY")).isEqualTo("vmail_column");
        assertThat(row.get("EVENT_TYPE")).isEqualTo("EmailVerificationRequested");
        assertThat(row.get("MEMBER_ID")).isEqualTo("mem_column");
        assertThat(row.get("SUBJECT")).isEqualTo("subject text");
        assertThat(row.get("BODY")).isEqualTo("body text");
        assertThat(row.get("RECIPIENT")).isEqualTo("column@impati.dev");
    }

    /**
     * 저장된 멱등 키를 그대로 읽어온다.
     *
     * <p>컬럼을 안 읽고 객체가 값을 지어내면 왕복 테스트는 통과한다. 그래서 컬럼을 직접 넣고
     * 포트로 읽는 방향으로 확인한다.
     */
    @Test
    void readsIdempotencyKeyBackFromTheColumn() {
        var notification = Notification.mail(
                "EmailVerificationRequested", "mem_readback", "readback@impati.dev",
                "subject", "body", "vmail_readback");

        notificationRepository.save(notification);

        var loaded = notificationRepository.findByMemberId("mem_readback").stream()
                .findFirst()
                .orElseThrow();
        assertThat(loaded.idempotencyKey()).isEqualTo("vmail_readback");
    }

    /** 같은 키로 다시 기록하면 행을 만들지 않고 먼저 기록된 것을 돌려준다 (ADR-0011). */
    @Test
    void saveIfAbsentReturnsTheFirstRecordForTheSameKey() {
        var first = Notification.mail(
                "EmailVerificationRequested", "mem_dup", "dup@impati.dev",
                "subject", "body", "vmail_dup");
        var second = Notification.mail(
                "EmailVerificationRequested", "mem_dup", "dup@impati.dev",
                "subject", "body", "vmail_dup");

        var stored = notificationRepository.saveIfAbsent(first);
        var again = notificationRepository.saveIfAbsent(second);

        assertThat(stored.id()).isEqualTo(first.id());
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where idempotency_key = ?", Integer.class, "vmail_dup"))
                .isEqualTo(1);
    }

    /**
     * 키가 없는 알림은 여럿이어도 제약에 걸리지 않는다.
     *
     * <p>기록만 남기는 알림에는 아직 키가 없다. 유니크 제약이 NULL을 같게 본다면 두 번째
     * 주문 알림부터 기록이 실패한다.
     */
    @Test
    void allowsManyNotificationsWithoutAnIdempotencyKey() {
        notificationRepository.save(new Notification("OrderPaid", "mem_nokey", "one", "body"));
        notificationRepository.save(new Notification("OrderPaid", "mem_nokey", "two", "body"));

        assertThat(notificationRepository.findByMemberId("mem_nokey")).hasSize(2);
    }
}
