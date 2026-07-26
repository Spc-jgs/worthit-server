package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.shaopc.worthit.auth.WorthItAuthApplication;
import com.shaopc.worthit.auth.authentication.application.AuthenticationResult;
import com.shaopc.worthit.auth.authentication.application.AuthenticationService;
import com.shaopc.worthit.auth.authentication.application.PasswordAuthenticationService;
import com.shaopc.worthit.auth.authentication.application.PasswordLoginCommand;
import com.shaopc.worthit.auth.authentication.application.WechatLoginCommand;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.application.port.WechatCodeExchange;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(AuthenticationConcurrencyIntegrationTest.FakeAuthenticationConfig.class)
@SpringBootTest(
        classes = WorthItAuthApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.auth.wechat.app-id=wx-app",
            "worthit.auth.wechat.app-secret=invalid-test-secret",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class AuthenticationConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private PasswordAuthenticationService passwordAuthenticationService;

    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

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
    void clearAuthenticationData() {
        jdbcTemplate.update("DELETE FROM auth_password_credential");
        jdbcTemplate.update("DELETE FROM auth_external_identity");
        jdbcTemplate.update("DELETE FROM auth_user");
    }

    @Test
    void concurrentFirstWechatLoginCreatesOneUserAndOneIdentity()
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<AuthenticationResult> login = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return authenticationService.login(
                        new WechatLoginCommand("same-code"));
            };
            List<Future<AuthenticationResult>> futures = List.of(
                    executor.submit(login),
                    executor.submit(login));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AuthenticationResult> results = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));

            assertThat(results)
                    .extracting(result -> result.user().id())
                    .containsOnly(results.get(0).user().id());
            assertThat(results)
                    .extracting(AuthenticationResult::newUser)
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(countRows("auth_user")).isEqualTo(1);
        assertThat(countRows("auth_external_identity")).isEqualTo(1);
    }

    @Test
    void persistsHashedPasswordCredentialAndLogsInThroughRealRepository() {
        passwordCredentialRepository.createAccount(
                "local.user",
                passwordHasher.encode("correct-password"),
                "本地用户");

        AuthenticationResult result = passwordAuthenticationService.login(
                new PasswordLoginCommand(
                        "LOCAL.USER", "correct-password"));

        assertThat(result.user().nickname()).isEqualTo("本地用户");
        assertThat(result.token().value())
                .isEqualTo("token-" + result.user().id());
        String passwordHash = jdbcTemplate.queryForObject(
                """
                SELECT password_hash
                FROM auth_password_credential
                WHERE username = 'local.user'
                """,
                String.class);
        assertThat(passwordHash)
                .startsWith("{bcrypt}")
                .doesNotContain("correct-password");
    }

    private Integer countRows(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAuthenticationConfig {

        @Bean
        @Primary
        WechatCodeExchange fixedWechatCodeExchange() {
            return code -> new WechatIdentity(
                    "wx-app", "openid-concurrent", null);
        }

        @Bean
        @Primary
        UserSession fakeUserSession() {
            return new UserSession() {
                @Override
                public IssuedToken login(long userId) {
                    return new IssuedToken(
                            "token-" + userId, 2_592_000L);
                }

                @Override
                public long currentUserId() {
                    return 1L;
                }

                @Override
                public void logout() {
                }
            };
        }
    }
}
