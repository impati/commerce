package com.impati.commerce.test;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 격리를 만드는 성질을 고정한다 (ADR-0013).
 *
 * <p>테스트가 서로를 깨뜨리지 않는 근거가 여기다. 같은 이름을 요청해도 다른 데이터베이스를
 * 받아야 하고, 받은 것은 비어 있어야 한다.
 */
@RequiresDatabase
class TestDatabaseTest {
    @Test
    void sameNameStillGetsItsOwnDatabase() {
        var first = TestDatabase.createDatabase("same-name");
        var second = TestDatabase.createDatabase("same-name");

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * 받은 데이터베이스는 비어 있다.
     *
     * <p>컨테이너를 재사용하면 지난 실행이 만든 같은 이름이 남아 있을 수 있다. 만들기 전에
     * 지우지 않으면 그것을 물려받아 테스트가 남의 데이터를 보게 된다.
     */
    @Test
    void handedDatabaseIsEmptyEvenIfTheNameWasUsedBefore() throws Exception {
        var url = TestDatabase.createDatabase("leftover");
        try (var connection = DriverManager.getConnection(url, TestDatabase.username(), TestDatabase.password());
             var statement = connection.createStatement()) {
            statement.execute("create table leftover_marker (id int primary key)");
            statement.execute("insert into leftover_marker (id) values (1)");
        }

        // 같은 이름을 다시 요청하면 새 데이터베이스이므로 앞의 흔적이 없다.
        var again = TestDatabase.createDatabase("leftover");
        try (var connection = DriverManager.getConnection(again, TestDatabase.username(), TestDatabase.password());
             var statement = connection.createStatement();
             var tables = statement.executeQuery("show tables")) {
            assertThat(tables.next()).as("새로 받은 데이터베이스에 테이블이 있으면 안 된다").isFalse();
        }
    }
}
