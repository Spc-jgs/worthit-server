package com.shaopc.worthit.tracking.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.tracking.client.api.TrackingDataExportClient;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.service.annotation.GetExchange;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataExportClientContractTest {

    @Test
    void freezesInternalPathAndStringIdentifiers() throws Exception {
        Method method = TrackingDataExportClient.class
                .getMethod("exportUserData", long.class);

        assertThat(method.getAnnotation(GetExchange.class).value())
                .isEqualTo("/internal/v1/tracking/users/{userId}/data-export");

        TrackingDataExportResponse response = new TrackingDataExportResponse(
                1,
                Instant.parse("2026-08-04T02:00:00Z"),
                "Asia/Shanghai",
                "9223372036854775807",
                List.of(new TrackingDataExportResponse.Category(
                        "9223372036854775806", "未分类", "UNCATEGORIZED",
                        1L, Instant.EPOCH, Instant.EPOCH, false, null)),
                List.of(), List.of(), List.of(), List.of(), List.of());

        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(response);
        assertThat(json)
                .contains("\"userId\":\"9223372036854775807\"")
                .contains("\"id\":\"9223372036854775806\"")
                .doesNotContain("9223372036854775807.0");
    }
}
