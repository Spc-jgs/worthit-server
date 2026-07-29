package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
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

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryReferenceResolver categoryReferenceResolver;

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
        awaitCategoryLockWaiters(2);

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

    private void awaitCategoryLockWaiters(int expected) {
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
                      AND requested.object_name = 'trk_category'
                    """,
                    Integer.class);
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
                        LocalDateTime.now().minusSeconds(30));
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
            LocalDateTime now = LocalDateTime.now();
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUserConfiguration {

        @Bean
        @Primary
        CurrentUserProvider fixedCurrentUserProvider() {
            return () -> new UserContext(USER_ID);
        }
    }
}
