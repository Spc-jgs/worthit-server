package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.item.application.ItemService;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionService;
import com.shaopc.worthit.tracking.wish.application.WishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(CategoryReferenceConcurrencyIntegrationTest
        .TestUserConfiguration.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class CategoryReferenceConcurrencyIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long BUSINESS_ID = 9001L;
    private static final long RESTORE_GRANT_ID = 8001L;
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime TEST_BUSINESS_TIME =
            LocalDateTime.of(2026, 7, 29, 17, 0);
    private static final Instant BEFORE_RESTORE_DEADLINE =
            Instant.parse("2026-07-29T09:00:59Z");
    private static final Instant AFTER_RESTORE_DEADLINE =
            Instant.parse("2026-07-29T09:01:01Z");
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(BEFORE_RESTORE_DEADLINE);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryReferenceResolver categoryReferenceResolver;

    @Autowired
    private ItemService itemService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private WishService wishService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private JdbcTemplate lockObservationJdbc;

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
        CURRENT_INSTANT.set(BEFORE_RESTORE_DEADLINE);
        jdbcTemplate.update("DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_category");
        lockObservationJdbc = new JdbcTemplate(
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(),
                        "root",
                        MYSQL.getPassword()));
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(
                5, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ReferenceWrite.class)
    void categoryDeleteSerializesWithEveryReferenceWrite(
            ReferenceWrite referenceWrite) throws Exception {
        Category target = categoryService.create("目标分类");
        Category source = categoryService.create("来源分类");
        referenceWrite.prepare(jdbcTemplate, source.id(), target.id());

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> lockHolder = executor.submit(() ->
                inTransaction(() -> {
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id
                            FROM trk_category
                            WHERE id = ?
                            FOR UPDATE
                            """,
                            Long.class,
                            target.id());
                    lockAcquired.countDown();
                    await(releaseLock);
                }));
        assertThat(lockAcquired.await(
                5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch deleteStarted = new CountDownLatch(1);
        Future<OperationResult> delete = executor.submit(() -> {
            deleteStarted.countDown();
            return capture(() -> categoryService.delete(target.id()));
        });
        assertThat(deleteStarted.await(
                5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch writeStarted = new CountDownLatch(1);
        Future<OperationResult> write = executor.submit(() -> {
            writeStarted.countDown();
            return capture(() -> inTransaction(() -> {
                categoryReferenceResolver.resolve(
                        target.id(), USER_ID);
                referenceWrite.execute(
                        jdbcTemplate, target.id());
            }));
        });
        assertThat(writeStarted.await(
                5, TimeUnit.SECONDS)).isTrue();
        awaitLockWaiters("trk_category", 2);

        releaseLock.countDown();
        lockHolder.get(5, TimeUnit.SECONDS);
        OperationResult deleteResult =
                delete.get(5, TimeUnit.SECONDS);
        OperationResult writeResult =
                write.get(5, TimeUnit.SECONDS);

        assertThat(deleteResult.succeeded()
                ^ writeResult.succeeded()).isTrue();
        assertThat(deleteResult.failure())
                .satisfiesAnyOf(
                        failure -> assertThat(failure).isNull(),
                        failure -> assertThat(failure)
                                .isInstanceOf(BusinessException.class));
        assertThat(writeResult.failure())
                .satisfiesAnyOf(
                        failure -> assertThat(failure).isNull(),
                        failure -> assertThat(failure)
                                .isInstanceOf(BusinessException.class));
        assertThat(activeOrphanCount(
                referenceWrite.table())).isZero();
    }

    @ParameterizedTest
    @EnumSource(AcceptedRestore.class)
    void acceptedRestoreReservesCategoryBeforeTokenClaim(
            AcceptedRestore acceptedRestore) throws Exception {
        Category category = categoryService.create("待恢复分类");
        LocalDateTime deletedAt = TEST_BUSINESS_TIME;
        String restoreToken =
                "restore-" + acceptedRestore.name();
        acceptedRestore.prepare(
                jdbcTemplate, category.id(), deletedAt);
        insertRestoreGrant(
                acceptedRestore.operationCode(),
                restoreToken,
                deletedAt.plusSeconds(60));

        CountDownLatch tokenLocked = new CountDownLatch(1);
        CountDownLatch releaseToken = new CountDownLatch(1);
        Future<?> tokenLockHolder = executor.submit(() ->
                inTransaction(() -> {
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id
                            FROM trk_idempotency_record
                            WHERE id = ?
                            FOR UPDATE
                            """,
                            Long.class,
                            RESTORE_GRANT_ID);
                    tokenLocked.countDown();
                    await(releaseToken);
                }));
        assertThat(tokenLocked.await(
                5, TimeUnit.SECONDS)).isTrue();

        Future<OperationResult> restore = executor.submit(
                () -> capture(() -> acceptedRestore.restore(
                        itemService,
                        subscriptionService,
                        wishService,
                        restoreToken)));
        awaitLockWaiters("trk_idempotency_record", 1);

        CURRENT_INSTANT.set(AFTER_RESTORE_DEADLINE);
        Future<OperationResult> delete = executor.submit(
                () -> capture(
                        () -> categoryService.delete(category.id())));
        boolean deleteCompletedBeforeTokenRelease;
        try {
            delete.get(1, TimeUnit.SECONDS);
            deleteCompletedBeforeTokenRelease = true;
        } catch (TimeoutException expected) {
            deleteCompletedBeforeTokenRelease = false;
        } finally {
            releaseToken.countDown();
        }

        tokenLockHolder.get(5, TimeUnit.SECONDS);
        OperationResult restoreResult =
                restore.get(5, TimeUnit.SECONDS);
        OperationResult deleteResult =
                delete.get(5, TimeUnit.SECONDS);

        assertThat(deleteCompletedBeforeTokenRelease)
                .as("分类删除不得越过已开始校验的恢复")
                .isFalse();
        assertThat(restoreResult.succeeded()).isTrue();
        assertThat(deleteResult.failure())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CATEGORY_IN_USE"));
        assertThat(activeOrphanCount(
                acceptedRestore.table())).isZero();
    }

    private void awaitLockWaiters(
            String table, int expected) {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(5);
        int waiters;
        do {
            waiters = lockObservationJdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits w
                    JOIN performance_schema.data_locks requested
                      ON requested.engine = w.engine
                     AND requested.engine_lock_id =
                         w.requesting_engine_lock_id
                    WHERE requested.object_schema = DATABASE()
                      AND requested.object_name = ?
                    """,
                    Integer.class,
                    table);
            if (waiters >= expected) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "等待分类行锁时被中断", exception);
            }
        } while (System.nanoTime() < deadline);
        throw new AssertionError(
                "分类行锁等待者不足，expected="
                        + expected + ", actual=" + waiters);
    }

    private void insertRestoreGrant(
            String operationCode,
            String restoreToken,
            LocalDateTime deadline) {
        LocalDateTime createdAt = deadline.minusSeconds(60);
        jdbcTemplate.update(
                """
                INSERT INTO trk_idempotency_record (
                    id, user_id, operation_code,
                    idempotency_key, request_hash,
                    status, processing_expire_at, expires_at,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                RESTORE_GRANT_ID,
                USER_ID,
                operationCode,
                restoreToken,
                restoreHash(BUSINESS_ID, 1L),
                "PROCESSING",
                deadline,
                deadline,
                createdAt,
                createdAt);
    }

    private static String restoreHash(
            long resourceId, long deletedVersion) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (resourceId + ":" + deletedVersion)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256", exception);
        }
    }

    private int activeOrphanCount(String table) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM %s business
                LEFT JOIN trk_category category
                  ON category.id = business.category_id
                 AND category.user_id = business.user_id
                 AND category.del_flag = 0
                WHERE business.user_id = ?
                  AND business.del_flag = 0
                  AND category.id IS NULL
                """.formatted(table),
                Integer.class,
                USER_ID);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> action.run());
    }

    private static OperationResult capture(Runnable action) {
        try {
            action.run();
            return new OperationResult(true, null);
        } catch (RuntimeException failure) {
            return new OperationResult(false, failure);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待释放分类行锁超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "等待释放分类行锁时被中断", exception);
        }
    }

    private record OperationResult(
            boolean succeeded, RuntimeException failure) {
    }

    private enum ReferenceWrite {
        ITEM_CREATE("trk_item", WriteKind.CREATE),
        ITEM_UPDATE("trk_item", WriteKind.UPDATE),
        ITEM_RESTORE("trk_item", WriteKind.RESTORE),
        SUBSCRIPTION_CREATE(
                "trk_subscription", WriteKind.CREATE),
        SUBSCRIPTION_UPDATE(
                "trk_subscription", WriteKind.UPDATE),
        SUBSCRIPTION_RESTORE(
                "trk_subscription", WriteKind.RESTORE),
        WISH_CREATE("trk_wish", WriteKind.CREATE),
        WISH_UPDATE("trk_wish", WriteKind.UPDATE),
        WISH_RESTORE("trk_wish", WriteKind.RESTORE);

        private final String table;
        private final WriteKind kind;

        ReferenceWrite(String table, WriteKind kind) {
            this.table = table;
            this.kind = kind;
        }

        String table() {
            return table;
        }

        void prepare(
                JdbcTemplate jdbc,
                long sourceCategoryId,
                long targetCategoryId) {
            if (kind == WriteKind.CREATE) {
                return;
            }
            insert(jdbc, kind == WriteKind.UPDATE
                    ? sourceCategoryId : targetCategoryId);
            if (kind == WriteKind.RESTORE) {
                jdbc.update(
                        "UPDATE " + table
                                + " SET del_flag = 1,"
                                + " delete_time = ?",
                        TEST_BUSINESS_TIME.minusSeconds(30));
            }
        }

        void execute(JdbcTemplate jdbc, long targetCategoryId) {
            switch (kind) {
                case CREATE -> insert(jdbc, targetCategoryId);
                case UPDATE -> jdbc.update(
                        "UPDATE " + table
                                + " SET category_id = ?"
                                + " WHERE id = ?",
                        targetCategoryId,
                        BUSINESS_ID);
                case RESTORE -> jdbc.update(
                        "UPDATE " + table
                                + " SET del_flag = 0,"
                                + " delete_time = NULL"
                                + " WHERE id = ?",
                        BUSINESS_ID);
            }
        }

        private void insert(
                JdbcTemplate jdbc, long categoryId) {
            LocalDateTime now = TEST_BUSINESS_TIME;
            switch (table) {
                case "trk_item" -> jdbc.update(
                        """
                        INSERT INTO trk_item (
                            id, user_id, category_id, name,
                            purchase_price, expected_years,
                            warranty_reminder_enabled,
                            lifecycle_status, version,
                            create_by, create_time,
                            update_by, update_time, del_flag
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  ?, ?, ?, ?, ?)
                        """,
                        BUSINESS_ID, USER_ID, categoryId,
                        "物品", "1000", "1", false,
                        "HOLDING", 1L, USER_ID, now,
                        USER_ID, now, false);
                case "trk_subscription" -> jdbc.update(
                        """
                        INSERT INTO trk_subscription (
                            id, user_id, category_id, name,
                            amount, currency, billing_cycle_type,
                            auto_renew, renewal_reminder_enabled,
                            status, version, create_by,
                            create_time, update_by,
                            update_time, del_flag
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  ?, ?, ?, ?, ?, ?, ?)
                        """,
                        BUSINESS_ID, USER_ID, categoryId,
                        "订阅", "30", "CNY", "MONTHLY",
                        "UNKNOWN", false, "ACTIVE", 1L,
                        USER_ID, now, USER_ID, now, false);
                case "trk_wish" -> jdbc.update(
                        """
                        INSERT INTO trk_wish (
                            id, user_id, category_id, name,
                            expected_price, expected_years,
                            watch_reminder_enabled, status,
                            version, create_by, create_time,
                            update_by, update_time, del_flag
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  ?, ?, ?, ?, ?)
                        """,
                        BUSINESS_ID, USER_ID, categoryId,
                        "想买", "5000", "3", false,
                        "CONSIDERING", 1L, USER_ID, now,
                        USER_ID, now, false);
                default -> throw new IllegalStateException(
                        "不支持的业务表：" + table);
            }
        }
    }

    private enum WriteKind {
        CREATE,
        UPDATE,
        RESTORE
    }

    private enum AcceptedRestore {
        ITEM("trk_item", "ITEM_RESTORE") {
            @Override
            void restore(
                    ItemService itemService,
                    SubscriptionService subscriptionService,
                    WishService wishService,
                    String restoreToken) {
                itemService.restore(
                        BUSINESS_ID, 1L, restoreToken);
            }
        },
        SUBSCRIPTION(
                "trk_subscription", "SUB_RESTORE") {
            @Override
            void restore(
                    ItemService itemService,
                    SubscriptionService subscriptionService,
                    WishService wishService,
                    String restoreToken) {
                subscriptionService.restore(
                        BUSINESS_ID, 1L, restoreToken);
            }
        },
        WISH("trk_wish", "WISH_RESTORE") {
            @Override
            void restore(
                    ItemService itemService,
                    SubscriptionService subscriptionService,
                    WishService wishService,
                    String restoreToken) {
                wishService.restore(
                        BUSINESS_ID, 1L, restoreToken);
            }
        };

        private final String table;
        private final String operationCode;

        AcceptedRestore(
                String table, String operationCode) {
            this.table = table;
            this.operationCode = operationCode;
        }

        String table() {
            return table;
        }

        String operationCode() {
            return operationCode;
        }

        void prepare(
                JdbcTemplate jdbc,
                long categoryId,
                LocalDateTime deletedAt) {
            ReferenceWrite referenceWrite = switch (this) {
                case ITEM -> ReferenceWrite.ITEM_RESTORE;
                case SUBSCRIPTION ->
                        ReferenceWrite.SUBSCRIPTION_RESTORE;
                case WISH -> ReferenceWrite.WISH_RESTORE;
            };
            referenceWrite.prepare(
                    jdbc, categoryId, categoryId);
            jdbc.update(
                    "UPDATE " + table
                            + " SET delete_time = ?"
                            + " WHERE id = ?",
                    deletedAt,
                    BUSINESS_ID);
        }

        abstract void restore(
                ItemService itemService,
                SubscriptionService subscriptionService,
                WishService wishService,
                String restoreToken);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUserConfiguration {

        @Bean
        @Primary
        CurrentUserProvider fixedCurrentUserProvider() {
            return () -> new UserContext(USER_ID);
        }

        @Bean
        @Primary
        Clock mutableTrackingClock() {
            return new MutableTrackingClock(BUSINESS_ZONE);
        }
    }

    private static final class MutableTrackingClock extends Clock {

        private final ZoneId zone;

        private MutableTrackingClock(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId targetZone) {
            return new MutableTrackingClock(targetZone);
        }

        @Override
        public Instant instant() {
            return CURRENT_INSTANT.get();
        }
    }
}
