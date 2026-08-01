package com.shaopc.worthit.tracking.idempotency.infrastructure;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyExecutionCoordinator;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(IdempotencyExecutionCoordinatorIntegrationTest.FixedClock.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class IdempotencyExecutionCoordinatorIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final String REQUEST_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(
                    Instant.parse("2026-07-30T04:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private IdempotencyExecutionCoordinator coordinator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add(
                "spring.datasource.username", MYSQL::getUsername);
        registry.add(
                "spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearRecords() {
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-30T04:00:00Z"));
        jdbcTemplate.update(
                "DELETE FROM trk_idempotency_record");
    }

    @Test
    void activeSameHashReturnsStableInProgressError() {
        String key = UUID.randomUUID().toString();
        insertProcessing(
                key,
                REQUEST_HASH,
                now().plusSeconds(30));
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    return new TestResult("unexpected");
                }))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_IN_PROGRESS"));
        assertThat(executions).hasValue(0);
    }

    @Test
    void activeDifferentHashReturnsConflict() {
        String key = UUID.randomUUID().toString();
        insertProcessing(
                key,
                REQUEST_HASH,
                now().plusSeconds(30));

        assertThatThrownBy(() -> execute(
                key,
                differentHash(),
                () -> new TestResult("unexpected")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_CONFLICT"));
    }

    @Test
    void expiredSameHashIsReclaimedAndCompleted() {
        String key = UUID.randomUUID().toString();
        insertProcessing(
                key,
                REQUEST_HASH,
                now().minusSeconds(1));

        TestResult result = execute(
                key,
                REQUEST_HASH,
                () -> new TestResult("reclaimed"));

        assertThat(result.value()).isEqualTo("reclaimed");
        assertThat(status(key)).isEqualTo("SUCCEEDED");
        assertThat(processingExpireAt(key)).isNull();
    }

    @Test
    void expiredDifferentHashCannotTakeOverClaim() {
        String key = UUID.randomUUID().toString();
        insertProcessing(
                key,
                REQUEST_HASH,
                now().minusSeconds(1));

        assertThatThrownBy(() -> execute(
                key,
                differentHash(),
                () -> new TestResult("unexpected")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_CONFLICT"));
        assertThat(status(key)).isEqualTo("PROCESSING");
    }

    @Test
    void successfulResultIsReplayedWithoutReexecution() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        TestResult first = execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    return new TestResult("first");
                });
        TestResult replay = execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    return new TestResult("unexpected");
                });

        assertThat(first).isEqualTo(new TestResult("first"));
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
    }

    @Test
    void freshClaimWithSubMillisecondClockCompletes() {
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-30T04:00:00.123456789Z"));
        String key = UUID.randomUUID().toString();

        TestResult result = execute(
                key,
                REQUEST_HASH,
                () -> new TestResult("sub-millisecond"));

        assertThat(result.value()).isEqualTo("sub-millisecond");
        assertThat(status(key)).isEqualTo("SUCCEEDED");
        assertThat(processingExpireAt(key)).isNull();
    }

    @Test
    void terminalBusinessFailureIsPersistedAndReplayed() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        assertNotFound(key, executions);
        assertNotFound(key, executions);

        assertThat(executions).hasValue(1);
        assertThat(status(key)).isEqualTo("FAILED");
        assertThat(errorCode(key)).isEqualTo("RES_NOT_FOUND");
    }

    @Test
    void serverBusinessFailureKeepsProcessingForRetry() {
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> {
                    throw new BusinessException(
                            CommonWebErrorCode.SYS_ERROR);
                }))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("SYS_ERROR"));

        assertThat(status(key)).isEqualTo("PROCESSING");
        assertThat(errorCode(key)).isNull();
    }

    @Test
    void technicalFailureRetriesOnlyAfterLeaseExpiry() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    throw new IllegalStateException("temporary");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary");
        assertThat(status(key)).isEqualTo("PROCESSING");

        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> new TestResult("too-early")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_IN_PROGRESS"));

        CURRENT_INSTANT.set(
                Instant.parse("2026-07-30T04:01:01Z"));
        TestResult retried = execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    return new TestResult("retried");
                });

        assertThat(retried.value()).isEqualTo("retried");
        assertThat(executions).hasValue(2);
        assertThat(status(key)).isEqualTo("SUCCEEDED");
    }

    @Test
    void concurrentRequestsGrantOnlyOneActiveLease()
            throws Exception {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);

        CompletableFuture<TestResult> first =
                CompletableFuture.supplyAsync(() -> execute(
                        key,
                        REQUEST_HASH,
                        () -> {
                            executions.incrementAndGet();
                            actionStarted.countDown();
                            await(allowCompletion);
                            return new TestResult("first");
                        }));

        assertThat(actionStarted.await(10, TimeUnit.SECONDS))
                .isTrue();
        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    return new TestResult("second");
                }))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_IN_PROGRESS"));

        allowCompletion.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS))
                .isEqualTo(new TestResult("first"));
        assertThat(executions).hasValue(1);
    }

    private TestResult execute(
            String key,
            String requestHash,
            IdempotentTestAction action) {
        return coordinator.execute(
                USER_ID,
                TrackingOperation.ITEM_RETURN,
                key,
                requestHash,
                TestResult.class,
                action::execute);
    }

    private void assertNotFound(
            String key, AtomicInteger executions) {
        assertThatThrownBy(() -> execute(
                key,
                REQUEST_HASH,
                () -> {
                    executions.incrementAndGet();
                    throw new BusinessException(
                            CommonWebErrorCode.RES_NOT_FOUND);
                }))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RES_NOT_FOUND"));
    }

    private void insertProcessing(
            String key,
            String requestHash,
            LocalDateTime processingExpireAt) {
        LocalDateTime now = now();
        jdbcTemplate.update(
                """
                INSERT INTO trk_idempotency_record (
                    id, user_id, operation_code,
                    idempotency_key, request_hash,
                    status, processing_expire_at,
                    expires_at, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Math.abs(UUID.randomUUID().getMostSignificantBits()),
                USER_ID,
                TrackingOperation.ITEM_RETURN.code(),
                key,
                requestHash,
                "PROCESSING",
                processingExpireAt,
                now.plusDays(1),
                now,
                now);
    }

    private String status(String key) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM trk_idempotency_record
                WHERE user_id = ?
                  AND operation_code = ?
                  AND idempotency_key = ?
                """,
                String.class,
                USER_ID,
                TrackingOperation.ITEM_RETURN.code(),
                key);
    }

    private LocalDateTime processingExpireAt(String key) {
        return jdbcTemplate.queryForObject(
                """
                SELECT processing_expire_at
                FROM trk_idempotency_record
                WHERE user_id = ?
                  AND operation_code = ?
                  AND idempotency_key = ?
                """,
                LocalDateTime.class,
                USER_ID,
                TrackingOperation.ITEM_RETURN.code(),
                key);
    }

    private String errorCode(String key) {
        return jdbcTemplate.queryForObject(
                """
                SELECT error_code
                FROM trk_idempotency_record
                WHERE user_id = ?
                  AND operation_code = ?
                  AND idempotency_key = ?
                """,
                String.class,
                USER_ID,
                TrackingOperation.ITEM_RETURN.code(),
                key);
    }

    private static String differentHash() {
        return "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(
                CURRENT_INSTANT.get(),
                ZoneId.of("Asia/Shanghai"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发测试超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "并发测试被中断", exception);
        }
    }

    private record TestResult(String value) {
    }

    @FunctionalInterface
    private interface IdempotentTestAction {
        TestResult execute();
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedTrackingClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneId.of("Asia/Shanghai");
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return CURRENT_INSTANT.get();
                }
            };
        }
    }
}
