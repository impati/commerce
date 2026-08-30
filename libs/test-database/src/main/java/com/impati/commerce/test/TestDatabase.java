package com.impati.commerce.test;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트가 쓰는 MySQL. 컨테이너 하나를 모든 테스트가 공유하고 <b>스프링 컨텍스트마다 데이터베이스를
 * 따로 만든다</b> (ADR-0013).
 *
 * <p>격리 단위가 데이터베이스인 이유는 그것이 지금까지의 성질이기 때문이다. H2에서는 컨텍스트마다
 * 별개의 in-memory DB를 썼고, 그래서 테이블을 통째로 비우는 테스트가 다른 컨텍스트를 깨뜨리지
 * 않았다. DB를 옮기면서 그 성질을 부수면 원인 모를 실패가 나중에 생긴다.
 *
 * <p>컨테이너를 컨텍스트마다 띄우지 않는 이유는 그럴 필요가 없어서다. 격리는 데이터베이스가
 * 만들고 컨테이너는 그것을 담는 그릇일 뿐이다.
 *
 * <p><b>버전 하한이 8.0.16이다.</b> inventory가 재고 음수 방지에 {@code check} 제약을 쓰는데,
 * 그 아래 버전은 제약을 파싱만 하고 <b>강제하지 않는다</b>. 조용히 통과하는 검증은 없는 것보다
 * 나쁘므로 이미지를 고정한다.
 *
 * <p>연결 시간대를 UTC로 고정한다. 저장 타입이 {@code datetime(6)}이라 시간대를 담지 않으므로,
 * 세션 시간대가 다르면 {@code OffsetDateTime}을 쓰고 읽을 때 값이 옮겨진다. 점유가 시각 비교로
 * 배타성을 만들기 때문에 그 어긋남은 곧 배타성이 깨지는 것이다.
 */
public final class TestDatabase {
    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0.36");

    /** 컨텍스트마다 다른 데이터베이스를 주기 위한 일련번호. */
    private static final AtomicInteger NEXT = new AtomicInteger();

    private static final MySQLContainer<?> CONTAINER = start();

    /** 이번 실행이 만든 것. 끝나면 지운다. */
    private static final java.util.Set<String> CREATED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private TestDatabase() {
    }

    private static MySQLContainer<?> start() {
        var container = new MySQLContainer<>(IMAGE)
                .withUrlParam("connectionTimeZone", "UTC")
                .withUrlParam("forceConnectionTimeZoneToSession", "true")
                .withReuse(true);
        container.start();
        Runtime.getRuntime().addShutdownHook(new Thread(TestDatabase::dropCreated));
        return container;
    }

    /**
     * 새 데이터베이스를 만들고 그 JDBC URL을 돌려준다.
     *
     * <p>{@code name}은 어느 테스트의 것인지 보이게 하는 접두사다. 뒤에 일련번호가 붙으므로 같은
     * 이름을 여러 컨텍스트가 써도 섞이지 않는다.
     */
    public static String createDatabase(String name) {
        var database = sanitize(name) + "_" + NEXT.incrementAndGet();
        // 만드는 것과 권한을 주는 것 모두 root로 한다. 컨테이너의 일반 사용자는 기본
        // 데이터베이스에만 권한이 있어서 새로 만든 것에 접속하지 못한다.
        //
        // 먼저 지우는 이유는 컨테이너가 재사용될 수 있기 때문이다. 일련번호는 JVM마다 0에서
        // 시작하는데 컨테이너는 실행을 넘어 살아남으므로, 지우지 않으면 두 번째 실행이 이미
        // 있는 이름을 만들려다 실패하거나 <b>지난 실행의 데이터를 물려받는다</b>.
        run("drop database if exists `" + database + "`",
                "create database `" + database + "`",
                "grant all privileges on `" + database + "`.* to '" + CONTAINER.getUsername() + "'@'%'",
                "flush privileges");
        CREATED.add(database);
        return CONTAINER.getJdbcUrl().replaceFirst("/" + CONTAINER.getDatabaseName() + "\\?", "/" + database + "?");
    }

    /**
     * 이번 실행이 만든 데이터베이스를 지운다.
     *
     * <p>컨테이너를 재사용하면 실행마다 쌓인다. 지우는 것이 다음 실행의 정확성에 필요하지는
     * 않지만 — 만들 때 먼저 지우므로 — 쌓아둘 이유도 없다.
     */
    private static void dropCreated() {
        if (CREATED.isEmpty()) {
            return;
        }
        try {
            run(CREATED.stream().map(db -> "drop database if exists `" + db + "`").toArray(String[]::new));
        } catch (RuntimeException ignored) {
            // 정리 실패가 테스트 결과를 바꾸지 않는다. 다음 실행이 같은 이름을 다시 지운다.
        }
    }

    private static void run(String... statements) {
        try (var admin = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "root", CONTAINER.getPassword());
             var statement = admin.createStatement()) {
            for (var sql : statements) {
                statement.execute(sql);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("test database statement failed", failure);
        }
    }

    public static String username() {
        return CONTAINER.getUsername();
    }

    public static String password() {
        return CONTAINER.getPassword();
    }

    /** 데이터베이스 이름에 쓸 수 없는 문자를 걷어낸다. 테스트가 넘기는 이름은 자유 문자열이다. */
    private static String sanitize(String name) {
        var cleaned = name.replaceAll("[^A-Za-z0-9_]", "_");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
