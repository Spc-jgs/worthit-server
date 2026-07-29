package com.shaopc.worthit.tracking.dashboard.infrastructure;

import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.dashboard.application.DashboardResult;
import com.shaopc.worthit.tracking.dashboard.application.DashboardService;
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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(DashboardPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class DashboardPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 2002L;
    private static final AtomicLong CURRENT_USER =
            new AtomicLong(USER_ID);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 29, 16, 0);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private DashboardService dashboardService;

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
    void clearTrackingData() {
        CURRENT_USER.set(USER_ID);
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_category");
        insertCategory(101L, USER_ID);
        insertCategory(201L, OTHER_USER_ID);
    }

    @Test
    void aggregatesGoldHomeAndFiltersByStateDeleteAndUser() {
        insertItem(
                1001L, USER_ID, 101L,
                "1000", "1", null,
                "HOLDING", false);
        insertItem(
                1002L, USER_ID, 101L,
                "500", "1", null,
                "SOLD", false);
        insertItem(
                1003L, USER_ID, 101L,
                "600", "1", null,
                "HOLDING", true);
        insertItem(
                2001L, OTHER_USER_ID, 201L,
                "9999", "1", null,
                "HOLDING", false);

        insertSubscription(
                3001L, USER_ID, 101L,
                "20", "USD", "MONTHLY", null,
                "140", "ACTIVE", false);
        insertSubscription(
                3002L, USER_ID, 101L,
                "120", "CNY", "YEARLY", null,
                null, "ACTIVE", false);
        insertSubscription(
                3003L, USER_ID, 101L,
                "30", "EUR", "MONTHLY", null,
                null, "ACTIVE", false);
        insertSubscription(
                3004L, USER_ID, 101L,
                "999", "CNY", "MONTHLY", null,
                null, "PAUSED", false);
        insertSubscription(
                3005L, USER_ID, 101L,
                "888", "CNY", "MONTHLY", null,
                null, "ACTIVE", true);
        insertSubscription(
                4001L, OTHER_USER_ID, 201L,
                "777", "CNY", "MONTHLY", null,
                null, "ACTIVE", false);

        insertWish(
                5001L, USER_ID, 101L,
                "1000", "CONSIDERING", false);
        insertWish(
                5002L, USER_ID, 101L,
                "5000", "ABANDONED", false);
        insertWish(
                5003L, USER_ID, 101L,
                "3000", "CONSIDERING", true);
        insertWish(
                6001L, OTHER_USER_ID, 201L,
                "9999", "CONSIDERING", false);

        DashboardResult result = dashboardService.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("2.74");
        assertThat(result.itemResidualUnsetCount())
                .isEqualTo(1);
        assertThat(result.subscriptionMonthlyCnyTotal())
                .isEqualTo("150.00");
        assertThat(result.subscriptionMonthlyCnyTotalDisplay())
                .isEqualTo("约 ¥150.00/月");
        assertThat(result.subscriptionMonthlyCnyApproximate())
                .isTrue();
        assertThat(result.subscriptionUnconvertedForeignCount())
                .isEqualTo(1);
        assertThat(result.wishConsideringCount())
                .isEqualTo(1);
        assertThat(result.wishConsideringAmountTotal())
                .isEqualTo("1000.00");
    }

    @Test
    void sumsPreciseItemFactsBeforeRounding() {
        insertItem(
                1001L, USER_ID, 101L,
                "1", "1", null,
                "HOLDING", false);
        insertItem(
                1002L, USER_ID, 101L,
                "1", "1", null,
                "HOLDING", false);

        DashboardResult result = dashboardService.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("0.01");
        assertThat(result.itemPlanDailyTotalDisplay())
                .isEqualTo("¥0.01/天");
        assertThat(result.itemResidualUnsetCount())
                .isEqualTo(2);
    }

    @Test
    void returnsZeroContractWhenCurrentUserHasNoFacts() {
        CURRENT_USER.set(3003L);

        DashboardResult result = dashboardService.summary();

        assertThat(result.itemPlanDailyTotal())
                .isEqualTo("0.00");
        assertThat(result.subscriptionMonthlyCnyTotal())
                .isEqualTo("0.00");
        assertThat(result.wishConsideringAmountTotal())
                .isEqualTo("0.00");
        assertThat(result.itemResidualUnsetCount()).isZero();
        assertThat(result.subscriptionUnconvertedForeignCount())
                .isZero();
        assertThat(result.wishConsideringCount()).isZero();
    }

    private void insertCategory(long id, long userId) {
        jdbcTemplate.update("""
                INSERT INTO trk_category (
                    id, user_id, name, version,
                    create_by, create_time,
                    update_by, update_time,
                    del_flag, delete_time)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, 0, NULL)
                """,
                id, userId, "未分类-" + userId,
                userId, NOW, userId, NOW);
    }

    private void insertItem(
            long id,
            long userId,
            long categoryId,
            String purchasePrice,
            String expectedYears,
            String residualValue,
            String status,
            boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO trk_item (
                    id, user_id, category_id, name,
                    purchase_price, expected_years,
                    residual_value, lifecycle_status,
                    version, create_by, create_time,
                    update_by, update_time,
                    del_flag, delete_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                """,
                id, userId, categoryId, "物品-" + id,
                purchasePrice, expectedYears, residualValue,
                status, userId, NOW, userId, NOW,
                deleted, deleted ? NOW : null);
    }

    private void insertSubscription(
            long id,
            long userId,
            long categoryId,
            String amount,
            String currency,
            String cycleType,
            Integer cycleValue,
            String cnyReferenceAmount,
            String status,
            boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO trk_subscription (
                    id, user_id, category_id, name,
                    amount, currency, billing_cycle_type,
                    billing_cycle_value, cny_reference_amount,
                    status, version, create_by, create_time,
                    update_by, update_time,
                    del_flag, delete_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                """,
                id, userId, categoryId, "订阅-" + id,
                amount, currency, cycleType, cycleValue,
                cnyReferenceAmount, status,
                userId, NOW, userId, NOW,
                deleted, deleted ? NOW : null);
    }

    private void insertWish(
            long id,
            long userId,
            long categoryId,
            String expectedPrice,
            String status,
            boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO trk_wish (
                    id, user_id, category_id, name,
                    expected_price, expected_years,
                    status, version, create_by, create_time,
                    update_by, update_time,
                    del_flag, delete_time)
                VALUES (?, ?, ?, ?, ?, 1, ?, 1, ?, ?, ?, ?, ?, ?)
                """,
                id, userId, categoryId, "想买-" + id,
                expectedPrice, status,
                userId, NOW, userId, NOW,
                deleted, deleted ? NOW : null);
    }

    @TestConfiguration
    static class FixedContext {

        @Bean
        @Primary
        CurrentUserProvider currentUserProvider() {
            return () -> new UserContext(CURRENT_USER.get());
        }
    }
}
