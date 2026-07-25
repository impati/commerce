package com.impati.commerce.notification.adapter.out.persistence;

import com.impati.commerce.notification.application.NotificationRepository;
import com.impati.commerce.notification.domain.NotificationModels.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-repo;DB_CLOSE_DELAY=-1")
class JdbcNotificationRepositoryTest {
    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private JdbcTemplate jdbc;

    /** 알림 목록은 기록 순서를 유지해야 한다. id가 랜덤이므로 정렬 컬럼이 없으면 순서가 깨진다. */
    @Test
    void keepsInsertionOrder() {
        var first = new Notification("OrderPaid", "mem_order", "first", "body 1");
        var second = new Notification("ShipmentCreated", "mem_order", "second", "body 2");
        var third = new Notification("OrderDelivered", "mem_order", "third", "body 3");

        notifications.save(first);
        notifications.save(second);
        notifications.save(third);

        var subjects = notifications.findAll().stream()
                .filter(notification -> notification.memberId().equals("mem_order"))
                .map(Notification::subject)
                .toList();
        assertThat(subjects).containsExactly("first", "second", "third");
    }

    @Test
    void writesEachFieldToItsOwnColumn() {
        var notification = new Notification("OrderCancelled", "mem_column", "subject text", "body text");

        notifications.save(notification);

        var row = jdbc.queryForMap(
                "select event_type, member_id, subject, body from notifications where id = ?",
                notification.id()
        );
        assertThat(row.get("EVENT_TYPE")).isEqualTo("OrderCancelled");
        assertThat(row.get("MEMBER_ID")).isEqualTo("mem_column");
        assertThat(row.get("SUBJECT")).isEqualTo("subject text");
        assertThat(row.get("BODY")).isEqualTo("body text");
    }
}
