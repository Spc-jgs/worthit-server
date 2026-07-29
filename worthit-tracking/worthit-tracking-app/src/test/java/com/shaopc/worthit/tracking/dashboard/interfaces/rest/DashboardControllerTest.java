package com.shaopc.worthit.tracking.dashboard.interfaces.rest;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.dashboard.application.DashboardResult;
import com.shaopc.worthit.tracking.dashboard.application.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private static final String TRACE_ID =
            "trace-dashboard-001";

    private final DashboardService dashboardService =
            mock(DashboardService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new DashboardController(dashboardService))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                traceIdGenerator))
                .build();
    }

    @Test
    void returnsFrozenDashboardContractWithoutReminderFields()
            throws Exception {
        when(dashboardService.summary())
                .thenReturn(new DashboardResult(
                        "2.74",
                        "¥2.74/天",
                        1,
                        "150.00",
                        "约 ¥150.00/月",
                        true,
                        0,
                        1,
                        "1000.00",
                        "¥1000.00"));

        mockMvc.perform(get("/api/v1/dashboard")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.itemPlanDailyTotal")
                        .value("2.74"))
                .andExpect(jsonPath(
                        "$.data.itemPlanDailyTotalDisplay")
                        .value("¥2.74/天"))
                .andExpect(jsonPath(
                        "$.data.itemResidualUnsetCount")
                        .value(1))
                .andExpect(jsonPath(
                        "$.data.subscriptionMonthlyCnyTotal")
                        .value("150.00"))
                .andExpect(jsonPath(
                        "$.data.subscriptionMonthlyCnyTotalDisplay")
                        .value("约 ¥150.00/月"))
                .andExpect(jsonPath(
                        "$.data.subscriptionMonthlyCnyApproximate")
                        .value(true))
                .andExpect(jsonPath(
                        "$.data.subscriptionUnconvertedForeignCount")
                        .value(0))
                .andExpect(jsonPath(
                        "$.data.wishConsideringCount")
                        .value(1))
                .andExpect(jsonPath(
                        "$.data.wishConsideringAmountTotal")
                        .value("1000.00"))
                .andExpect(jsonPath(
                        "$.data.wishConsideringAmountTotalDisplay")
                        .value("¥1000.00"))
                .andExpect(jsonPath("$.data.pendingCount")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.recentProcessed")
                        .doesNotExist())
                .andExpect(jsonPath("$.traceId")
                        .value(TRACE_ID));

        verify(dashboardService).summary();
    }
}
