package com.shaopc.worthit.reminder.app.reconcile.infrastructure;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.reminder.app.WorthItReminderApplication;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderReconcileService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(ReminderReconcileIntegrationTest.FixedClock.class)
@SpringBootTest(
        classes = WorthItReminderApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class ReminderReconcileIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long BUSINESS_ID = 2001L;
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(
                    Instant.parse("2026-07-28T02:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReminderReconcileService reconcileService;

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
    void clearReminderData() {
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T02:00:00Z"));
        jdbcTemplate.update("DELETE FROM rem_command_log");
        jdbcTemplate.update("DELETE FROM rem_instance");
        jdbcTemplate.update("DELETE FROM rem_binding");
        jdbcTemplate.update("DELETE FROM rem_user_write_fence");
    }

    @Test
    void lateReconcileCannotRecreateDataAfterAccountCancellation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 0);
        jdbcTemplate.update(
                """
                INSERT INTO rem_user_write_fence (
                    user_id, status, cancellation_id, completed_at,
                    create_time, update_time
                ) VALUES (?, 'CANCELLED', '9001', ?, ?, ?)
                """,
                USER_ID,
                now,
                now,
                now);

        assertThatThrownBy(() -> reconcileService.reconcile(
                "event-after-cancellation",
                command(
                        1,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(2026, 8, 3, 0, 0),
                        true,
                        ReminderOperationType.INITIAL_SYNC)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
        assertThat(count("rem_binding")).isZero();
        assertThat(count("rem_instance")).isZero();
        assertThat(count("rem_command_log")).isZero();
    }

    @Test
    void initialSyncCreatesOneBindingPendingAndIdempotentHistory() {
        ReconcileReminderCommand command = command(
                1,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                ReminderOperationType.INITIAL_SYNC);

        ReconcileReminderResponse first =
                reconcileService.reconcile("event-001", command);
        ReconcileReminderResponse replay =
                reconcileService.reconcile("event-001", command);

        assertThat(first.applied()).isTrue();
        assertThat(first.resultCode())
                .isEqualTo(ReconcileResultCode.APPLIED);
        assertThat(first.idempotent()).isFalse();
        assertThat(replay.resultCode())
                .isEqualTo(ReconcileResultCode.APPLIED);
        assertThat(replay.idempotent()).isTrue();
        assertThat(replay.bindingId()).isEqualTo(first.bindingId());
        assertThat(count("rem_binding")).isEqualTo(1);
        assertThat(count("rem_instance")).isEqualTo(1);
        assertThat(count("rem_command_log")).isEqualTo(1);
        assertThat(singleString(
                "SELECT status FROM rem_instance"))
                .isEqualTo("PENDING");
    }

    @Test
    void replaysOldEventBeforeVersionComparisonAfterNewerApplied() {
        ReconcileReminderCommand versionFive = command(
                5,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                ReminderOperationType.INITIAL_SYNC);
        ReconcileReminderCommand versionSix = command(
                6,
                LocalDate.of(2026, 8, 12),
                LocalDateTime.of(2026, 8, 5, 0, 0),
                true,
                ReminderOperationType.UPDATE_BUSINESS_DATE);
        reconcileService.reconcile("event-A", versionFive);
        reconcileService.reconcile("event-B", versionSix);

        ReconcileReminderResponse replay =
                reconcileService.reconcile("event-A", versionFive);

        assertThat(replay.resultCode())
                .isEqualTo(ReconcileResultCode.APPLIED);
        assertThat(replay.idempotent()).isTrue();
        assertThat(replay.lastSourceVersion()).isEqualTo(6);
        assertThat(count("rem_command_log")).isEqualTo(2);
        assertThat(singleLong(
                "SELECT last_source_version FROM rem_binding"))
                .isEqualTo(6);
    }

    @Test
    void sameVersionAndDigestWithNewEventReplaysAuthority() {
        ReconcileReminderCommand command = command(
                5,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                ReminderOperationType.INITIAL_SYNC);
        reconcileService.reconcile("event-A", command);

        ReconcileReminderResponse replay =
                reconcileService.reconcile("event-C", command);

        assertThat(replay.resultCode())
                .isEqualTo(ReconcileResultCode.APPLIED);
        assertThat(replay.idempotent()).isTrue();
        assertThat(count("rem_command_log")).isEqualTo(1);
        assertThat(count("rem_instance")).isEqualTo(1);
    }

    @Test
    void sameVersionDifferentDigestRecordsConflictWithoutMutation() {
        reconcileService.reconcile(
                "event-A",
                command(
                        5,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(2026, 8, 3, 0, 0),
                        true,
                        ReminderOperationType.INITIAL_SYNC));

        assertThatThrownBy(() -> reconcileService.reconcile(
                "event-D",
                command(
                        5,
                        LocalDate.of(2026, 8, 11),
                        LocalDateTime.of(2026, 8, 4, 0, 0),
                        true,
                        ReminderOperationType.UPDATE_BUSINESS_DATE)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "BIZ_CONTRACT_CONFLICT"));

        assertThat(count("rem_command_log")).isEqualTo(1);
        assertThat(count("rem_instance")).isEqualTo(1);
        assertThat(singleLong(
                "SELECT conflict_count FROM rem_command_log"))
                .isEqualTo(1);
        assertThat(singleString(
                "SELECT result_code FROM rem_command_log"))
                .isEqualTo("APPLIED");
    }

    @Test
    void unseenOldVersionIsRecordedAsIgnoredOld() {
        reconcileService.reconcile(
                "event-v3",
                command(
                        3,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(2026, 8, 3, 0, 0),
                        true,
                        ReminderOperationType.INITIAL_SYNC));

        ReconcileReminderResponse response =
                reconcileService.reconcile(
                        "event-v2",
                        command(
                                2,
                                LocalDate.of(2026, 8, 9),
                                LocalDateTime.of(
                                        2026, 8, 2, 0, 0),
                                true,
                                ReminderOperationType
                                        .UPDATE_BUSINESS_DATE));

        assertThat(response.applied()).isFalse();
        assertThat(response.resultCode())
                .isEqualTo(ReconcileResultCode.IGNORED_OLD);
        assertThat(response.idempotent()).isFalse();
        assertThat(response.lastSourceVersion()).isEqualTo(3);
        assertThat(count("rem_command_log")).isEqualTo(2);
        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_command_log "
                        + "WHERE result_code='IGNORED_OLD'"))
                .isEqualTo(1);
        assertThat(count("rem_instance")).isEqualTo(1);
    }

    @Test
    void dueDateChangeProcessesOldAndCreatesOneNewPending() {
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T00:00:00Z"));
        reconcileService.reconcile(
                "event-v1",
                command(
                        1,
                        LocalDate.of(2026, 7, 28),
                        LocalDateTime.of(
                                2026, 7, 28, 9, 0),
                        true,
                        ReminderOperationType.INITIAL_SYNC));
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T02:00:01Z"));

        reconcileService.reconcile(
                "event-v2",
                command(
                        2,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(
                                2026, 8, 3, 0, 0),
                        true,
                        ReminderOperationType
                                .UPDATE_BUSINESS_DATE));

        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_instance "
                        + "WHERE status='PROCESSED'"))
                .isEqualTo(1);
        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_instance "
                        + "WHERE status='PENDING'"))
                .isEqualTo(1);
        assertThat(singleString(
                "SELECT resolution_reason FROM rem_instance "
                        + "WHERE status='PROCESSED'"))
                .isEqualTo("BUSINESS_DATE_CHANGED");
    }

    @Test
    void disablingReminderCancelsFuturePendingWithoutReplacement() {
        reconcileService.reconcile(
                "event-v1",
                command(
                        1,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(
                                2026, 8, 3, 0, 0),
                        true,
                        ReminderOperationType.INITIAL_SYNC));

        reconcileService.reconcile(
                "event-v2",
                command(
                        2,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(
                                2026, 8, 3, 0, 0),
                        false,
                        ReminderOperationType
                                .DISABLE_REMINDER));

        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_instance "
                        + "WHERE status='PENDING'"))
                .isZero();
        assertThat(singleString(
                "SELECT status FROM rem_instance"))
                .isEqualTo("CANCELED");
        assertThat(singleString(
                "SELECT resolution_reason FROM rem_instance"))
                .isEqualTo("REMINDER_DISABLED");
    }

    @Test
    void terminalBusinessStatusNeverCreatesPending() {
        ReconcileReminderCommand command =
                new ReconcileReminderCommand(
                        USER_ID,
                        ReminderBusinessType.ITEM,
                        BUSINESS_ID,
                        ReminderType.WARRANTY,
                        1,
                        LocalDate.of(2026, 8, 10),
                        LocalDateTime.of(
                                2026, 8, 3, 0, 0),
                        true,
                        "SOLD",
                        ReminderOperationType.INITIAL_SYNC,
                        ReminderClientContract.SCHEMA_VERSION);

        ReconcileReminderResponse response =
                reconcileService.reconcile(
                        "event-terminal", command);

        assertThat(response.resultCode())
                .isEqualTo(ReconcileResultCode.APPLIED);
        assertThat(count("rem_binding")).isEqualTo(1);
        assertThat(count("rem_instance")).isZero();
        assertThat(singleLong(
                "SELECT last_source_version FROM rem_binding"))
                .isEqualTo(1);
    }

    @Test
    void concurrentFirstSyncCreatesOneBindingAndAtMostOnePending()
            throws Exception {
        ReconcileReminderCommand command = command(
                1,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                ReminderOperationType.INITIAL_SYNC);

        runConcurrently(
                () -> reconcileService.reconcile(
                        "event-concurrent-A", command),
                () -> reconcileService.reconcile(
                        "event-concurrent-B", command));

        assertThat(count("rem_binding")).isEqualTo(1);
        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_instance "
                        + "WHERE status='PENDING'"))
                .isEqualTo(1);
        assertThat(count("rem_command_log")).isEqualTo(1);
    }

    @Test
    void concurrentOutOfOrderVersionsConvergeToHighestVersion()
            throws Exception {
        ReconcileReminderCommand versionTwo = command(
                2,
                LocalDate.of(2026, 8, 9),
                LocalDateTime.of(2026, 8, 2, 0, 0),
                true,
                ReminderOperationType.INITIAL_SYNC);
        ReconcileReminderCommand versionThree = command(
                3,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                ReminderOperationType.UPDATE_BUSINESS_DATE);

        runConcurrently(
                () -> reconcileService.reconcile(
                        "event-v2", versionTwo),
                () -> reconcileService.reconcile(
                        "event-v3", versionThree));

        assertThat(singleLong(
                "SELECT last_source_version FROM rem_binding"))
                .isEqualTo(3);
        assertThat(singleLong(
                "SELECT COUNT(*) FROM rem_instance "
                        + "WHERE status='PENDING'"))
                .isEqualTo(1);
        assertThat(singleString(
                "SELECT business_date FROM rem_instance "
                        + "WHERE status='PENDING'"))
                .isEqualTo("2026-08-10");
        assertThat(count("rem_command_log")).isEqualTo(2);
    }

    private void runConcurrently(
            Task firstTask, Task secondTask) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> {
                        start.await();
                        return firstTask.run();
                    }),
                    executor.submit(() -> {
                        start.await();
                        return secondTask.run();
                    }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private ReconcileReminderCommand command(
            long sourceVersion,
            LocalDate businessDate,
            LocalDateTime remindAt,
            boolean enabled,
            ReminderOperationType operationType) {
        return new ReconcileReminderCommand(
                USER_ID,
                ReminderBusinessType.ITEM,
                BUSINESS_ID,
                ReminderType.WARRANTY,
                sourceVersion,
                businessDate,
                remindAt,
                enabled,
                "HOLDING",
                operationType,
                ReminderClientContract.SCHEMA_VERSION);
    }

    private long count(String tableName) {
        return singleLong("SELECT COUNT(*) FROM " + tableName);
    }

    private long singleLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String singleString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock reminderTestClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return BUSINESS_ZONE;
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

    @FunctionalInterface
    private interface Task {

        ReconcileReminderResponse run();
    }
}
