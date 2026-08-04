package com.shaopc.worthit.reminder.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.reminder.client.api.ReminderDataExportClient;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.service.annotation.GetExchange;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderDataExportClientContractTest {

    @Test
    void freezesInternalPathAndExcludesTechnicalMetadata() throws Exception {
        Method method = ReminderDataExportClient.class
                .getMethod("exportUserData", long.class);
        assertThat(method.getAnnotation(GetExchange.class).value())
                .isEqualTo("/internal/v1/reminders/users/{userId}/data-export");

        ReminderDataExportResponse response = new ReminderDataExportResponse(
                1, Instant.EPOCH, "Asia/Shanghai", "9223372036854775807",
                List.of(new ReminderDataExportResponse.Binding(
                        "11", "ITEM", "22", "WARRANTY", true,
                        Instant.EPOCH, Instant.EPOCH)),
                List.of());
        String json = new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(response);
        assertThat(json)
                .contains("\"businessId\":\"22\"")
                .doesNotContain("lastSourceVersion")
                .doesNotContain("sourceEventId")
                .doesNotContain("pendingMarker");
    }
}
