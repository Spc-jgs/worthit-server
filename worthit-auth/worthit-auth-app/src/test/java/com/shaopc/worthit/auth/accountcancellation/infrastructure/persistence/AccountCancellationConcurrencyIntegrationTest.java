package com.shaopc.worthit.auth.accountcancellation.infrastructure.persistence;

import com.shaopc.worthit.auth.WorthItAuthApplication;
import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationExecutionTransactions;
import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationService;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationResponse;
import com.shaopc.worthit.auth.authentication.application.AuthenticationResult;
import com.shaopc.worthit.auth.authentication.application.LoginTokenIssuer;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.common.core.error.BusinessException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Import(AccountCancellationConcurrencyIntegrationTest.ConcurrencyTestConfig.class)
@SpringBootTest(
        classes = WorthItAuthApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.account-cancellation.enabled=false",
            "worthit.auth.wechat.app-id=wx-app",
            "worthit.auth.wechat.app-secret=invalid-test-secret",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class AccountCancellationConcurrencyIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long CANCELLATION_ID = 9001L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime EFFECTIVE_AT =
            LocalDateTime.of(2026, 8, 16, 12, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AccountCancellationService cancellationService;

    @Autowired
    private AccountCancellationExecutionTransactions transactions;

    @Autowired
    private LoginTokenIssuer tokenIssuer;

    @Autowired
    private BlockingUserSession userSession;

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
    void resetData() {
        userSession.reset();
        jdbcTemplate.update("DELETE FROM auth_password_credential");
        jdbcTemplate.update("DELETE FROM auth_external_identity");
        jdbcTemplate.update("DELETE FROM auth_login_audit");
        jdbcTemplate.update("DELETE FROM auth_idempotency_record");
        jdbcTemplate.update("DELETE FROM auth_account_cancellation");
        jdbcTemplate.update("DELETE FROM auth_user");
        jdbcTemplate.update(
                """
                INSERT INTO auth_user (
                    id, nickname, avatar_file_id, status,
                    create_time, update_time
                ) VALUES (?, '并发用户', NULL, 'ACTIVE', ?, ?)
                """,
                USER_ID,
                EFFECTIVE_AT.minusDays(7),
                EFFECTIVE_AT.minusDays(7));
        jdbcTemplate.update(
                """
                INSERT INTO auth_account_cancellation (
                    id, user_id, apply_at, effective_at, completed_at,
                    status, revoked_at, version, create_time, update_time
                ) VALUES (?, ?, ?, ?, NULL, 'PENDING', NULL, 1, ?, ?)
                """,
                CANCELLATION_ID,
                USER_ID,
                EFFECTIVE_AT.minusDays(7),
                EFFECTIVE_AT,
                EFFECTIVE_AT.minusDays(7),
                EFFECTIVE_AT.minusDays(7));
    }

    @Test
    void revokeAndDueClaimHaveExactlyOneWinner() throws Exception {
        AccountCancellation candidate = pendingCancellation();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<AccountCancellation>> claim = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return transactions.claim(candidate, EFFECTIVE_AT);
            });
            Future<Object> revoke = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    return cancellationService.revoke(
                            UUID.randomUUID().toString(),
                            Long.toString(CANCELLATION_ID),
                            1L);
                } catch (BusinessException exception) {
                    return exception;
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Optional<AccountCancellation> claimed =
                    claim.get(10, TimeUnit.SECONDS);
            Object revoked = revoke.get(10, TimeUnit.SECONDS);

            if (claimed.isPresent()) {
                assertThat(revoked)
                        .isInstanceOfSatisfying(BusinessException.class,
                                exception -> assertThat(exception.code())
                                        .isEqualTo("VAL_STATE_CONFLICT"));
                assertThat(status()).isEqualTo("EXECUTING");
                assertThat(userStatus()).isEqualTo("CANCELLATION_EXECUTING");
            } else {
                assertThat(revoked)
                        .isInstanceOfSatisfying(AccountCancellationResponse.class,
                                response -> assertThat(response.status())
                                        .isEqualTo("REVOKED"));
                assertThat(status()).isEqualTo("REVOKED");
                assertThat(userStatus()).isEqualTo("ACTIVE");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void issuedTokenCannotEscapeClaimThatWaitsForLoginTransaction()
            throws Exception {
        userSession.blockNextLogin();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AuthenticationResult> login =
                    executor.submit(() -> tokenIssuer.issue(USER_ID, false));
            assertThat(userSession.awaitLoginEntered()).isTrue();

            Future<Optional<AccountCancellation>> claim = executor.submit(
                    () -> transactions.claim(pendingCancellation(), EFFECTIVE_AT));
            assertThatThrownBy(() -> claim.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            userSession.releaseLogin();
            AuthenticationResult issued = login.get(10, TimeUnit.SECONDS);
            assertThat(claim.get(10, TimeUnit.SECONDS)).isPresent();

            assertThat(issued.token().value()).isEqualTo("token-1001");
            assertThat(userSession.tokenActive()).isFalse();
            assertThat(userSession.logoutObserved()).isTrue();
            assertThatThrownBy(() -> tokenIssuer.issue(USER_ID, false))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo("AUTH_FORBIDDEN"));
        } finally {
            userSession.releaseLogin();
            executor.shutdownNow();
        }
    }

    private AccountCancellation pendingCancellation() {
        return new AccountCancellation(
                CANCELLATION_ID,
                USER_ID,
                EFFECTIVE_AT.minusDays(7),
                EFFECTIVE_AT,
                null,
                AccountCancellationStatus.PENDING,
                null,
                1L);
    }

    private String status() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM auth_account_cancellation WHERE id = ?",
                String.class,
                CANCELLATION_ID);
    }

    private String userStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM auth_user WHERE id = ?",
                String.class,
                USER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyTestConfig {

        @Bean
        @Primary
        Clock cancellationTestClock() {
            return Clock.fixed(
                    EFFECTIVE_AT.minusNanos(1_000_000)
                            .atZone(ZONE).toInstant(),
                    ZONE);
        }

        @Bean
        @Primary
        BlockingUserSession blockingUserSession() {
            return new BlockingUserSession();
        }
    }

    static final class BlockingUserSession implements UserSession {

        private final AtomicBoolean tokenActive = new AtomicBoolean();
        private final AtomicBoolean logoutObserved = new AtomicBoolean();
        private volatile CountDownLatch loginEntered = new CountDownLatch(0);
        private volatile CountDownLatch loginRelease = new CountDownLatch(0);

        void reset() {
            tokenActive.set(false);
            logoutObserved.set(false);
            loginEntered = new CountDownLatch(0);
            loginRelease = new CountDownLatch(0);
        }

        void blockNextLogin() {
            loginEntered = new CountDownLatch(1);
            loginRelease = new CountDownLatch(1);
        }

        boolean awaitLoginEntered() throws InterruptedException {
            return loginEntered.await(5, TimeUnit.SECONDS);
        }

        void releaseLogin() {
            loginRelease.countDown();
        }

        boolean tokenActive() {
            return tokenActive.get();
        }

        boolean logoutObserved() {
            return logoutObserved.get();
        }

        @Override
        public IssuedToken login(long userId) {
            tokenActive.set(true);
            loginEntered.countDown();
            try {
                if (!loginRelease.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待释放登录超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待登录被中断", exception);
            }
            return new IssuedToken("token-" + userId, 3600L);
        }

        @Override
        public long currentUserId() {
            return USER_ID;
        }

        @Override
        public void logout() {
            tokenActive.set(false);
        }

        @Override
        public void logoutUser(long userId) {
            tokenActive.set(false);
            logoutObserved.set(true);
        }
    }
}
