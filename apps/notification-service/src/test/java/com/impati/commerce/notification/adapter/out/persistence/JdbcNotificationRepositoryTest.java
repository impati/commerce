package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.port.out.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import com.impati.commerce.test.RequiresDatabase;
import com.impati.commerce.test.TestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "notifications.dispatch-interval=3600000"
})
@RequiresDatabase
class JdbcNotificationRepositoryTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry, "notification-repo");
    }
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** 알림 목록은 기록 순서를 유지해야 한다. id가 랜덤이므로 정렬 컬럼이 없으면 순서가 깨진다. */
    @Test
    void keepsInsertionOrder() {
        var first = Notification.recorded("OrderPaid", "mem_order", "first", "body 1", "evt_order_1");
        var second = Notification.recorded("ShipmentCreated", "mem_order", "second", "body 2", "evt_order_2");
        var third = Notification.recorded("OrderDelivered", "mem_order", "third", "body 3", "evt_order_3");

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
        assertThat(row.get("idempotency_key")).isEqualTo("vmail_column");
        assertThat(row.get("event_type")).isEqualTo("EmailVerificationRequested");
        assertThat(row.get("member_id")).isEqualTo("mem_column");
        assertThat(row.get("subject")).isEqualTo("subject text");
        assertThat(row.get("body")).isEqualTo("body text");
        assertThat(row.get("recipient")).isEqualTo("column@impati.dev");
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
     * 기록만 남기는 알림도 키로 걸러진다.
     *
     * <p>예전에는 이 경로에 키가 없어 유니크 제약이 NULL을 서로 다르게 보는 성질에 기대고
     * 있었다. 두 경로가 모두 키를 요구하게 되면서 그 예외가 사라졌다 (ADR-0012).
     */
    @Test
    void keepsOneRowPerIdempotencyKeyForRecordedNotifications() {
        var first = notificationRepository.saveIfAbsent(
                Notification.recorded("OrderPaid", "mem_dedup", "one", "body", "evt_dedup"));
        var again = notificationRepository.saveIfAbsent(
                Notification.recorded("OrderPaid", "mem_dedup", "two", "body", "evt_dedup"));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.subject()).isEqualTo("one");
        assertThat(notificationRepository.findByMemberId("mem_dedup")).hasSize(1);
    }
}
