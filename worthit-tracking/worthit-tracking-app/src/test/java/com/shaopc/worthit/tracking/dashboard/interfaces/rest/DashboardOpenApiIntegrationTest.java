package com.shaopc.worthit.tracking.dashboard.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        classes = WorthItTrackingApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "worthit.outbox.relay.enabled=false",
            "springdoc.api-docs.enabled=true",
            "springdoc.swagger-ui.enabled=true",
            "springdoc.enable-default-api-docs=false",
            "worthit.web.openapi.enabled=true",
            "sa-token.jwt-secret-key="
                    + "worthit-test-jwt-secret-key-at-least-thirty-two-bytes"
        })
class DashboardOpenApiIntegrationTest {

    private static final Set<String> DASHBOARD_FIELDS = Set.of(
            "itemPlanDailyTotal",
            "itemPlanDailyTotalDisplay",
            "itemResidualUnsetCount",
            "subscriptionMonthlyCnyTotal",
            "subscriptionMonthlyCnyTotalDisplay",
            "subscriptionMonthlyCnyApproximate",
            "subscriptionUnconvertedForeignCount",
            "wishConsideringCount",
            "wishConsideringAmountTotal",
            "wishConsideringAmountTotalDisplay");

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void publishesDashboardOnlyInPublicGroupWithFrozenSchema()
            throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");

        assertThat(publicDocument.path("paths")
                .has("/api/v1/dashboard")).isTrue();
        assertThat(internalDocument.path("paths")
                .has("/api/v1/dashboard")).isFalse();

        JsonNode schemas = publicDocument
                .path("components")
                .path("schemas");
        assertThat(schemas.has("DashboardResponse")).isTrue();
        JsonNode properties = schemas
                .path("DashboardResponse")
                .path("properties");
        Set<String> actualFields = StreamSupport.stream(
                        ((Iterable<String>) properties::fieldNames)
                                .spliterator(),
                        false)
                .collect(Collectors.toSet());

        assertThat(actualFields)
                .containsExactlyInAnyOrderElementsOf(
                        DASHBOARD_FIELDS);
        assertThat(properties.has("pendingCount")).isFalse();
    }

    @Test
    void publishesDataExportOnlyInInternalGroup() throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");
        String path = "/internal/v1/tracking/users/{userId}/data-export";

        assertThat(publicDocument.path("paths").has(path)).isFalse();
        assertThat(internalDocument.path("paths").has(path)).isTrue();
    }

    @Test
    void publishesAccountCancellationOnlyInInternalGroup() throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");
        String path =
                "/internal/v1/tracking/users/{userId}/account-cancellation";

        assertThat(publicDocument.path("paths").has(path)).isFalse();
        assertThat(internalDocument.path("paths").has(path)).isTrue();
    }

    @Test
    void publishesLifecyclePathsAndSchemasOnlyInPublicGroup()
            throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");

        for (String action :
                new String[]{"return", "sell", "scrap"}) {
            String path = "/api/v1/items/{id}/" + action;
            assertThat(publicDocument.path("paths").has(path))
                    .isTrue();
            assertThat(internalDocument.path("paths").has(path))
                    .isFalse();
        }
        JsonNode schemas = publicDocument
                .path("components")
                .path("schemas");
        assertThat(schemas.has("ReturnItemRequest")).isTrue();
        assertThat(schemas.has("SellItemRequest")).isTrue();
        assertThat(schemas.has("ScrapItemRequest")).isTrue();
        assertThat(schemas.has("ItemLifecycleResponse"))
                .isTrue();
        assertThat(schemas.has("ItemDisposalResponse")).isTrue();
    }

    @Test
    void publishesRecoveryPathsAndStringIdentifiersOnlyInPublicGroup()
            throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");
        String listPath = "/api/v1/recovery/resources";
        String restorePath =
                "/api/v1/recovery/resources/{resourceType}/{id}/restore";

        assertThat(publicDocument.path("paths").has(listPath))
                .isTrue();
        assertThat(publicDocument.path("paths").has(restorePath))
                .isTrue();
        assertThat(internalDocument.path("paths").has(listPath))
                .isFalse();
        assertThat(internalDocument.path("paths").has(restorePath))
                .isFalse();

        JsonNode schemas = publicDocument
                .path("components")
                .path("schemas");
        assertThat(schemas.path("DeletedRecoveryResourceResponse")
                .path("properties")
                .path("id")
                .path("type")
                .asText()).isEqualTo("string");
        assertThat(schemas.path("FullRestoreResponse")
                .path("properties")
                .path("categoryId")
                .path("type")
                .asText()).isEqualTo("string");
        assertThat(schemas.path("FullRestoreRequest")
                .path("properties")
                .has("version")).isTrue();
    }

    @Test
    void publishesReplacementAndReviewFrozenSchemas()
            throws Exception {
        JsonNode publicDocument =
                getOpenApiDocument("/v3/api-docs/public");
        JsonNode internalDocument =
                getOpenApiDocument("/v3/api-docs/internal");
        String replacementPath =
                "/api/v1/items/{oldItemId}/replace";
        String reviewPath =
                "/api/v1/lifecycle/review";

        assertThat(publicDocument.path("paths")
                .has(replacementPath)).isTrue();
        assertThat(publicDocument.path("paths")
                .has(reviewPath)).isTrue();
        assertThat(internalDocument.path("paths")
                .has(replacementPath)).isFalse();
        assertThat(internalDocument.path("paths")
                .has(reviewPath)).isFalse();

        JsonNode schemas = publicDocument
                .path("components")
                .path("schemas");
        assertThat(schemas.path("ItemReplacementResponse")
                .path("properties")
                .path("relationId")
                .path("type")
                .asText()).isEqualTo("string");
        assertThat(schemas.path("LifecycleItemBriefResponse")
                .path("properties")
                .path("id")
                .path("type")
                .asText()).isEqualTo("string");
        assertThat(schemas.path("LifecycleReviewEntryResponse")
                .path("properties")
                .path("id")
                .path("type")
                .asText()).isEqualTo("string");
        assertThat(schemas.path("LifecycleReviewEntryResponse")
                .path("properties")
                .has("disposal")).isTrue();
        assertThat(schemas.path("LifecycleReviewEntryResponse")
                .path("properties")
                .has("replacement")).isTrue();
        assertThat(schemas.path("LifecycleDisposalReviewResponse")
                .path("properties")
                .has("saleAmount")).isTrue();
        assertThat(schemas.path(
                        "LifecycleReplacementReviewResponse")
                .path("properties")
                .size()).isEqualTo(2);
    }

    private JsonNode getOpenApiDocument(String path)
            throws Exception {
        String content = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }
}
