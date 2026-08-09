package com.shaopc.worthit.auth.dataexport.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.auth.WorthItAuthApplication;
import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationExecutionTransactions;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.dataexport.application.AuthDataExportAccount;
import com.shaopc.worthit.auth.dataexport.application.AuthDataExportQuery;
import com.shaopc.worthit.common.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        classes = WorthItAuthApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.security.same-token.rotation.enabled=false",
            "worthit.auth.wechat.app-id=wx-app",
            "worthit.auth.wechat.app-secret=test-only-secret",
            "springdoc.api-docs.enabled=true",
            "springdoc.swagger-ui.enabled=true",
            "springdoc.enable-default-api-docs=false",
            "worthit.web.openapi.enabled=true",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class AuthDataExportPersistenceIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AuthDataExportQuery query;

    @Autowired
    private AccountCancellationExecutionTransactions cancellationTransactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSession userSession;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM auth_password_credential");
        jdbcTemplate.update("DELETE FROM auth_external_identity");
        jdbcTemplate.update("DELETE FROM auth_login_audit");
        jdbcTemplate.update("DELETE FROM auth_idempotency_record");
        jdbcTemplate.update("DELETE FROM auth_account_cancellation");
        jdbcTemplate.update("DELETE FROM auth_user");
    }

    @Test
    void finalizationDeletesAuthIdentityAndRetainsMinimalAudit() {
        LocalDateTime applyAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 8, 10, 0);
        jdbcTemplate.update(
                """
                INSERT INTO auth_user (
                    id, nickname, avatar_file_id, status,
                    create_time, update_time
                ) VALUES (42, '待注销', 99, 'CANCELLATION_EXECUTING', ?, ?),
                         (43, '其他用户', NULL, 'ACTIVE', ?, ?)
                """,
                applyAt,
                completedAt,
                applyAt,
                completedAt);
        jdbcTemplate.update(
                """
                INSERT INTO auth_password_credential (
                    user_id, username, password_hash, create_time, update_time
                ) VALUES (42, 'cancel-user', '{bcrypt}secret', ?, ?)
                """,
                applyAt,
                completedAt);
        jdbcTemplate.update(
                """
                INSERT INTO auth_external_identity (
                    id, user_id, identity_type, app_id, external_subject,
                    verified, create_time, update_time
                ) VALUES (100, 42, 'WECHAT_MINI', 'wx-app',
                          'openid-secret', 1, ?, ?)
                """,
                applyAt,
                completedAt);
        jdbcTemplate.update(
                """
                INSERT INTO auth_login_audit (
                    id, user_id, identity_type, result, ip,
                    device_summary, create_time
                ) VALUES (200, 42, 'PASSWORD', 'SUCCESS',
                          '127.0.0.1', 'secret-device', ?)
                """,
                completedAt);
        jdbcTemplate.update(
                """
                INSERT INTO auth_idempotency_record (
                    id, user_id, operation_code, idempotency_key,
                    request_hash, response_json, status,
                    processing_expire_at, expires_at, create_time, update_time
                ) VALUES (300, 42, 'APPLY',
                          '00000000-0000-0000-0000-000000000001',
                          ?, '{}', 'SUCCEEDED', NULL, ?, ?, ?)
                """,
                "a".repeat(64),
                completedAt.plusDays(90),
                applyAt,
                completedAt);
        jdbcTemplate.update(
                """
                INSERT INTO auth_account_cancellation (
                    id, user_id, apply_at, effective_at, completed_at,
                    status, revoked_at, version, create_time, update_time
                ) VALUES (400, 42, ?, ?, NULL, 'EXECUTING', NULL, 2, ?, ?)
                """,
                applyAt,
                applyAt.plusDays(7),
                applyAt,
                completedAt);
        AccountCancellation cancellation = new AccountCancellation(
                400L,
                42L,
                applyAt,
                applyAt.plusDays(7),
                null,
                AccountCancellationStatus.EXECUTING,
                null,
                2L);

        cancellationTransactions.finalizeExecution(cancellation, completedAt);

        verify(userSession).logoutUser(42L);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE id = 42",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE id = 43",
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_password_credential "
                        + "WHERE user_id = 42",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_external_identity WHERE user_id = 42",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_login_audit WHERE user_id = 42",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_idempotency_record WHERE user_id = 42",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM auth_account_cancellation WHERE id = 400",
                String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void readsOnlyExplicitOwnedAccountFields() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
        jdbcTemplate.update(
                """
                INSERT INTO auth_user (
                    id, nickname, avatar_file_id, status,
                    create_time, update_time
                ) VALUES (42, '小值', 99, 'ACTIVE', ?, ?)
                """,
                now, now);
        jdbcTemplate.update(
                """
                INSERT INTO auth_external_identity (
                    id, user_id, identity_type, app_id, external_subject,
                    union_id, verified, create_time, update_time
                ) VALUES (100, 42, 'WECHAT_MINI', 'wx-app',
                          'openid-secret', 'unionid-secret', 1, ?, ?)
                """,
                now, now);

        AuthDataExportAccount response = query.exportAccount(42L);

        assertThat(response.userId()).isEqualTo("42");
        assertThat(response.account().id()).isEqualTo("42");
        assertThat(response.account().avatarFileId()).isEqualTo("99");
        assertThat(response.timeZone()).isEqualTo("Asia/Shanghai");
        assertThat(objectMapper.writeValueAsString(response))
                .doesNotContain(
                        "openid-secret",
                        "unionid-secret",
                        "subject",
                        "passwordHash",
                        "token");
    }

    @Test
    void reportsInvisibleAccountAsNotFound() {
        assertThatThrownBy(() -> query.exportAccount(404L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("RES_NOT_FOUND"));
    }

    @Test
    void publishesBinaryExportOnlyInPublicOpenApiGroup() throws Exception {
        var publicDocument = getOpenApi("/v3/api-docs/public");
        var internalDocument = getOpenApi("/v3/api-docs/internal");
        String path = "/api/v1/auth/data-export";

        assertThat(publicDocument.path("paths").has(path)).isTrue();
        assertThat(internalDocument.path("paths").has(path)).isFalse();
        assertThat(publicDocument.path("paths").path(path).path("get")
                .path("responses").path("200").path("content")
                .has("application/zip")).isTrue();
    }

    @Test
    void publishesAccountCancellationOnlyInPublicOpenApiGroup()
            throws Exception {
        var publicDocument = getOpenApi("/v3/api-docs/public");
        var internalDocument = getOpenApi("/v3/api-docs/internal");

        for (String path : new String[]{
                "/api/v1/auth/cancellation",
                "/api/v1/auth/cancellation/revoke"}) {
            assertThat(publicDocument.path("paths").has(path)).isTrue();
            assertThat(internalDocument.path("paths").has(path)).isFalse();
        }
        assertThat(publicDocument.path("components").path("schemas")
                .path("AccountCancellationResponse")
                .path("properties").path("id").path("type").asText())
                .isEqualTo("string");
    }

    private com.fasterxml.jackson.databind.JsonNode getOpenApi(String path)
            throws Exception {
        String content = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }
}
