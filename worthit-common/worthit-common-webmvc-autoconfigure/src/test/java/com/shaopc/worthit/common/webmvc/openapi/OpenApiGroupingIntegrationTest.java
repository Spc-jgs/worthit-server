package com.shaopc.worthit.common.webmvc.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.common.web.response.FieldViolation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = OpenApiGroupingIntegrationTest.TestApplication.class,
        properties = {
                "springdoc.api-docs.enabled=true",
                "springdoc.swagger-ui.enabled=true",
                "springdoc.enable-default-api-docs=false"
        })
@AutoConfigureMockMvc
class OpenApiGroupingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicGroupContainsOnlyPublicPaths() throws Exception {
        JsonNode document = getOpenApiDocument("/v3/api-docs/public");

        assertThat(document.path("paths").has("/api/v1/test-items")).isTrue();
        assertThat(document.path("paths").has("/internal/v1/test-reminders")).isFalse();
        assertThat(document.path("paths").has("/actuator-like-test")).isFalse();
    }

    @Test
    void internalGroupContainsOnlyInternalPaths() throws Exception {
        JsonNode document = getOpenApiDocument("/v3/api-docs/internal");

        assertThat(document.path("paths").has("/internal/v1/test-reminders")).isTrue();
        assertThat(document.path("paths").has("/api/v1/test-items")).isFalse();
        assertThat(document.path("paths").has("/actuator-like-test")).isFalse();
    }

    @Test
    void exposesCommonResponseSchemas() throws Exception {
        JsonNode document = getOpenApiDocument("/v3/api-docs/internal");
        Iterable<String> schemaNames =
                document.path("components").path("schemas")::fieldNames;

        assertThat(StreamSupport.stream(schemaNames.spliterator(), false))
                .anyMatch(name -> name.contains("ApiResponse"))
                .anyMatch(name -> name.contains("FieldViolation"));
    }

    @Test
    void disablesDefaultApiDocsEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesSwaggerUiWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    private JsonNode getOpenApiDocument(String path) throws Exception {
        String content = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test-items")
        ApiResponse<String> getPublicItem() {
            return ApiResponse.success("item", "trace-public");
        }

        @GetMapping("/internal/v1/test-reminders")
        ApiResponse<List<FieldViolation>> getInternalReminder() {
            return ApiResponse.success(
                    List.of(new FieldViolation("businessId", "不能为空")),
                    "trace-internal");
        }

        @GetMapping("/actuator-like-test")
        String getUnrelatedEndpoint() {
            return "unrelated";
        }
    }
}
