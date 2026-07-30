package com.shaopc.worthit.tracking.item.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.item.application.CreateItemCommand;
import com.shaopc.worthit.tracking.item.application.DeleteItemResult;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemService;
import com.shaopc.worthit.tracking.item.application.ItemSummary;
import com.shaopc.worthit.tracking.item.application.UpdateItemCommand;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleResult;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleService;
import com.shaopc.worthit.tracking.lifecycle.application.ReturnItemCommand;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(ItemPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class ItemPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 26);
    private static final AtomicLong CURRENT_USER =
            new AtomicLong(USER_ID);
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(
                    Instant.parse("2026-07-26T04:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemLifecycleService lifecycleService;

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
                Instant.parse("2026-07-26T04:00:00Z"));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update(
                "DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_item_disposal");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_category");
    }

    @Test
    void createsItemUncategorizedAndWarrantyOutboxAtomically()
            throws Exception {
        ItemDetail detail = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "MacBook",
                        null,
                        "1000",
                        "1",
                        null,
                        TODAY.minusDays(9),
                        TODAY.plusDays(15),
                        null));

        assertThat(detail.categoryName()).isEqualTo("未分类");
        assertThat(detail.purchasePrice())
                .isEqualTo("1000.000000");
        assertThat(detail.expectedYears()).isEqualTo("1.000");
        assertThat(detail.residualUnset()).isTrue();
        assertThat(detail.expectedUseDays()).isEqualTo(365);
        assertThat(detail.planDailyCost()).isEqualTo("2.74");
        assertThat(detail.holdingDays()).isEqualTo(10);
        assertThat(detail.holdingDailyCost())
                .isEqualTo("100.00");
        assertThat(detail.warrantyReminderEnabled()).isTrue();

        assertThat(count("trk_category")).isEqualTo(1);
        assertThat(count("trk_item")).isEqualTo(1);
        assertThat(count("trk_outbox_event")).isEqualTo(1);
        assertThat(count("trk_idempotency_record"))
                .isEqualTo(1);

        String payload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM trk_outbox_event",
                String.class);
        JsonNode command = objectMapper.readTree(payload);
        assertThat(command.path("businessType").asText())
                .isEqualTo("ITEM");
        assertThat(command.path("reminderType").asText())
                .isEqualTo("WARRANTY");
        assertThat(command.path("businessId").asLong())
                .isEqualTo(detail.id());
        assertThat(command.path("sourceVersion").asLong())
                .isEqualTo(1);
        assertThat(command.path("businessDate").asText())
                .isEqualTo("2026-08-10");
        assertThat(command.path("remindAt").asText())
                .isEqualTo("2026-08-03T00:00:00");
        assertThat(command.path("operationType").asText())
                .isEqualTo("INITIAL_SYNC");
        assertThat(command.path("schemaVersion").asInt())
                .isEqualTo(1);
    }

    @Test
    void repeatedSameRequestReplaysFirstResultOnlyOnce() {
        String key = UUID.randomUUID().toString();
        CreateItemCommand command = command(
                "相机",
                null,
                "5000",
                "3",
                "0",
                null,
                null,
                null);

        ItemDetail first = itemService.create(key, command);
        ItemDetail last = null;
        for (int index = 0; index < 4; index++) {
            last = itemService.create(key, command);
        }

        assertThat(last).isEqualTo(first);
        assertThat(count("trk_item")).isEqualTo(1);
        assertThat(count("trk_idempotency_record"))
                .isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsIdempotencyConflict() {
        String key = UUID.randomUUID().toString();
        itemService.create(
                key,
                command(
                        "相机",
                        null,
                        "5000",
                        "3",
                        null,
                        null,
                        null,
                        null));

        assertThatThrownBy(() -> itemService.create(
                key,
                command(
                        "镜头",
                        null,
                        "5000",
                        "3",
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_CONFLICT"));
        assertThat(count("trk_item")).isEqualTo(1);
    }

    @Test
    void detailAndListStayWithinCurrentUser() {
        ItemDetail visible = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "MacBook",
                        null,
                        "1000",
                        "1",
                        null,
                        null,
                        null,
                        null));

        PageResult<ItemSummary> matching = itemService.list(
                1, 20, "Mac", null);
        PageResult<ItemSummary> missing = itemService.list(
                1, 20, "相机", null);

        assertThat(itemService.detail(visible.id()))
                .isEqualTo(visible);
        assertThat(matching.getItems())
                .extracting(ItemSummary::id)
                .containsExactly(visible.id());
        assertThat(missing.getItems()).isEmpty();
    }

    @Test
    void terminalDetailKeepsHoldingMetricsFrozenAcrossDays() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "相机",
                        null,
                        "1000",
                        "3",
                        null,
                        TODAY.minusDays(9),
                        null,
                        false));
        jdbcTemplate.update(
                """
                UPDATE trk_item
                SET lifecycle_status = 'SOLD',
                    warranty_reminder_enabled = 0
                WHERE id = ?
                """,
                created.id());
        jdbcTemplate.update(
                """
                INSERT INTO trk_item_disposal (
                    id, user_id, item_id, disposal_type,
                    disposal_date, purchase_price_snapshot,
                    sale_amount, remark, create_time, update_time
                ) VALUES (?, ?, ?, 'SOLD', ?, ?, ?, ?, ?, ?)
                """,
                9001L,
                USER_ID,
                created.id(),
                TODAY,
                new BigDecimal("1000"),
                new BigDecimal("700"),
                "升级设备",
                TODAY.atTime(12, 0),
                TODAY.atTime(12, 0));

        ItemDetail disposalDay = itemService.detail(created.id());
        CURRENT_INSTANT.set(
                Instant.parse("2026-08-26T04:00:00Z"));
        ItemDetail oneMonthLater =
                itemService.detail(created.id());

        assertThat(disposalDay.holdingDays()).isEqualTo(10);
        assertThat(oneMonthLater.holdingDays()).isEqualTo(10);
        assertThat(oneMonthLater.holdingDailyCost())
                .isEqualTo("100.00");
        assertThat(oneMonthLater.disposal().type())
                .isEqualTo("SOLD");
        assertThat(oneMonthLater.disposal().date())
                .isEqualTo(TODAY);
        assertThat(oneMonthLater.disposal().saleAmount())
                .isEqualTo("700.000000");
        assertThat(oneMonthLater.disposal().netCost())
                .isEqualTo("300.000000");
    }

    @Test
    void returnsItemAndClosesWarrantyReminderAtomically()
            throws Exception {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "相机",
                        null,
                        "1000",
                        "3",
                        null,
                        TODAY.minusDays(9),
                        TODAY.plusDays(30),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        String key = UUID.randomUUID().toString();
        ReturnItemCommand command = new ReturnItemCommand(
                created.version(),
                TODAY,
                "  尺寸不合适  ");

        ItemLifecycleResult first =
                lifecycleService.returnItem(
                        created.id(), key, command);
        ItemLifecycleResult replay =
                lifecycleService.returnItem(
                        created.id(), key, command);

        assertThat(replay).isEqualTo(first);
        assertThat(first.lifecycleStatus())
                .isEqualTo("RETURNED");
        assertThat(first.disposal().remark())
                .isEqualTo("尺寸不合适");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT lifecycle_status
                FROM trk_item
                WHERE id = ?
                """,
                String.class,
                created.id())).isEqualTo("RETURNED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT warranty_reminder_enabled
                FROM trk_item
                WHERE id = ?
                """,
                Boolean.class,
                created.id())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM trk_item WHERE id = ?",
                Long.class,
                created.id())).isEqualTo(2L);
        assertThat(count("trk_item_disposal")).isEqualTo(1);
        assertThat(count("trk_outbox_event")).isEqualTo(1);

        JsonNode payload = objectMapper.readTree(
                jdbcTemplate.queryForObject(
                        "SELECT payload_json "
                                + "FROM trk_outbox_event",
                        String.class));
        assertThat(payload.path("sourceVersion").asLong())
                .isEqualTo(2);
        assertThat(payload.path("reminderEnabled").asBoolean())
                .isFalse();
        assertThat(payload.path("businessStatusCode").asText())
                .isEqualTo("RETURNED");
        assertThat(payload.path("operationType").asText())
                .isEqualTo("DISPOSE_ITEM");

        ItemDetail detail = itemService.detail(created.id());
        assertThat(detail.warrantyReminderEnabled()).isFalse();
        assertThat(detail.disposal().type())
                .isEqualTo("RETURNED");
    }

    @Test
    void returnKeepsAlreadyDisabledReminderOff() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "耳机",
                        null,
                        "500",
                        "2",
                        null,
                        TODAY,
                        null,
                        false));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        lifecycleService.returnItem(
                created.id(),
                UUID.randomUUID().toString(),
                new ReturnItemCommand(
                        created.version(), TODAY, null));

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT warranty_reminder_enabled
                FROM trk_item
                WHERE id = ?
                """,
                Boolean.class,
                created.id())).isFalse();
    }

    @Test
    void disposalRollsBackItemAndFactWhenOutboxInsertFails() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "显示器",
                        null,
                        "2000",
                        "3",
                        null,
                        TODAY,
                        TODAY.plusDays(30),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update(
                """
                INSERT INTO trk_outbox_event (
                    id, event_id, aggregate_type, aggregate_id,
                    user_id, source_version, event_type,
                    payload_json, schema_version, status,
                    retry_count, create_time, update_time
                ) VALUES (
                    ?, ?, 'ITEM', ?, ?, 2,
                    'REMINDER_RECONCILE', '{}', 1, 'NEW',
                    0, ?, ?
                )
                """,
                9101L,
                UUID.randomUUID().toString(),
                created.id(),
                USER_ID,
                TODAY.atStartOfDay(),
                TODAY.atStartOfDay());

        assertThatThrownBy(() ->
                lifecycleService.returnItem(
                        created.id(),
                        UUID.randomUUID().toString(),
                        new ReturnItemCommand(
                                created.version(),
                                TODAY,
                                null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT lifecycle_status
                FROM trk_item
                WHERE id = ?
                """,
                String.class,
                created.id())).isEqualTo("HOLDING");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT warranty_reminder_enabled
                FROM trk_item
                WHERE id = ?
                """,
                Boolean.class,
                created.id())).isTrue();
        assertThat(count("trk_item_disposal")).isZero();
        assertThat(count("trk_outbox_event")).isEqualTo(1);
    }

    @Test
    void terminalItemCannotReEnableWarrantyReminder() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "平板",
                        null,
                        "3000",
                        "3",
                        null,
                        TODAY,
                        TODAY.plusDays(30),
                        true));
        ItemLifecycleResult returned =
                lifecycleService.returnItem(
                        created.id(),
                        UUID.randomUUID().toString(),
                        new ReturnItemCommand(
                                created.version(), TODAY, null));

        assertStateConflict(() -> itemService.update(
                created.id(),
                UUID.randomUUID().toString(),
                updateCommand(
                        returned.version(),
                        "平板",
                        created.categoryId(),
                        TODAY.plusDays(30),
                        true)));
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT warranty_reminder_enabled
                FROM trk_item
                WHERE id = ?
                """,
                Boolean.class,
                created.id())).isFalse();
    }

    @Test
    void listTotalMatchesItemsWhenLegacyOrphanExists() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "历史孤儿",
                        null,
                        "1000",
                        "1",
                        null,
                        null,
                        null,
                        false));
        jdbcTemplate.update(
                """
                UPDATE trk_category
                SET del_flag = 1, delete_time = ?
                WHERE id = ?
                """,
                TODAY.atStartOfDay(),
                created.categoryId());

        PageResult<ItemSummary> result =
                itemService.list(1, 20, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void rejectsFuturePurchaseDateAndEnabledReminderWithoutDate() {
        assertThatThrownBy(() -> itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "MacBook",
                        null,
                        "1000",
                        "1",
                        null,
                        TODAY.plusDays(1),
                        null,
                        null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "VAL_INVALID_ARGUMENT"));

        assertThatThrownBy(() -> itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "MacBook",
                        null,
                        "1000",
                        "1",
                        null,
                        null,
                        null,
                        true)))
                .isInstanceOf(BusinessException.class);
        assertThat(count("trk_item")).isZero();
    }

    @Test
    void updateUsesVersionAndWritesOneChangedExpectation()
            throws Exception {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "MacBook",
                        null,
                        "1000",
                        "1",
                        null,
                        null,
                        TODAY.plusDays(15),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        String updateKey = UUID.randomUUID().toString();
        UpdateItemCommand update = updateCommand(
                created.version(),
                "MacBook Pro",
                created.categoryId(),
                TODAY.plusDays(30),
                true);
        ItemDetail updated = itemService.update(
                created.id(),
                updateKey,
                update);
        ItemDetail replay = itemService.update(
                created.id(),
                updateKey,
                update);

        assertThat(updated.name()).isEqualTo("MacBook Pro");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(replay).isEqualTo(updated);
        assertThat(count("trk_outbox_event")).isEqualTo(1);
        JsonNode payload = objectMapper.readTree(
                jdbcTemplate.queryForObject(
                        "SELECT payload_json "
                                + "FROM trk_outbox_event",
                        String.class));
        assertThat(payload.path("sourceVersion").asLong())
                .isEqualTo(2);
        assertThat(payload.path("operationType").asText())
                .isEqualTo("UPDATE_BUSINESS_DATE");

        assertThatThrownBy(() -> itemService.update(
                created.id(),
                UUID.randomUUID().toString(),
                updateCommand(
                        created.version(),
                        "过期更新",
                        created.categoryId(),
                        TODAY.plusDays(31),
                        true)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
        assertThat(count("trk_outbox_event")).isEqualTo(1);
    }

    @Test
    void deleteAndRepeatedRestoreAreAtomicAndIdempotent() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "相机",
                        null,
                        "5000",
                        "3",
                        null,
                        null,
                        TODAY.plusDays(15),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        String deleteKey = UUID.randomUUID().toString();
        DeleteItemResult deleted = itemService.delete(
                created.id(), created.version(), deleteKey);
        DeleteItemResult deleteReplay = itemService.delete(
                created.id(), created.version(), deleteKey);

        assertThat(deleted.restoreDeadline()).isEqualTo(
                LocalDate.of(2026, 7, 26)
                        .atTime(12, 1));
        assertThat(deleteReplay).isEqualTo(deleted);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM trk_item WHERE id = ?",
                Long.class,
                created.id())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_item WHERE id = ?",
                Boolean.class,
                created.id())).isTrue();
        assertThat(count("trk_outbox_event")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operation_code "
                        + "FROM trk_idempotency_record "
                        + "WHERE idempotency_key = ?",
                String.class,
                deleted.restoreToken()))
                .isEqualTo("ITEM_RESTORE");

        ItemDetail first = itemService.restore(
                created.id(), 2, deleted.restoreToken());
        ItemDetail replay = itemService.restore(
                created.id(), 2, deleted.restoreToken());

        assertThat(first.version()).isEqualTo(3);
        assertThat(replay).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_item WHERE id = ?",
                Boolean.class,
                created.id())).isFalse();
        assertThat(count("trk_outbox_event")).isEqualTo(1);
    }

    @Test
    void restoreRejectsWrongTokenVersionExpiryAndOtherUser() {
        ItemDetail created = itemService.create(
                UUID.randomUUID().toString(),
                command(
                        "耳机",
                        null,
                        "1000",
                        "2",
                        null,
                        null,
                        null,
                        false));
        DeleteItemResult deleted = itemService.delete(
                created.id(),
                created.version(),
                UUID.randomUUID().toString());

        assertStateConflict(() -> itemService.restore(
                created.id(), 2, UUID.randomUUID().toString()));
        assertStateConflict(() -> itemService.restore(
                created.id(), 3, deleted.restoreToken()));

        CURRENT_USER.set(2002L);
        assertThatThrownBy(() -> itemService.restore(
                created.id(), 2, deleted.restoreToken()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RES_NOT_FOUND"));

        CURRENT_USER.set(USER_ID);
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-26T04:01:01Z"));
        assertStateConflict(() -> itemService.restore(
                created.id(), 2, deleted.restoreToken()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_item WHERE id = ?",
                Boolean.class,
                created.id())).isTrue();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class);
    }

    private static UpdateItemCommand updateCommand(
            long version,
            String name,
            long categoryId,
            LocalDate warrantyDate,
            boolean reminderEnabled) {
        return new UpdateItemCommand(
                version,
                name,
                categoryId,
                new BigDecimal("1200"),
                new BigDecimal("2"),
                BigDecimal.ZERO,
                TODAY.minusDays(10),
                warrantyDate,
                reminderEnabled,
                "M4",
                "办公使用");
    }

    private static void assertStateConflict(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
    }

    private static CreateItemCommand command(
            String name,
            Long categoryId,
            String price,
            String years,
            String residual,
            LocalDate purchaseDate,
            LocalDate warrantyDate,
            Boolean reminderEnabled) {
        return new CreateItemCommand(
                name,
                categoryId,
                new BigDecimal(price),
                new BigDecimal(years),
                residual == null
                        ? null
                        : new BigDecimal(residual),
                purchaseDate,
                warrantyDate,
                reminderEnabled,
                null,
                null);
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
