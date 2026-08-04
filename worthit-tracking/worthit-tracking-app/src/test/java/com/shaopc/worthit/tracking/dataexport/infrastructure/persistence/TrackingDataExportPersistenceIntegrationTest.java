package com.shaopc.worthit.tracking.dataexport.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import com.shaopc.worthit.tracking.dataexport.application.TrackingDataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class TrackingDataExportPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 4, 10, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TrackingDataExportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update("DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_item_replacement");
        jdbcTemplate.update("DELETE FROM trk_item_disposal");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_category");
    }

    @Test
    void exportsAllOwnedBusinessRowsIncludingLogicalDeletesInIdOrder()
            throws Exception {
        insertCategory(102L, USER_ID, "已删除分类", true);
        insertCategory(101L, USER_ID, "活动分类", false);
        insertCategory(999L, 2002L, "其他用户分类", false);
        insertItem();
        insertSubscription();
        insertWish();
        insertDisposal();
        insertReplacement();

        TrackingDataExportResponse response = service.exportUserData(USER_ID);

        assertThat(response.userId()).isEqualTo("1001");
        assertThat(response.categories())
                .extracting(TrackingDataExportResponse.Category::id)
                .containsExactly("101", "102");
        assertThat(response.categories().get(1).deleted()).isTrue();
        assertThat(response.items()).extracting(
                        TrackingDataExportResponse.Item::id)
                .containsExactly("201");
        assertThat(response.items().get(0).deleted()).isTrue();
        assertThat(response.subscriptions()).hasSize(1);
        assertThat(response.wishes()).hasSize(1);
        assertThat(response.disposals()).hasSize(1);
        assertThat(response.replacements()).hasSize(1);

        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .doesNotContain(
                        "conversion-key-secret",
                        "createBy",
                        "updateBy",
                        "lastError",
                        "其他用户分类");
    }

    @Test
    void rejectsTheTenThousandAndFirstOwnedRecord() {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO trk_category (
                    id, user_id, name, system_code, version,
                    create_time, update_time, del_flag, delete_time
                ) VALUES (?, ?, ?, NULL, 1, ?, ?, 0, NULL)
                """,
                java.util.stream.LongStream.rangeClosed(1, 10_001)
                        .boxed()
                        .toList(),
                1000,
                (statement, id) -> {
                    statement.setLong(1, id);
                    statement.setLong(2, USER_ID);
                    statement.setString(3, "分类" + id);
                    statement.setTimestamp(4, Timestamp.valueOf(NOW));
                    statement.setTimestamp(5, Timestamp.valueOf(NOW));
                });

        assertThatThrownBy(() -> service.exportUserData(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_LIMIT_EXCEEDED"));
    }

    private void insertCategory(
            long id, long userId, String name, boolean deleted) {
        jdbcTemplate.update(
                """
                INSERT INTO trk_category (
                    id, user_id, name, system_code, version, create_by,
                    create_time, update_by, update_time, del_flag, delete_time
                ) VALUES (?, ?, ?, NULL, 1, 9001, ?, 9002, ?, ?, ?)
                """,
                id, userId, name, NOW, NOW,
                deleted ? 1 : 0, deleted ? NOW : null);
    }

    private void insertItem() {
        jdbcTemplate.update(
                """
                INSERT INTO trk_item (
                    id, user_id, category_id, name, purchase_price,
                    expected_years, residual_value, purchase_date,
                    warranty_expire_date, warranty_reminder_enabled,
                    brand_model, remark, source_wish_id, lifecycle_status,
                    version, create_by, create_time, update_by, update_time,
                    del_flag, delete_time
                ) VALUES (
                    201, ?, 101, '相机', 5000.000000, 3.000, 500.000000,
                    '2025-01-01', NULL, 0, 'X1', '用户备注', NULL,
                    'SOLD', 2, 9001, ?, 9002, ?, 1, ?)
                """,
                USER_ID, NOW, NOW, NOW);
    }

    private void insertSubscription() {
        jdbcTemplate.update(
                """
                INSERT INTO trk_subscription (
                    id, user_id, category_id, name, amount, currency,
                    billing_cycle_type, billing_cycle_value,
                    cny_reference_amount, next_renewal_date, auto_renew,
                    renewal_reminder_enabled, status, remark, version,
                    create_time, update_time, del_flag, delete_time
                ) VALUES (
                    301, ?, 101, '云服务', 12.300000, 'CNY', 'MONTHLY',
                    NULL, NULL, NULL, 'UNKNOWN', 0, 'PAUSED', '暂停中', 3,
                    ?, ?, 0, NULL)
                """,
                USER_ID, NOW, NOW);
    }

    private void insertWish() {
        jdbcTemplate.update(
                """
                INSERT INTO trk_wish (
                    id, user_id, category_id, name, expected_price,
                    expected_years, residual_value, reason, remark,
                    watch_deadline, watch_reminder_enabled, status,
                    last_abandon_reason, last_abandon_at, converted_item_id,
                    conversion_key, version, create_time, update_time,
                    del_flag, delete_time
                ) VALUES (
                    401, ?, 101, '镜头', 2000.000000, 5.000, NULL,
                    '拍摄', '用户愿望', NULL, 0, 'ABANDONED', '预算调整',
                    ?, NULL, 'conversion-key-secret', 4, ?, ?, 0, NULL)
                """,
                USER_ID, NOW, NOW, NOW);
    }

    private void insertDisposal() {
        jdbcTemplate.update(
                """
                INSERT INTO trk_item_disposal (
                    id, user_id, item_id, disposal_type, disposal_date,
                    purchase_price_snapshot, sale_amount, remark,
                    create_time, update_time
                ) VALUES (501, ?, 201, 'SOLD', '2026-08-01',
                          5000.000000, 3200.000000, '已售', ?, ?)
                """,
                USER_ID, NOW, NOW);
    }

    private void insertReplacement() {
        jdbcTemplate.update(
                """
                INSERT INTO trk_item_replacement (
                    id, user_id, old_item_id, new_item_id, create_time
                ) VALUES (601, ?, 201, 202, ?)
                """,
                USER_ID, NOW);
    }
}
