package com.shaopc.worthit.tracking.item.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.item.application.CreateItemCommand;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemService;
import com.shaopc.worthit.tracking.item.application.ItemSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(ItemPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class ItemPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 26);

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ItemService itemService;

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
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update(
                "DELETE FROM trk_idempotency_record");
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

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class);
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
            return () -> new UserContext(USER_ID);
        }

        @Bean
        @Primary
        Clock fixedTrackingClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-26T04:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }
    }
}
