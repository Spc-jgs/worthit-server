package com.shaopc.worthit.reminder.app.reminder.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderListItem;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderTab;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderViewService;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderControllerTest {

    private static final String TRACE_ID =
            "trace-reminder-public-001";
    private final ReminderViewService service =
            mock(ReminderViewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ReminderController(service))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                traceIdGenerator))
                .build();
    }

    @Test
    void returnsFrozenReminderListContract()
            throws Exception {
        when(service.list(
                ReminderTab.PENDING, 1, 20))
                .thenReturn(PageResult.of(
                        List.of(new ReminderListItem(
                                9001L,
                                ReminderType.RENEWAL,
                                ReminderBusinessType
                                        .SUBSCRIPTION,
                                8001L,
                                LocalDate.of(
                                        2026, 8, 10),
                                LocalDateTime.of(
                                        2026, 8, 9, 0, 0),
                                "PENDING")),
                        new PageQuery(1, 20),
                        1L));

        mockMvc.perform(get("/api/v1/reminders")
                        .param("tab", "PENDING")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("9001"))
                .andExpect(jsonPath(
                        "$.data.items[0].reminderType")
                        .value("RENEWAL"))
                .andExpect(jsonPath(
                        "$.data.items[0].businessType")
                        .value("SUBSCRIPTION"))
                .andExpect(jsonPath(
                        "$.data.items[0].businessId")
                        .value("8001"))
                .andExpect(jsonPath(
                        "$.data.items[0].businessName")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[0].title")
                        .value("续费提醒"))
                .andExpect(jsonPath(
                        "$.data.items[0].detailPath")
                        .value("/subscriptions/8001"))
                .andExpect(jsonPath("$.data.total")
                        .value(1))
                .andExpect(jsonPath("$.data.hasMore")
                        .value(false))
                .andExpect(jsonPath("$.traceId")
                        .value(TRACE_ID));
    }

    @Test
    void returnsPendingCountContract()
            throws Exception {
        when(service.pendingCount()).thenReturn(3L);

        mockMvc.perform(get(
                        "/api/v1/reminders/pending-count")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count")
                        .value(3));
    }

    @Test
    void ignoresReminder() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/reminders/9001/ignore")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data")
                        .doesNotExist());

        verify(service).ignore(9001L);
    }

    @Test
    void rejectsUnknownTabAndOversizedPage()
            throws Exception {
        mockMvc.perform(get("/api/v1/reminders")
                        .param("tab", "CANCELED")
                        .param("size", "51")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }
}
