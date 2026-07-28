package com.shaopc.worthit.tracking.outbox.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.client.api.ReminderCommandClient;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.outbox.application.OutboxRelayService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@Import(OutboxRelayIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class OutboxRelayIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 28, 12, 0);
    private static final int MAX_RETRIES = 8;

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private ReminderCommandClient reminderClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ExecutorService executor;

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
    void prepare() {
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        reset(reminderClient);
        when(reminderClient.reconcile(anyString(), any()))
                .thenReturn(new ReconcileReminderResponse(
                        true,
                        ReconcileResultCode.APPLIED,
                        false,
                        1L,
                        1L));
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void closeExecutor() {
        executor.shutdownNow();
    }

    @Test
    void deliversNewEventAndMarksSucceeded()
            throws Exception {
        String eventId = insertEvent(
                "NEW", 0, null, null, null);

        assertThat(relayService.relayBatch()).isEqualTo(1);

        verify(reminderClient).reconcile(
                eventId, command());
        assertThat(status(eventId)).isEqualTo("SUCCEEDED");
        assertThat(integer(eventId, "retry_count"))
                .isZero();
        assertThat(value(eventId, "locked_by"))
                .isNull();
        assertThat(value(eventId, "processed_at"))
                .isEqualTo(NOW);
    }

    @Test
    void reminderFailureSchedulesExponentialRetry()
            throws Exception {
        String eventId = insertEvent(
                "NEW", 0, null, null, null);
        doThrow(new IllegalStateException("reminder 503"))
                .when(reminderClient)
                .reconcile(anyString(), any());

        assertThat(relayService.relayBatch()).isEqualTo(1);

        assertThat(status(eventId)).isEqualTo("RETRY_WAIT");
        assertThat(integer(eventId, "retry_count"))
                .isEqualTo(1);
        assertThat(value(eventId, "next_retry_at"))
                .isEqualTo(NOW.plusSeconds(5));
        assertThat(value(eventId, "locked_by")).isNull();
        assertThat(text(eventId, "last_error"))
                .contains("IllegalStateException")
                .contains("reminder 503");
    }

    @Test
    void recoversExpiredProcessingLeaseWithSameEventId()
            throws Exception {
        String eventId = insertEvent(
                "PROCESSING",
                2,
                null,
                "dead-instance",
                NOW.minusSeconds(31));

        assertThat(relayService.relayBatch()).isEqualTo(1);

        verify(reminderClient).reconcile(
                eventId, command());
        assertThat(status(eventId)).isEqualTo("SUCCEEDED");
        assertThat(integer(eventId, "retry_count"))
                .isEqualTo(2);
    }

    @Test
    void doesNotStealAnActiveProcessingLease()
            throws Exception {
        String eventId = insertEvent(
                "PROCESSING",
                0,
                null,
                "active-instance",
                NOW.minusSeconds(29));

        assertThat(relayService.relayBatch()).isZero();

        assertThat(status(eventId)).isEqualTo("PROCESSING");
        assertThat(text(eventId, "locked_by"))
                .isEqualTo("active-instance");
    }

    @Test
    void movesEventToDeadAtRetryLimit()
            throws Exception {
        String eventId = insertEvent(
                "RETRY_WAIT",
                MAX_RETRIES - 1,
                NOW.minusSeconds(1),
                null,
                null);
        doThrow(new IllegalStateException("still unavailable"))
                .when(reminderClient)
                .reconcile(anyString(), any());

        assertThat(relayService.relayBatch()).isEqualTo(1);

        assertThat(status(eventId)).isEqualTo("DEAD");
        assertThat(integer(eventId, "retry_count"))
                .isEqualTo(MAX_RETRIES);
        assertThat(value(eventId, "next_retry_at")).isNull();
        assertThat(value(eventId, "processed_at"))
                .isEqualTo(NOW);
    }

    @Test
    void concurrentBatchCannotDeliverFreshLeaseTwice()
            throws Exception {
        String eventId = insertEvent(
                "NEW", 0, null, null, null);
        CountDownLatch enteredClient = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredClient.countDown();
            if (!releaseClient.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "等待并发测试释放超时");
            }
            return new ReconcileReminderResponse(
                    true,
                    ReconcileResultCode.APPLIED,
                    false,
                    1L,
                    1L);
        }).when(reminderClient)
                .reconcile(anyString(), any());

        Future<Integer> first =
                executor.submit(relayService::relayBatch);
        assertThat(enteredClient.await(
                5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(relayService.relayBatch()).isZero();
        } finally {
            releaseClient.countDown();
        }

        assertThat(first.get(5, TimeUnit.SECONDS))
                .isEqualTo(1);
        verify(reminderClient).reconcile(
                eventId, command());
        assertThat(status(eventId)).isEqualTo("SUCCEEDED");
    }

    private String insertEvent(
            String status,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lockedBy,
            LocalDateTime lockedAt)
            throws JsonProcessingException {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO trk_outbox_event (
                  id, event_id, aggregate_type, aggregate_id,
                  user_id, source_version, event_type,
                  payload_json, schema_version, status,
                  retry_count, next_retry_at, locked_by,
                  locked_at, create_time, update_time
                ) VALUES (
                  ?, ?, 'ITEM', 2001, 1001, 1,
                  'REMINDER_RECONCILE', ?, 1, ?,
                  ?, ?, ?, ?, ?, ?
                )
                """,
                Math.abs(UUID.randomUUID()
                        .getMostSignificantBits()),
                eventId,
                objectMapper.writeValueAsString(command()),
                status,
                retryCount,
                nextRetryAt,
                lockedBy,
                lockedAt,
                NOW.minusMinutes(1),
                NOW.minusMinutes(1));
        return eventId;
    }

    private static ReconcileReminderCommand command() {
        return new ReconcileReminderCommand(
                1001L,
                ReminderBusinessType.ITEM,
                2001L,
                ReminderType.WARRANTY,
                1L,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                true,
                "HOLDING",
                ReminderOperationType.INITIAL_SYNC,
                1);
    }

    private String status(String eventId) {
        return text(eventId, "status");
    }

    private int integer(String eventId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM trk_outbox_event"
                        + " WHERE event_id = ?",
                Integer.class,
                eventId);
    }

    private String text(String eventId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM trk_outbox_event"
                        + " WHERE event_id = ?",
                String.class,
                eventId);
    }

    private LocalDateTime value(
            String eventId,
            String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM trk_outbox_event"
                        + " WHERE event_id = ?",
                LocalDateTime.class,
                eventId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedContext {

        @Bean
        @Primary
        Clock fixedTrackingClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-28T04:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        ReminderCommandClient testReminderClient() {
            return mock(ReminderCommandClient.class);
        }
    }
}
