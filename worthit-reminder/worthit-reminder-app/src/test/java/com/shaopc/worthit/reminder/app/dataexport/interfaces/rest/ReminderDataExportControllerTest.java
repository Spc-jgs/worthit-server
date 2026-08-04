package com.shaopc.worthit.reminder.app.dataexport.interfaces.rest;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.reminder.app.dataexport.application.ReminderDataExportService;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderDataExportControllerTest {

    private final ReminderDataExportService service =
            mock(ReminderDataExportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator generator = () -> "trace-reminder-export";
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReminderDataExportController(service))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(), generator))
                .build();
    }

    @Test
    void acceptsOnlyAuthCallerAndPositiveLongUserId() throws Exception {
        when(service.exportUserData(42L)).thenReturn(response());

        mockMvc.perform(get(
                                "/internal/v1/reminders/users/42/data-export")
                        .header(SecurityHeaderNames.CALLER_SERVICE,
                                "worthit-auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"));
        verify(service).exportUserData(42L);

        mockMvc.perform(get(
                                "/internal/v1/reminders/users/42/data-export")
                        .header(SecurityHeaderNames.CALLER_SERVICE,
                                "worthit-tracking"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mockMvc.perform(get(
                                "/internal/v1/reminders/users/overflow/data-export")
                        .header(SecurityHeaderNames.CALLER_SERVICE,
                                "worthit-auth"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    private static ReminderDataExportResponse response() {
        return new ReminderDataExportResponse(
                1, Instant.parse("2026-08-04T00:00:00Z"),
                "Asia/Shanghai", "42", List.of(), List.of());
    }
}
