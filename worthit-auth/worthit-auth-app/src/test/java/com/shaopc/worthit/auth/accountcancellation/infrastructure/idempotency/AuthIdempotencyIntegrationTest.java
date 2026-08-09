package com.shaopc.worthit.auth.accountcancellation.infrastructure.idempotency;

import com.shaopc.worthit.auth.WorthItAuthApplication;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthCancellationOperation;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthIdempotencyExecutor;
import com.shaopc.worthit.common.core.error.BusinessException;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        classes = WorthItAuthApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.security.same-token.rotation.enabled=false",
            "worthit.account-cancellation.enabled=false",
            "worthit.auth.wechat.app-id=wx-app",
            "worthit.auth.wechat.app-secret=test-only-secret",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class AuthIdempotencyIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AuthIdempotencyExecutor executor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM auth_idempotency_record");
    }

    @Test
    void replaysSuccessAndRejectsDifferentRequestHash() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        TestResponse first = execute(key, HASH_A, () -> {
            executions.incrementAndGet();
            return new TestResponse("first");
        });
        TestResponse replay = execute(key, HASH_A, () -> {
            executions.incrementAndGet();
            return new TestResponse("second");
        });

        assertThat(first).isEqualTo(new TestResponse("first"));
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
        assertThatThrownBy(() -> execute(
                key, HASH_B, () -> new TestResponse("conflict")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("IDEM_CONFLICT"));
    }

    @Test
    void reportsActiveLeaseAndReclaimsExpiredLease() throws Exception {
        String key = UUID.randomUUID().toString();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> execute(key, HASH_A, () -> {
            started.countDown();
            await(release);
            return new TestResponse("first");
        }));
        first.start();
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> execute(
                key, HASH_A, () -> new TestResponse("second")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("IDEM_IN_PROGRESS"));
        release.countDown();
        first.join(10_000L);
        assertThat(first.isAlive()).isFalse();

        String retryKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> execute(retryKey, HASH_A, () -> {
            throw new IllegalStateException("temporary");
        })).isInstanceOf(IllegalStateException.class);
        jdbcTemplate.update(
                """
                UPDATE auth_idempotency_record
                   SET processing_expire_at = ?
                 WHERE user_id = ? AND idempotency_key = ?
                """,
                LocalDateTime.of(2000, 1, 1, 0, 0),
                USER_ID,
                retryKey);

        assertThat(execute(
                retryKey, HASH_A, () -> new TestResponse("retried")))
                .isEqualTo(new TestResponse("retried"));
    }

    private TestResponse execute(
            String key,
            String hash,
            AuthIdempotencyExecutor.IdempotentAction<TestResponse> action) {
        return executor.execute(
                USER_ID,
                AuthCancellationOperation.APPLY,
                key,
                hash,
                TestResponse.class,
                action);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发测试超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }

    record TestResponse(String value) {
    }
}
