package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.category.application.CurrentUserProvider;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(CategoryPersistenceIntegrationTest.TestUserConfiguration.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
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
    }
}
