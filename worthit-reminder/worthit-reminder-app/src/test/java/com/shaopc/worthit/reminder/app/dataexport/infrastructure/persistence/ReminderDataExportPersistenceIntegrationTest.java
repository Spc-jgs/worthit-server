package com.shaopc.worthit.reminder.app.dataexport.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.app.WorthItReminderApplication;
import com.shaopc.worthit.reminder.app.dataexport.application.ReminderDataExportService;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        classes = WorthItReminderApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "springdoc.api-docs.enabled=true",
            "springdoc.swagger-ui.enabled=true",
            "springdoc.enable-default-api-docs=false",
            "worthit.web.openapi.enabled=true",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class ReminderDataExportPersistenceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 4, 10, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReminderDataExportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM rem_command_log");
        jdbcTemplate.update("DELETE FROM rem_instance");
        jdbcTemplate.update("DELETE FROM rem_binding");
    }

    @Test
    void exportsOwnedBindingsAndAllInstanceStatesInIdOrder() throws Exception {
        insertBinding(102L, USER_ID, 2L, 88L);
        insertBinding(101L, USER_ID, 1L, 77L);
        insertBinding(199L, 2002L, 9L, 99L);
        insertInstance(202L, 102L, USER_ID, "IGNORED", true);
        insertInstance(201L, 101L, USER_ID, "PENDING", false);
        insertInstance(299L, 199L, 2002L, "PROCESSED", true);

        ReminderDataExportResponse response = service.exportUserData(USER_ID);

        assertThat(response.userId()).isEqualTo("1001");
        assertThat(response.bindings())
                .extracting(ReminderDataExportResponse.Binding::id)
                .containsExactly("101", "102");
        assertThat(response.instances())
                .extracting(ReminderDataExportResponse.Instance::id)
                .containsExactly("201", "202");
        assertThat(response.instances())
                .extracting(ReminderDataExportResponse.Instance::status)
                .containsExactly("PENDING", "IGNORED");

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain(
                "source-event-secret",
                "lastSourceVersion",
                "pendingMarker",
                "其他用户");
    }

    @Test
    void publishesDataExportOnlyInInternalOpenApiGroup() throws Exception {
        var publicDocument = getOpenApi("/v3/api-docs/public");
        var internalDocument = getOpenApi("/v3/api-docs/internal");
        String path = "/internal/v1/reminders/users/{userId}/data-export";

        assertThat(publicDocument.path("paths").has(path)).isFalse();
        assertThat(internalDocument.path("paths").has(path)).isTrue();
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

    private void insertBinding(
            long id, long userId, long businessId, long sourceVersion) {
        jdbcTemplate.update(
                """
                INSERT INTO rem_binding (
                    id, user_id, business_type, business_id, reminder_type,
                    reminder_enabled, last_source_version,
                    create_time, update_time
                ) VALUES (?, ?, 'ITEM', ?, 'WARRANTY', 1, ?, ?, ?)
                """,
                id, userId, businessId, sourceVersion, NOW, NOW);
    }

    private void insertInstance(
            long id,
            long bindingId,
            long userId,
            String status,
            boolean resolved) {
        jdbcTemplate.update(
                """
                INSERT INTO rem_instance (
                    id, binding_id, user_id, business_date, remind_at,
                    timezone, status, resolved_at, resolution_reason,
                    created_source_event_id, resolved_source_event_id,
                    create_time, update_time
                ) VALUES (?, ?, ?, '2026-08-10', ?, 'Asia/Shanghai', ?,
                          ?, ?, 'source-event-secret', ?, ?, ?)
                """,
                id,
                bindingId,
                userId,
                NOW,
                status,
                resolved ? NOW : null,
                resolved ? "USER_ACTION" : null,
                resolved ? "resolved-event-secret" : null,
                NOW,
                NOW);
    }
}
