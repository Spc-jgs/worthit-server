package com.shaopc.worthit.tracking.subscription.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.application.CreateSubscriptionCommand;
import com.shaopc.worthit.tracking.subscription.application.DeleteSubscriptionResult;
import com.shaopc.worthit.tracking.subscription.application.ResumeSubscriptionCommand;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionDetail;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionService;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionSummary;
import com.shaopc.worthit.tracking.subscription.application.UpdateSubscriptionCommand;
import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(SubscriptionPersistenceIntegrationTest.FixedContext.class)
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class SubscriptionPersistenceIntegrationTest {

    private static final long USER_ID = 3001L;
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 28);
    private static final AtomicLong CURRENT_USER =
            new AtomicLong(USER_ID);
    private static final AtomicReference<Instant> CURRENT_INSTANT =
            new AtomicReference<>(
                    Instant.parse("2026-07-28T04:00:00Z"));

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private SubscriptionService service;

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
                "spring.datasource.username",
                MYSQL::getUsername);
        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearTrackingData() {
        CURRENT_USER.set(USER_ID);
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T04:00:00Z"));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");
        jdbcTemplate.update(
                "DELETE FROM trk_idempotency_record");
        jdbcTemplate.update("DELETE FROM trk_subscription");
        jdbcTemplate.update("DELETE FROM trk_item");
        jdbcTemplate.update("DELETE FROM trk_wish");
        jdbcTemplate.update("DELETE FROM trk_category");
    }

    @Test
    void createsMonthlyForeignSubscriptionIdempotently()
            throws Exception {
        String key = UUID.randomUUID().toString();
        CreateSubscriptionCommand command = command(
                "ChatGPT Plus",
                "20",
                "USD",
                BillingCycleType.MONTHLY,
                null,
                null,
                null,
                null);

        SubscriptionDetail first =
                service.create(key, command);
        SubscriptionDetail replay =
                service.create(key, command);

        assertThat(replay).isEqualTo(first);
        assertThat(first.categoryName())
                .isEqualTo("未分类");
        assertThat(first.amount())
                .isEqualTo("20.000000");
        assertThat(first.originalMonthlyCost())
                .isEqualTo("20.00");
        assertThat(first.originalMonthlyCostDisplay())
                .isEqualTo("20.00 USD/月");
        assertThat(first.cnyMonthlyCost()).isNull();
        assertThat(first.includeInCnyTotal()).isFalse();
        assertThat(first.renewalReminderEnabled())
                .isFalse();
        assertThat(first.status()).isEqualTo("ACTIVE");
        assertThat(count("trk_subscription")).isEqualTo(1);
        assertThat(count("trk_outbox_event")).isZero();
        assertThat(count("trk_idempotency_record"))
                .isEqualTo(1);

        assertThatThrownBy(() -> service.create(
                key,
                command(
                        "Different",
                        "20",
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("IDEM_CONFLICT"));
    }

    @Test
    void createsRenewalExpectationWithFrozenTiming()
            throws Exception {
        SubscriptionDetail detail = service.create(
                UUID.randomUUID().toString(),
                command(
                        "云服务",
                        "120",
                        "CNY",
                        BillingCycleType.YEARLY,
                        null,
                        null,
                        TODAY.plusDays(13),
                        null));

        assertThat(detail.originalMonthlyCostDisplay())
                .isEqualTo("¥10.00/月");
        assertThat(detail.cnyMonthlyCostDisplay())
                .isEqualTo("¥10.00/月");
        assertThat(detail.includeInCnyTotal()).isTrue();
        assertThat(detail.renewalReminderEnabled())
                .isTrue();
        assertThat(count("trk_outbox_event")).isEqualTo(1);

        JsonNode payload = latestOutboxPayload();
        assertThat(payload.path("businessType").asText())
                .isEqualTo("SUBSCRIPTION");
        assertThat(payload.path("reminderType").asText())
                .isEqualTo("RENEWAL");
        assertThat(payload.path("sourceVersion").asLong())
                .isEqualTo(1);
        assertThat(payload.path("businessDate").asText())
                .isEqualTo("2026-08-10");
        assertThat(payload.path("remindAt").asText())
                .isEqualTo("2026-08-09T00:00:00");
        assertThat(payload.path("reminderEnabled").asBoolean())
                .isTrue();
        assertThat(payload.path("operationType").asText())
                .isEqualTo("INITIAL_SYNC");
    }

    @Test
    void detailAndFilteredListAreUserIsolated() {
        SubscriptionDetail visible = service.create(
                UUID.randomUUID().toString(),
                command(
                        "ChatGPT Plus",
                        "20",
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        "140",
                        null,
                        false));

        PageResult<SubscriptionSummary> matching =
                service.list(1, 20, "Chat", null);
        PageResult<SubscriptionSummary> missing =
                service.list(1, 20, "Music", null);

        assertThat(service.detail(visible.id()))
                .isEqualTo(visible);
        assertThat(matching.getItems())
                .extracting(SubscriptionSummary::id)
                .containsExactly(visible.id());
        assertThat(missing.getItems()).isEmpty();

        CURRENT_USER.set(4002L);
        assertThatThrownBy(() -> service.detail(visible.id()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RES_NOT_FOUND"));
    }

    @Test
    void validatesCycleCurrencyReferenceAndReminderRules() {
        assertInvalid(command(
                "错误月付",
                "20",
                "CNY",
                BillingCycleType.MONTHLY,
                12,
                null,
                null,
                false));
        assertInvalid(command(
                "错误多月",
                "20",
                "CNY",
                BillingCycleType.MULTI_MONTH,
                null,
                null,
                null,
                false));
        assertInvalid(command(
                "人民币参考",
                "20",
                "CNY",
                BillingCycleType.MONTHLY,
                null,
                "20",
                null,
                false));
        assertInvalid(command(
                "缺少日期",
                "20",
                "USD",
                BillingCycleType.MONTHLY,
                null,
                null,
                null,
                true));
        assertThat(count("trk_subscription")).isZero();
    }

    @Test
    void updateUsesVersionAndReconcilesChangedReminder()
            throws Exception {
        SubscriptionDetail created = service.create(
                UUID.randomUUID().toString(),
                command(
                        "ChatGPT Plus",
                        "20",
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        TODAY.plusDays(13),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        String key = UUID.randomUUID().toString();
        UpdateSubscriptionCommand update =
                new UpdateSubscriptionCommand(
                        created.version(),
                        "ChatGPT Plus",
                        created.categoryId(),
                        new BigDecimal("20"),
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        new BigDecimal("140"),
                        TODAY.plusDays(20),
                        AutoRenew.YES,
                        true,
                        "工作使用");
        SubscriptionDetail changed = service.update(
                created.id(), key, update);
        SubscriptionDetail replay = service.update(
                created.id(), key, update);

        assertThat(replay).isEqualTo(changed);
        assertThat(changed.version()).isEqualTo(2);
        assertThat(changed.cnyMonthlyCostDisplay())
                .isEqualTo("约 ¥140.00/月");
        assertThat(changed.cnyApproximate()).isTrue();
        assertThat(latestOutboxPayload()
                .path("operationType").asText())
                .isEqualTo("UPDATE_BUSINESS_DATE");

        assertStateConflict(() -> service.update(
                created.id(),
                UUID.randomUUID().toString(),
                new UpdateSubscriptionCommand(
                        1,
                        "过期更新",
                        created.categoryId(),
                        new BigDecimal("20"),
                        "USD",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        TODAY.plusDays(21),
                        AutoRenew.UNKNOWN,
                        true,
                        null)));
        assertThat(count("trk_outbox_event")).isEqualTo(1);
    }

    @Test
    void pauseEndAndResumeFollowFrozenStateRules()
            throws Exception {
        SubscriptionDetail created = service.create(
                UUID.randomUUID().toString(),
                command(
                        "视频会员",
                        "30",
                        "CNY",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        TODAY.plusDays(5),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        SubscriptionDetail paused = service.pause(
                created.id(),
                created.version(),
                UUID.randomUUID().toString());
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertOutbox("PAUSE_SUBSCRIPTION", false, 2);

        SubscriptionDetail resumed = service.resume(
                created.id(),
                UUID.randomUUID().toString(),
                new ResumeSubscriptionCommand(
                        paused.version(), null, null));
        assertThat(resumed.status()).isEqualTo("ACTIVE");
        assertThat(resumed.nextRenewalDate())
                .isEqualTo(TODAY.plusDays(5));
        assertOutbox("RESUME_SUBSCRIPTION", true, 3);

        SubscriptionDetail ended = service.end(
                created.id(),
                resumed.version(),
                UUID.randomUUID().toString());
        assertThat(ended.status()).isEqualTo("ENDED");
        assertOutbox("END_SUBSCRIPTION", false, 4);

        CURRENT_INSTANT.set(
                Instant.parse("2026-08-03T04:00:00Z"));
        assertStateConflict(() -> service.resume(
                created.id(),
                UUID.randomUUID().toString(),
                new ResumeSubscriptionCommand(
                        ended.version(), null, null)));

        SubscriptionDetail disabledResume = service.resume(
                created.id(),
                UUID.randomUUID().toString(),
                new ResumeSubscriptionCommand(
                        ended.version(), null, false));
        assertThat(disabledResume.status())
                .isEqualTo("ACTIVE");
        assertThat(disabledResume.renewalReminderEnabled())
                .isFalse();
        assertOutbox("RESUME_SUBSCRIPTION", false, 5);
    }

    @Test
    void deleteAndRestoreAreIdempotentWithoutReminderRevival()
            throws Exception {
        SubscriptionDetail created = service.create(
                UUID.randomUUID().toString(),
                command(
                        "云盘",
                        "12",
                        "CNY",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        TODAY.plusDays(10),
                        true));
        jdbcTemplate.update("DELETE FROM trk_outbox_event");

        String deleteKey = UUID.randomUUID().toString();
        DeleteSubscriptionResult deleted = service.delete(
                created.id(),
                created.version(),
                deleteKey);
        DeleteSubscriptionResult replayDelete = service.delete(
                created.id(),
                created.version(),
                deleteKey);

        assertThat(replayDelete).isEqualTo(deleted);
        assertThat(deleted.restoreDeadline()).isEqualTo(
                LocalDateTime.of(
                        2026, 7, 28, 12, 1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_subscription "
                        + "WHERE id = ?",
                Boolean.class,
                created.id())).isTrue();
        assertOutbox("DELETE_OBJECT", false, 2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operation_code "
                        + "FROM trk_idempotency_record "
                        + "WHERE idempotency_key = ?",
                String.class,
                deleted.restoreToken()))
                .isEqualTo("SUB_RESTORE");

        SubscriptionDetail restored = service.restore(
                created.id(), 2, deleted.restoreToken());
        SubscriptionDetail replayRestore = service.restore(
                created.id(), 2, deleted.restoreToken());

        assertThat(replayRestore).isEqualTo(restored);
        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored.status()).isEqualTo("ACTIVE");
        assertThat(count("trk_outbox_event")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT del_flag FROM trk_subscription "
                        + "WHERE id = ?",
                Boolean.class,
                created.id())).isFalse();
    }

    @Test
    void restoreRejectsWrongTokenVersionExpiryAndOtherUser() {
        SubscriptionDetail created = service.create(
                UUID.randomUUID().toString(),
                command(
                        "音乐会员",
                        "10",
                        "CNY",
                        BillingCycleType.MONTHLY,
                        null,
                        null,
                        null,
                        false));
        DeleteSubscriptionResult deleted = service.delete(
                created.id(),
                created.version(),
                UUID.randomUUID().toString());

        assertStateConflict(() -> service.restore(
                created.id(),
                2,
                UUID.randomUUID().toString()));
        assertStateConflict(() -> service.restore(
                created.id(), 3, deleted.restoreToken()));

        CURRENT_USER.set(4002L);
        assertThatThrownBy(() -> service.restore(
                created.id(), 2, deleted.restoreToken()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RES_NOT_FOUND"));

        CURRENT_USER.set(USER_ID);
        CURRENT_INSTANT.set(
                Instant.parse("2026-07-28T04:01:01Z"));
        assertStateConflict(() -> service.restore(
                created.id(), 2, deleted.restoreToken()));
    }

    private JsonNode latestOutboxPayload() throws Exception {
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload_json "
                        + "FROM trk_outbox_event "
                        + "ORDER BY id DESC LIMIT 1",
                String.class);
        return objectMapper.readTree(payload);
    }

    private void assertOutbox(
            String operationType,
            boolean enabled,
            long sourceVersion) throws Exception {
        JsonNode payload = latestOutboxPayload();
        assertThat(payload.path("operationType").asText())
                .isEqualTo(operationType);
        assertThat(payload.path("reminderEnabled").asBoolean())
                .isEqualTo(enabled);
        assertThat(payload.path("sourceVersion").asLong())
                .isEqualTo(sourceVersion);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class);
    }

    private void assertInvalid(
            CreateSubscriptionCommand command) {
        assertThatThrownBy(() -> service.create(
                UUID.randomUUID().toString(), command))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "VAL_INVALID_ARGUMENT"));
    }

    private static void assertStateConflict(
            org.assertj.core.api.ThrowableAssert
                    .ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "VAL_STATE_CONFLICT"));
    }

    private static CreateSubscriptionCommand command(
            String name,
            String amount,
            String currency,
            BillingCycleType cycleType,
            Integer cycleValue,
            String cnyReference,
            LocalDate renewalDate,
            Boolean reminderEnabled) {
        return new CreateSubscriptionCommand(
                name,
                null,
                new BigDecimal(amount),
                currency,
                cycleType,
                cycleValue,
                cnyReference == null
                        ? null
                        : new BigDecimal(cnyReference),
                renewalDate,
                AutoRenew.UNKNOWN,
                reminderEnabled,
                null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedContext {

        @Bean
        @Primary
        CurrentUserProvider fixedCurrentUserProvider() {
            return () -> new UserContext(
                    CURRENT_USER.get());
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
