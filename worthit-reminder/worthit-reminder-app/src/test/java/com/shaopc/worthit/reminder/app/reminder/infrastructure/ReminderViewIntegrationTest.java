package com.shaopc.worthit.reminder.app.reminder.infrastructure;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.reminder.app.WorthItReminderApplication;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderReconcileService;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderTab;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderViewService;
import com.shaopc.worthit.reminder.app.security.CurrentUserProvider;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(ReminderViewIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItReminderApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class ReminderViewIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final AtomicLong IDS =
            new AtomicLong(9000L);
    private static final AtomicReference<Instant>
            CURRENT_INSTANT = new AtomicReference<>(
                    Instant.parse("2026-07-28T04:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReminderViewService reminderService;

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
    void clearData() {
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T04:00:00Z"));
        jdbcTemplate.update("DELETE FROM rem_command_log");
        jdbcTemplate.update("DELETE FROM rem_instance");
        jdbcTemplate.update("DELETE FROM rem_binding");
    }

    @Test
    void listsOnlyVisiblePendingAndDoneInstances() {
        long due = insertReminder(
                USER_ID,
                "ITEM",
                "WARRANTY",
                "PENDING",
                now().minusHours(1),
                null);
        insertReminder(
                USER_ID,
                "SUBSCRIPTION",
                "RENEWAL",
                "PENDING",
                now().plusHours(1),
                null);
        insertReminder(
                2002L,
                "WISH",
                "WATCH",
                "PENDING",
                now().minusHours(2),
                null);
        insertReminder(
                USER_ID,
                "ITEM",
                "WARRANTY",
                "CANCELED",
                now().minusDays(1),
                now().minusMinutes(30));
        long processed = insertReminder(
                USER_ID,
                "SUBSCRIPTION",
                "RENEWAL",
                "PROCESSED",
                now().minusDays(1),
                now().minusMinutes(20));
        long ignored = insertReminder(
                USER_ID,
                "WISH",
                "WATCH",
                "IGNORED",
                now().minusDays(2),
                now().minusMinutes(10));

        var pending = reminderService.list(
                ReminderTab.PENDING, 1, 20);
        var done = reminderService.list(
                ReminderTab.DONE, 1, 20);

        assertThat(pending.getTotal()).isEqualTo(1);
        assertThat(pending.getItems())
                .extracting(item -> item.id())
                .containsExactly(due);
        assertThat(reminderService.pendingCount())
                .isEqualTo(1);
        assertThat(done.getTotal()).isEqualTo(2);
        assertThat(done.getItems())
                .extracting(item -> item.id())
                .containsExactly(ignored, processed);
    }

    @Test
    void pendingListUsesReminderTimeAscendingAndPaginates() {
        long later = insertReminder(
                USER_ID,
                "ITEM",
                "WARRANTY",
                "PENDING",
                now().minusMinutes(10),
                null);
        long earlier = insertReminder(
                USER_ID,
                "WISH",
                "WATCH",
                "PENDING",
                now().minusHours(2),
                null);

        var firstPage = reminderService.list(
                ReminderTab.PENDING, 1, 1);

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.isHasMore()).isTrue();
        assertThat(firstPage.getItems())
                .extracting(item -> item.id())
                .containsExactly(earlier);
        assertThat(reminderService
                .list(ReminderTab.PENDING, 2, 1)
                .getItems())
                .extracting(item -> item.id())
                .containsExactly(later);
    }

    @Test
    void ignoresDuePendingIdempotently() {
        long reminderId = insertReminder(
                USER_ID,
                "ITEM",
                "WARRANTY",
                "PENDING",
                now().minusMinutes(1),
                null);

        reminderService.ignore(reminderId);
        reminderService.ignore(reminderId);

        assertThat(status(reminderId))
                .isEqualTo("IGNORED");
        assertThat(reminderService.pendingCount())
                .isZero();
        assertThat(reminderService
                .list(ReminderTab.DONE, 1, 20)
                .getItems())
                .extracting(item -> item.id())
                .containsExactly(reminderId);
    }

    @Test
    void rejectsFutureTerminalAndCrossUserIgnore() {
        long future = insertReminder(
                USER_ID,
                "ITEM",
                "WARRANTY",
                "PENDING",
                now().plusMinutes(1),
                null);
        long processed = insertReminder(
                USER_ID,
                "SUBSCRIPTION",
                "RENEWAL",
                "PROCESSED",
                now().minusDays(1),
                now());
        long otherUser = insertReminder(
                2002L,
                "WISH",
                "WATCH",
                "PENDING",
                now().minusMinutes(1),
                null);

        assertThatThrownBy(() ->
                reminderService.ignore(future))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.code())
                                .isEqualTo(
                                        "VAL_STATE_CONFLICT"));
        assertThatThrownBy(() ->
                reminderService.ignore(processed))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.code())
                                .isEqualTo(
                                        "VAL_STATE_CONFLICT"));
        assertThatThrownBy(() ->
                reminderService.ignore(otherUser))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.code())
                                .isEqualTo(
                                        "RES_NOT_FOUND"));
    }

    @Test
    void lateReconcileKeepsAlreadyIgnoredInstance() {
        long bindingId = insertBinding(
                USER_ID, "ITEM", "WARRANTY", 1L);
        long reminderId = insertInstance(
                bindingId,
                USER_ID,
                "PENDING",
                now().minusMinutes(1),
                null);

        reminderService.ignore(reminderId);
        reconcileService.reconcile(
                UUID.randomUUID().toString(),
                command(
                        2L,
                        ReminderOperationType
                                .UPDATE_BUSINESS_DATE,
                        LocalDate.of(2026, 8, 20),
                        LocalDateTime.of(
                                2026, 8, 13, 0, 0),
                        true));

        assertThat(status(reminderId))
                .isEqualTo("IGNORED");
    }

    @Test
    void ignoreCannotOverwriteReconcileTerminalState() {
        long bindingId = insertBinding(
                USER_ID, "ITEM", "WARRANTY", 1L);
        long reminderId = insertInstance(
                bindingId,
                USER_ID,
                "PENDING",
                now().minusMinutes(1),
                null);

        reconcileService.reconcile(
                UUID.randomUUID().toString(),
                command(
                        2L,
                        ReminderOperationType.DISPOSE_ITEM,
                        null,
                        null,
                        false));

        assertThat(status(reminderId))
                .isEqualTo("PROCESSED");
        assertThatThrownBy(() ->
                reminderService.ignore(reminderId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.code())
                                .isEqualTo(
                                        "VAL_STATE_CONFLICT"));
        assertThat(status(reminderId))
                .isEqualTo("PROCESSED");
    }

    @Test
    void disposalCancelsFuturePendingAndCreatesNoReplacement() {
        long bindingId = insertBinding(
                USER_ID, "ITEM", "WARRANTY", 1L);
        long reminderId = insertInstance(
                bindingId,
                USER_ID,
                "PENDING",
                now().plusDays(7),
                null);

        reconcileService.reconcile(
                UUID.randomUUID().toString(),
                command(
                        2L,
                        ReminderOperationType.DISPOSE_ITEM,
                        null,
                        null,
                        false));

        assertThat(status(reminderId))
                .isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM rem_instance
                WHERE binding_id = ?
                  AND status = 'PENDING'
                """,
                Integer.class,
                bindingId)).isZero();
    }

    private long insertReminder(
            long userId,
            String businessType,
            String reminderType,
            String status,
            LocalDateTime remindAt,
            LocalDateTime resolvedAt) {
        long bindingId = insertBinding(
                userId,
                businessType,
                reminderType,
                1L);
        return insertInstance(
                bindingId,
                userId,
                status,
                remindAt,
                resolvedAt);
    }

    private long insertBinding(
            long userId,
            String businessType,
            String reminderType,
            long lastSourceVersion) {
        long id = IDS.incrementAndGet();
        jdbcTemplate.update(
                """
                INSERT INTO rem_binding (
                  id, user_id, business_type, business_id,
                  reminder_type, reminder_enabled,
                  last_source_version, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)
                """,
                id,
                userId,
                businessType,
                IDS.incrementAndGet(),
                reminderType,
                lastSourceVersion,
                now().minusDays(2),
                now().minusDays(2));
        return id;
    }

    private long insertInstance(
            long bindingId,
            long userId,
            String status,
            LocalDateTime remindAt,
            LocalDateTime resolvedAt) {
        long id = IDS.incrementAndGet();
        boolean pending = "PENDING".equals(status);
        jdbcTemplate.update(
                """
                INSERT INTO rem_instance (
                  id, binding_id, user_id, business_date,
                  remind_at, timezone, status,
                  resolved_at, resolution_reason,
                  create_time, update_time
                ) VALUES (
                  ?, ?, ?, ?, ?, 'Asia/Shanghai', ?,
                  ?, ?, ?, ?
                )
                """,
                id,
                bindingId,
                userId,
                remindAt.toLocalDate(),
                remindAt,
                status,
                resolvedAt,
                pending ? null : "TEST_TERMINAL",
                now().minusDays(1),
                resolvedAt == null
                        ? now().minusDays(1)
                        : resolvedAt);
        return id;
    }

    private ReconcileReminderCommand command(
            long sourceVersion,
            ReminderOperationType operationType,
            LocalDate businessDate,
            LocalDateTime remindAt,
            boolean enabled) {
        Long businessId = jdbcTemplate.queryForObject(
                """
                SELECT business_id
                FROM rem_binding
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                USER_ID);
        return new ReconcileReminderCommand(
                USER_ID,
                ReminderBusinessType.ITEM,
                businessId,
                ReminderType.WARRANTY,
                sourceVersion,
                businessDate,
                remindAt,
                enabled,
                enabled ? "HOLDING" : "DISPOSED",
                operationType,
                1);
    }

    private String status(long reminderId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rem_instance
                WHERE id = ?
                """,
                String.class,
                reminderId);
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(
                CURRENT_INSTANT.get(),
                ZoneId.of("Asia/Shanghai"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedContext {

        @Bean
        @Primary
        Clock fixedReminderClock() {
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

        @Bean
        @Primary
        CurrentUserProvider testCurrentUserProvider() {
            return () -> new UserContext(USER_ID);
        }
    }
}
