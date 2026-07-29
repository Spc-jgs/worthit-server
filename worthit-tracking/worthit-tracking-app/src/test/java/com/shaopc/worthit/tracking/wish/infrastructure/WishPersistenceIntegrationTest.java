package com.shaopc.worthit.tracking.wish.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.wish.application.CreateWishCommand;
import com.shaopc.worthit.tracking.wish.application.DeleteWishResult;
import com.shaopc.worthit.tracking.wish.application.UpdateWishCommand;
import com.shaopc.worthit.tracking.wish.application.WishDetail;
import com.shaopc.worthit.tracking.wish.application.WishPurchaseResult;
import com.shaopc.worthit.tracking.wish.application.WishService;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(WishPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class WishPersistenceIntegrationTest {

    private static final long USER_ID = 4001L;
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 29);
    private static final AtomicLong CURRENT_USER =
            new AtomicLong(USER_ID);
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(
                    Instant.parse("2026-07-29T04:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WishService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
    void clearTrackingData() {
        CURRENT_USER.set(USER_ID);
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-29T04:00:00Z"));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update(
                "DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_category");
    }

    @Test
    void createsConsideringWishWithDefaultWatchReminder()
            throws Exception {
        WishDetail result = service.create(
                key(), command(
                        "显示器", LocalDate.of(2026, 8, 10)));

        assertThat(result.status()).isEqualTo("CONSIDERING");
        assertThat(result.planDailyCostDisplay())
                .isEqualTo("¥2.74/天");
        assertThat(result.residualUnset()).isTrue();
        assertThat(result.watchReminderEnabled()).isTrue();
        JsonNode payload = outboxPayload(result.id(), 1);
        assertThat(payload.path("businessType").asText())
                .isEqualTo("WISH");
        assertThat(payload.path("reminderType").asText())
                .isEqualTo("WATCH");
        assertThat(payload.path("remindAt").asText())
                .isEqualTo("2026-08-10T00:00:00");
    }

    @Test
    void noDeadlineDisablesReminderAndWritesNoOutbox() {
        WishDetail result = service.create(
                key(), command("键盘", null));

        assertThat(result.watchReminderEnabled()).isFalse();
        assertThat(count("trk_outbox_event")).isZero();
    }

    @Test
    void concurrentPurchaseCreatesOneItemAndReturnsSameItem()
            throws Exception {
        WishDetail wish = service.create(
                key(), command("显示器", LocalDate.of(2026, 8, 10)));
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<WishPurchaseResult>> futures = List.of(
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.purchase(
                                wish.id(), wish.version(), key());
                    }),
                    executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.purchase(
                                wish.id(), wish.version(), key());
                    }));
            ready.await();
            start.countDown();
            WishPurchaseResult first = futures.get(0).get();
            WishPurchaseResult second = futures.get(1).get();

            assertThat(first.item().id())
                    .isEqualTo(second.item().id());
            assertThat(first.wish().status())
                    .isEqualTo("PURCHASED");
            assertThat(count("trk_item")).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT source_wish_id FROM trk_item",
                    Long.class)).isEqualTo(wish.id());
            assertThat(outboxOperation(
                    wish.id(), 2)).isEqualTo("PURCHASE_WISH");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void abandonReconsiderAndAbandonKeepLatestReason() {
        WishDetail created = service.create(
                key(), command("显示器", LocalDate.of(2026, 8, 10)));
        WishDetail abandoned = service.abandon(
                created.id(), created.version(), "原因A", key());
        WishDetail reconsidered = service.reconsider(
                created.id(), abandoned.version(), key());
        WishDetail finalState = service.abandon(
                created.id(), reconsidered.version(), "原因B", key());

        assertThat(finalState.status()).isEqualTo("ABANDONED");
        assertThat(finalState.lastAbandonReason())
                .isEqualTo("原因B");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_abandon_reason FROM trk_wish",
                String.class)).isEqualTo("原因B");
    }

    @Test
    void updateChangesWatchDateAndDeleteRestoreKeepsState() {
        WishDetail created = service.create(
                key(), command("显示器", LocalDate.of(2026, 8, 10)));
        WishDetail updated = service.update(
                created.id(), key(),
                new UpdateWishCommand(
                        created.version(), "显示器", null,
                        new BigDecimal("1000"),
                        BigDecimal.ONE, null,
                        "护眼", null,
                        LocalDate.of(2026, 8, 20), true));
        assertThat(outboxOperation(
                created.id(), updated.version()))
                .isEqualTo("UPDATE_BUSINESS_DATE");

        DeleteWishResult deleted = service.delete(
                created.id(), updated.version(), key());
        WishDetail restored = service.restore(
                created.id(), updated.version() + 1,
                deleted.restoreToken());
        WishDetail replay = service.restore(
                created.id(), updated.version() + 1,
                deleted.restoreToken());

        assertThat(restored.status()).isEqualTo("CONSIDERING");
        assertThat(replay.id()).isEqualTo(restored.id());
        assertThat(restored.version())
                .isEqualTo(updated.version() + 2);
        assertThat(count("trk_wish")).isEqualTo(1);
    }

    @Test
    void restoreRejectsWrongTokenAndOtherUser() {
        WishDetail created = service.create(
                key(), command("显示器", null));
        DeleteWishResult deleted = service.delete(
                created.id(), created.version(), key());

        assertThatThrownBy(() -> service.restore(
                created.id(), created.version() + 1, key()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态");

        CURRENT_USER.set(USER_ID + 1);
        assertThatThrownBy(() -> service.restore(
                created.id(), created.version() + 1,
                deleted.restoreToken()))
                .isInstanceOf(BusinessException.class);
    }

    private CreateWishCommand command(
            String name, LocalDate deadline) {
        return new CreateWishCommand(
                name, null, new BigDecimal("1000"),
                BigDecimal.ONE, null, "提升效率", null,
                deadline, null);
    }

    private JsonNode outboxPayload(
            long wishId, long sourceVersion) throws Exception {
        String json = jdbcTemplate.queryForObject(
                """
                SELECT payload_json
                FROM trk_outbox_event
                WHERE aggregate_id = ?
                  AND source_version = ?
                """,
                String.class, wishId, sourceVersion);
        return objectMapper.readTree(json);
    }

    private String outboxOperation(
            long wishId, long sourceVersion) {
        try {
            return outboxPayload(wishId, sourceVersion)
                    .path("operationType").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedContext {

        @Bean
        @Primary
        CurrentUserProvider fixedCurrentUserProvider() {
            return () -> new UserContext(CURRENT_USER.get());
        }

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
