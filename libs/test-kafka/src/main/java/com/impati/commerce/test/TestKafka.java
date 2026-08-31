package com.impati.commerce.test;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트가 쓰는 카프카. 브로커 하나를 모든 테스트가 공유하고 <b>스프링 컨텍스트마다 토픽과
 * 컨슈머 그룹을 따로 만든다</b> (ADR-0016).
 *
 * <p>격리 단위가 토픽인 이유는 {@code libs:test-database}가 데이터베이스를 나누는 것과 같다.
 * 나누지 않으면 한 테스트가 발행한 사건을 다른 테스트의 컨슈머가 집어가고, 그 실패는 어디서
 * 왔는지 알 수 없는 종류다.
 *
 * <p><b>복제본이 하나다.</b> 운영은 {@code replication.factor=3} · {@code min.insync.replicas=2}로
 * 유실을 막지만 테스트 브로커는 단일 노드라 그 값을 쓸 수 없다. 여기서 검증되는 것은 순서와
 * 오프셋 동작이고, 복제 설정이 실제로 유실을 막는지는 검증 범위 밖이다.
 */
public final class TestKafka {
    private static final DockerImageName IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.0");

    /** 순서 검증에 파티션이 여럿이어야 한다. 하나면 키와 무관하게 전부 한 파티션에 떨어진다. */
    private static final int PARTITIONS = 3;

    /** 컨텍스트마다 다른 토픽을 주기 위한 일련번호. */
    private static final AtomicInteger NEXT = new AtomicInteger();

    /** 지운 토픽이 실제로 사라지기를 기다리는 상한. */
    private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);

    private static final KafkaContainer CONTAINER = start();

    /** 이번 실행이 만든 것. 끝나면 지운다. */
    private static final Set<String> CREATED = ConcurrentHashMap.newKeySet();

    private TestKafka() {
    }

    private static KafkaContainer start() {
        var container = new KafkaContainer(IMAGE).withReuse(true);
        container.start();
        Runtime.getRuntime().addShutdownHook(new Thread(TestKafka::deleteCreated));
        return container;
    }

    public static String bootstrapServers() {
        return CONTAINER.getBootstrapServers();
    }

    /**
     * 새 토픽을 만들고 그 이름을 돌려준다.
     *
     * <p>{@code name}은 어느 테스트의 것인지 보이게 하는 접두사다. 뒤에 일련번호가 붙으므로 같은
     * 이름을 여러 컨텍스트가 써도 섞이지 않는다.
     *
     * <p>미리 만드는 이유는 자동 생성에 기대면 파티션 수가 브로커 기본값이 되기 때문이다. 파티션이
     * 하나면 키가 달라도 한 곳에 떨어져 <b>순서 테스트가 항상 통과한다.</b>
     */
    public static String createTopic(String name) {
        var topic = sanitize(name) + "-" + NEXT.incrementAndGet();
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            // 먼저 지우는 이유는 컨테이너가 재사용될 수 있기 때문이다. 일련번호는 JVM마다 0에서
            // 시작하는데 컨테이너는 실행을 넘어 살아남으므로, 지우지 않으면 두 번째 실행이 이미
            // 있는 이름을 만들려다 실패하거나 지난 실행의 사건을 물려받는다.
            //
            // Makefile이 재사용을 켜라고 안내하므로 그 안내를 따른 사람이 먼저 겪는다.
            deleteTopics(admin, List.of(topic));
            awaitAbsent(admin, List.of(topic));
            admin.createTopics(List.of(new NewTopic(topic, PARTITIONS, (short) 1))).all().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test topic creation interrupted: " + topic, interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("test topic creation failed: " + topic, failure);
        }
        CREATED.add(topic);
        return topic;
    }

    /**
     * 이번 실행이 만든 토픽을 지운다.
     *
     * <p>컨테이너를 재사용하면 실행마다 쌓인다. 지우는 것이 다음 실행의 정확성에 필요하지는
     * 않지만 — 만들 때 먼저 지우므로 — 쌓아둘 이유도 없다.
     */
    private static void deleteCreated() {
        if (CREATED.isEmpty()) {
            return;
        }
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            deleteTopics(admin, List.copyOf(CREATED));
        } catch (Exception ignored) {
            // 정리 실패가 테스트 결과를 바꾸지 않는다. 다음 실행이 같은 이름을 다시 지운다.
        }
    }

    /** 없는 토픽을 지우는 것은 오류가 아니다. 있으면 지우고 없으면 넘어간다. */
    private static void deleteTopics(Admin admin, List<String> topics) {
        try {
            admin.deleteTopics(topics).all().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test topic deletion interrupted", interrupted);
        } catch (Exception missing) {
            // UnknownTopicOrPartitionException이 대부분이다. 지울 것이 없었다는 뜻이다.
        }
    }

    /**
     * 토픽이 실제로 사라질 때까지 기다린다.
     *
     * <p><b>삭제는 수락으로 끝나지 않는다.</b> {@code deleteTopics().get()}이 돌아와도 브로커는
     * 아직 지우는 중일 수 있고, 그 상태에서 같은 이름을 만들려 하면
     * {@code "Topic is marked for deletion"}으로 거절당한다. {@code libs:test-database}에 이
     * 단계가 없는 것은 MySQL의 {@code drop database}가 동기이기 때문이다.
     */
    private static void awaitAbsent(Admin admin, List<String> topics) {
        var deadline = System.nanoTime() + DELETE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                var existing = admin.listTopics().names().get();
                if (topics.stream().noneMatch(existing::contains)) {
                    return;
                }
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("waiting for test topic deletion interrupted", interrupted);
            } catch (Exception unavailable) {
                // 브로커가 잠시 답하지 않는 것은 기다릴 이유이지 끝낼 이유가 아니다.
            }
        }
        throw new IllegalStateException("test topics were not deleted in time: " + topics);
    }

    /** 토픽 이름에 쓸 수 없는 문자를 걷어낸다. 테스트가 넘기는 이름은 자유 문자열이다. */
    private static String sanitize(String name) {
        var cleaned = name.replaceAll("[^A-Za-z0-9._-]", "-");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
