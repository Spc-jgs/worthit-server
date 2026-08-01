package com.shaopc.worthit.tracking.recovery.infrastructure;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResourceSummary;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResult;
import com.shaopc.worthit.tracking.recovery.application.RecoveryService;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(RecoveryPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class RecoveryPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;
    private static final long ACTIVE_CATEGORY_ID = 101L;
    private static final long DELETED_CATEGORY_ID = 102L;
    private static final AtomicLong CURRENT_USER =
            new AtomicLong(USER_ID);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private CategoryService categoryService;

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
    void setUp() {
        CURRENT_USER.set(USER_ID);
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update("DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_item_replacement");
        jdbcTemplate.update("DELETE FROM trk_item_disposal");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_category");
        insertCategory(
                ACTIVE_CATEGORY_ID, USER_ID,
                "数码", null, false, null);
        insertCategory(
                DELETED_CATEGORY_ID, USER_ID,
                "旧分类", null, true,
                LocalDateTime.of(2026, 7, 30, 9, 0));
        insertCategory(
                201L, OTHER_USER_ID,
                "他人分类", null, false, null);
    }

    @Test
    void listsThreeTypesWithStableOrderFilterAndIsolation() {
        insertDeletedItem(
                1001L, USER_ID, ACTIVE_CATEGORY_ID,
                "相机", "HOLDING", 2,
                LocalDateTime.of(2026, 8, 2, 10, 0));
        insertDeletedSubscription(
                1002L, USER_ID, ACTIVE_CATEGORY_ID,
                "云盘", "PAUSED", 4,
                LocalDateTime.of(2026, 8, 2, 11, 0));
        insertDeletedWish(
                1003L, USER_ID, DELETED_CATEGORY_ID,
                "耳机", "ABANDONED", 6,
                LocalDateTime.of(2026, 8, 2, 11, 0));
        insertDeletedItem(
                2001L, OTHER_USER_ID, 201L,
                "他人物品", "HOLDING", 2,
                LocalDateTime.of(2026, 8, 2, 12, 0));

        PageResult<RecoveryResourceSummary> all =
                recoveryService.list(null, 1, 20);

        assertThat(all.getTotal()).isEqualTo(3);
        assertThat(all.getItems())
                .extracting(RecoveryResourceSummary::resourceType)
                .containsExactly(
                        RecoveryResourceType.SUBSCRIPTION,
                        RecoveryResourceType.WISH,
                        RecoveryResourceType.ITEM);
        assertThat(all.getItems().get(1).categoryName())
                .isEqualTo("旧分类");
        assertThat(all.getItems().get(1).categoryAvailable())
                .isFalse();

        PageResult<RecoveryResourceSummary> items =
                recoveryService.list(
                        RecoveryResourceType.ITEM, 1, 20);
        assertThat(items.getTotal()).isEqualTo(1);
        assertThat(items.getItems())
                .extracting(RecoveryResourceSummary::name)
                .containsExactly("相机");
    }

    @Test
    void restoresAllTypesIdempotentlyWithoutOutbox() {
        insertDeletedItem(
                1001L, USER_ID, ACTIVE_CATEGORY_ID,
                "相机", "SOLD", 2,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        insertDeletedSubscription(
                1002L, USER_ID, ACTIVE_CATEGORY_ID,
                "云盘", "ENDED", 4,
                LocalDateTime.of(2026, 8, 1, 11, 0));
        insertDeletedWish(
                1003L, USER_ID, DELETED_CATEGORY_ID,
                "耳机", "ABANDONED", 6,
                LocalDateTime.of(2026, 8, 1, 12, 0));

        String itemKey = UUID.randomUUID().toString();
        RecoveryResult item = recoveryService.restore(
                RecoveryResourceType.ITEM,
                1001L,
                2,
                itemKey);
        RecoveryResult itemReplay = recoveryService.restore(
                RecoveryResourceType.ITEM,
                1001L,
                2,
                itemKey);
        RecoveryResult subscription = recoveryService.restore(
                RecoveryResourceType.SUBSCRIPTION,
                1002L,
                4,
                UUID.randomUUID().toString());
        RecoveryResult wish = recoveryService.restore(
                RecoveryResourceType.WISH,
                1003L,
                6,
                UUID.randomUUID().toString());

        assertThat(itemReplay).isEqualTo(item);
        assertThat(item.status()).isEqualTo("SOLD");
        assertThat(item.version()).isEqualTo(3);
        assertThat(item.categoryFallbackApplied()).isFalse();
        assertThat(subscription.status()).isEqualTo("ENDED");
        assertThat(subscription.version()).isEqualTo(5);
        assertThat(wish.status()).isEqualTo("ABANDONED");
        assertThat(wish.version()).isEqualTo(7);
        assertThat(wish.categoryFallbackApplied()).isTrue();
        assertThat(wish.categoryName()).isEqualTo("未分类");
        assertThat(wish.categoryId())
                .isNotEqualTo(DELETED_CATEGORY_ID);
        assertThat(count("trk_outbox_event")).isZero();
        assertThat(recoveryService.list(null, 1, 20).getTotal())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operation_code "
                        + "FROM trk_idempotency_record "
                        + "WHERE idempotency_key = ?",
                String.class,
                itemKey)).isEqualTo("ITEM_FULL_RESTORE");
    }

    @Test
    void rejectsVersionConflictIdempotencyConflictAndOtherUser() {
        insertDeletedItem(
                1001L, USER_ID, ACTIVE_CATEGORY_ID,
                "相机", "HOLDING", 2,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        String key = UUID.randomUUID().toString();
        recoveryService.restore(
                RecoveryResourceType.ITEM,
                1001L,
                2,
                key);

        assertCode(
                "IDEM_CONFLICT",
                () -> recoveryService.restore(
                        RecoveryResourceType.ITEM,
                        1001L,
                        3,
                        key));
        assertCode(
                "VAL_STATE_CONFLICT",
                () -> recoveryService.restore(
                        RecoveryResourceType.ITEM,
                        1001L,
                        2,
                        UUID.randomUUID().toString()));

        insertDeletedWish(
                2001L, OTHER_USER_ID, 201L,
                "他人想买", "CONSIDERING", 2,
                LocalDateTime.of(2026, 8, 1, 11, 0));
        assertCode(
                "RES_NOT_FOUND",
                () -> recoveryService.restore(
                        RecoveryResourceType.WISH,
                        2001L,
                        2,
                        UUID.randomUUID().toString()));
    }

    @Test
    void concurrentFullRestoreHasSingleWinner() throws Exception {
        insertDeletedItem(
                1001L, USER_ID, ACTIVE_CATEGORY_ID,
                "相机", "HOLDING", 2,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() ->
                    restoreCode(start));
            Future<String> second = executor.submit(() ->
                    restoreCode(start));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            "OK", "VAL_STATE_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM trk_item WHERE id = 1001",
                Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_item WHERE id = 1001",
                Boolean.class)).isFalse();
    }

    @Test
    void fullRestoreAndCategoryDeleteNeverLeaveDanglingReference()
            throws Exception {
        insertDeletedItem(
                1001L, USER_ID, ACTIVE_CATEGORY_ID,
                "相机", "HOLDING", 2,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(2);
        try {
            Future<String> restore = executor.submit(() ->
                    restoreCode(start));
            Future<String> deleteCategory = executor.submit(() -> {
                start.await();
                try {
                    categoryService.delete(ACTIVE_CATEGORY_ID);
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.code();
                }
            });
            start.countDown();

            assertThat(restore.get()).isEqualTo("OK");
            assertThat(deleteCategory.get())
                    .isIn("OK", "BIZ_CATEGORY_IN_USE");
        } finally {
            executor.shutdownNow();
        }

        Long restoredCategoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM trk_item WHERE id = 1001 "
                        + "AND del_flag = 0",
                Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_category WHERE id = ?",
                Boolean.class,
                restoredCategoryId)).isFalse();
    }

    private String restoreCode(CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            recoveryService.restore(
                    RecoveryResourceType.ITEM,
                    1001L,
                    2,
                    UUID.randomUUID().toString());
            return "OK";
        } catch (BusinessException exception) {
            return exception.code();
        }
    }

    private void insertCategory(
            long id,
            long userId,
            String name,
            String systemCode,
            boolean deleted,
            LocalDateTime deleteTime) {
        jdbcTemplate.update("""
                INSERT INTO trk_category(
                  id,user_id,name,system_code,version,
                  create_by,create_time,update_by,update_time,
                  del_flag,delete_time)
                VALUES (?,?,?,?,1,?,?,?,?,?,?)
                """,
                id, userId, name, systemCode,
                userId, LocalDateTime.of(2026, 7, 1, 9, 0),
                userId, LocalDateTime.of(2026, 7, 1, 9, 0),
                deleted, deleteTime);
    }

    private void insertDeletedItem(
            long id,
            long userId,
            long categoryId,
            String name,
            String status,
            long version,
            LocalDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO trk_item(
                  id,user_id,category_id,name,purchase_price,
                  expected_years,warranty_reminder_enabled,
                  lifecycle_status,version,create_by,create_time,
                  update_by,update_time,del_flag,delete_time)
                VALUES (?,?,?,?,1000,2,0,?,?,?,?,?,?,1,?)
                """,
                id, userId, categoryId, name, status, version,
                userId, LocalDateTime.of(2026, 7, 1, 9, 0),
                userId, deletedAt, deletedAt);
    }

    private void insertDeletedSubscription(
            long id,
            long userId,
            long categoryId,
            String name,
            String status,
            long version,
            LocalDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO trk_subscription(
                  id,user_id,category_id,name,amount,currency,
                  billing_cycle_type,auto_renew,
                  renewal_reminder_enabled,status,version,
                  create_by,create_time,update_by,update_time,
                  del_flag,delete_time)
                VALUES (?,?,?,?,20,'CNY','MONTHLY','UNKNOWN',
                        0,?,?,?,?,?,?,1,?)
                """,
                id, userId, categoryId, name, status, version,
                userId, LocalDateTime.of(2026, 7, 1, 9, 0),
                userId, deletedAt, deletedAt);
    }

    private void insertDeletedWish(
            long id,
            long userId,
            long categoryId,
            String name,
            String status,
            long version,
            LocalDateTime deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO trk_wish(
                  id,user_id,category_id,name,expected_price,
                  expected_years,watch_reminder_enabled,status,
                  version,create_by,create_time,update_by,
                  update_time,del_flag,delete_time)
                VALUES (?,?,?,?,500,1,0,?,?,?,?,?,?,1,?)
                """,
                id, userId, categoryId, name, status, version,
                userId, LocalDateTime.of(2026, 7, 1, 9, 0),
                userId, deletedAt, deletedAt);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class);
    }

    private static void assertCode(
            String code,
            Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(code));
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
            return Clock.fixed(
                    Instant.parse("2026-08-02T04:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }
    }
}
