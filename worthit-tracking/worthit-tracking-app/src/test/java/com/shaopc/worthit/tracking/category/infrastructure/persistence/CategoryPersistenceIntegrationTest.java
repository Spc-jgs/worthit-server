package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(CategoryPersistenceIntegrationTest.TestUserConfiguration.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class CategoryPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock trackingClock;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add(
                "spring.datasource.username", MYSQL::getUsername);
        registry.add(
                "spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearTrackingData() {
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_category");
    }

    @Test
    void duplicateNameConflictsAndCanBeRecreatedAfterLogicalDelete() {
        Category created = categoryService.create("数码");

        assertThatThrownBy(() -> categoryService.create("数码"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CONFLICT"));

        categoryService.delete(created.id());
        Category recreated = categoryService.create("数码");

        assertThat(recreated.id()).isNotEqualTo(created.id());
        assertThat(categoryService.list())
                .extracting(Category::id)
                .containsExactly(recreated.id());
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM trk_category
                WHERE user_id = ? AND name = ?
                """,
                Integer.class,
                USER_ID,
                "数码")).isEqualTo(2);
    }

    @Test
    void renamesCustomCategoryAndIncrementsVersion() {
        Category created = categoryService.create("数码");

        Category renamed = categoryService.rename(
                created.id(), "  办公设备  ");

        CategoryDO persisted = categoryMapper.selectById(created.id());
        assertThat(renamed.name()).isEqualTo("办公设备");
        assertThat(persisted.getName()).isEqualTo("办公设备");
        assertThat(persisted.getVersion()).isEqualTo(2L);
        assertThat(persisted.getUpdateBy()).isEqualTo(USER_ID);
        assertThat(persisted.getUpdateTime())
                .isEqualTo(LocalDateTime.now(trackingClock));
    }

    @Test
    void renameToExistingActiveNameConflictsAndKeepsOriginalName() {
        Category source = categoryService.create("数码");
        categoryService.create("办公");

        assertThatThrownBy(() -> categoryService.rename(
                source.id(), "办公"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CONFLICT"));

        assertThat(categoryMapper.selectById(source.id()).getName())
                .isEqualTo("数码");
    }

    @Test
    void concurrentRenamesToSameNameAllowExactlyOneWinner()
            throws Exception {
        Category first = categoryService.create("数码");
        Category second = categoryService.create("办公");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RenameResult> firstResult = executor.submit(
                    () -> renameAfterBarrier(first.id(), ready, start));
            Future<RenameResult> secondResult = executor.submit(
                    () -> renameAfterBarrier(second.id(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RenameResult> results = List.of(
                    firstResult.get(5, TimeUnit.SECONDS),
                    secondResult.get(5, TimeUnit.SECONDS));

            assertThat(results).filteredOn(RenameResult::succeeded)
                    .hasSize(1);
            assertThat(results).filteredOn(result -> !result.succeeded())
                    .singleElement()
                    .extracting(RenameResult::failure)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo("BIZ_CONFLICT"));
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM trk_category
                    WHERE user_id = ?
                      AND name = ?
                      AND del_flag = 0
                    """,
                    Integer.class,
                    USER_ID,
                    "统一名称")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(
                    5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void systemCategoryCannotBeRenamed() {
        CategoryDO systemCategory =
                insertCategory("未分类", "UNCATEGORIZED");

        assertThatThrownBy(() -> categoryService.rename(
                systemCategory.getId(), "其他"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "BIZ_CATEGORY_SYSTEM_PROTECTED"));

        assertThat(categoryMapper.selectById(systemCategory.getId())
                .getName()).isEqualTo("未分类");
    }

    @Test
    void systemCategoryCannotBeDeleted() {
        CategoryDO systemCategory =
                insertCategory("未分类", "UNCATEGORIZED");

        assertThatThrownBy(
                () -> categoryService.delete(systemCategory.getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "BIZ_CATEGORY_SYSTEM_PROTECTED"));

        assertThat(categoryMapper.selectById(systemCategory.getId())
                .getDelFlag()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ITEM", "SUBSCRIPTION", "WISH"})
    void activeBusinessReferencePreventsCategoryDeletion(
            String businessType) {
        Category category = categoryService.create("数码");
        insertBusinessReference(businessType, category.id());

        assertThatThrownBy(() -> categoryService.delete(category.id()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CATEGORY_IN_USE"));

        assertThat(categoryMapper.selectById(category.id())
                .getDelFlag()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ITEM", "SUBSCRIPTION", "WISH"})
    void restorableDeletedReferencePreventsCategoryDeletion(
            String businessType) {
        Category category = categoryService.create("数码");
        insertBusinessReference(businessType, category.id());
        markBusinessReferenceDeleted(
                businessType,
                LocalDateTime.now(trackingClock).minusSeconds(30));

        assertThatThrownBy(() -> categoryService.delete(category.id()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CATEGORY_IN_USE"));

        assertThat(categoryMapper.selectById(category.id())
                .getDelFlag()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ITEM", "SUBSCRIPTION", "WISH"})
    void expiredDeletedReferenceDoesNotPreventCategoryDeletion(
            String businessType) {
        Category category = categoryService.create("数码");
        insertBusinessReference(businessType, category.id());
        markBusinessReferenceDeleted(
                businessType,
                LocalDateTime.now(trackingClock).minusSeconds(61));

        categoryService.delete(category.id());

        assertThat(categoryMapper.selectById(category.id())
                .getDelFlag()).isTrue();
    }

    private CategoryDO insertCategory(
            String name, String systemCode) {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 26, 12, 0);
        CategoryDO category = new CategoryDO();
        category.setUserId(USER_ID);
        category.setName(name);
        category.setSystemCode(systemCode);
        category.setVersion(1L);
        category.setCreateBy(USER_ID);
        category.setCreateTime(now);
        category.setUpdateBy(USER_ID);
        category.setUpdateTime(now);
        category.setDelFlag(false);
        categoryMapper.insert(category);
        return category;
    }

    private RenameResult renameAfterBarrier(
            long categoryId,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发重命名开始超时");
            }
            categoryService.rename(categoryId, "统一名称");
            return new RenameResult(true, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new RenameResult(false, exception);
        } catch (RuntimeException exception) {
            return new RenameResult(false, exception);
        }
    }

    private record RenameResult(boolean succeeded, Throwable failure) {
    }

    private void insertBusinessReference(
            String businessType, long categoryId) {
        switch (businessType) {
            case "ITEM" -> insertItem(categoryId);
            case "SUBSCRIPTION" -> insertSubscription(categoryId);
            case "WISH" -> insertWish(categoryId);
            default -> throw new IllegalArgumentException(
                    "不支持的业务类型：" + businessType);
        }
    }

    private void markBusinessReferenceDeleted(
            String businessType, LocalDateTime deleteTime) {
        String table = switch (businessType) {
            case "ITEM" -> "trk_item";
            case "SUBSCRIPTION" -> "trk_subscription";
            case "WISH" -> "trk_wish";
            default -> throw new IllegalArgumentException(
                    "不支持的业务类型：" + businessType);
        };
        jdbcTemplate.update(
                "UPDATE " + table
                        + " SET del_flag = 1, delete_time = ?",
                deleteTime);
    }

    private void insertItem(long categoryId) {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 26, 12, 0);
        jdbcTemplate.update(
                """
                INSERT INTO trk_item (
                    id, user_id, category_id, name,
                    purchase_price, expected_years,
                    warranty_reminder_enabled, lifecycle_status,
                    version, create_by, create_time,
                    update_by, update_time, del_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9001L,
                USER_ID,
                categoryId,
                "MacBook",
                "1000.00",
                "1",
                false,
                "HOLDING",
                1L,
                USER_ID,
                now,
                USER_ID,
                now,
                false);
    }

    private void insertSubscription(long categoryId) {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 26, 12, 0);
        jdbcTemplate.update(
                """
                INSERT INTO trk_subscription (
                    id, user_id, category_id, name,
                    amount, currency, billing_cycle_type,
                    auto_renew, renewal_reminder_enabled, status,
                    version, create_by, create_time,
                    update_by, update_time, del_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9002L,
                USER_ID,
                categoryId,
                "视频会员",
                "30.00",
                "CNY",
                "MONTHLY",
                "UNKNOWN",
                false,
                "ACTIVE",
                1L,
                USER_ID,
                now,
                USER_ID,
                now,
                false);
    }

    private void insertWish(long categoryId) {
        LocalDateTime now = LocalDateTime.of(
                2026, 7, 26, 12, 0);
        jdbcTemplate.update(
                """
                INSERT INTO trk_wish (
                    id, user_id, category_id, name,
                    expected_price, expected_years,
                    watch_reminder_enabled, status,
                    version, create_by, create_time,
                    update_by, update_time, del_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9003L,
                USER_ID,
                categoryId,
                "相机",
                "5000.00",
                "3",
                false,
                "CONSIDERING",
                1L,
                USER_ID,
                now,
                USER_ID,
                now,
                false);
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
        Clock fixedTrackingClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-29T09:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }
    }
}
