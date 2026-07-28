package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.subscription.application.DeleteSubscriptionResult;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionDetail;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionService;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerTest {

    private static final String TRACE_ID = "trace-sub-001";
    private static final String KEY =
            UUID.fromString(
                    "8d6ea838-b487-4bd5-bc11-297c45ca80a6")
                    .toString();
    private final SubscriptionService service =
            mock(SubscriptionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator generator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new SubscriptionController(service))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                generator))
                .build();
    }

    @Test
    void createsSubscriptionWithStringMoneyAndIds()
            throws Exception {
        when(service.create(eq(KEY), any()))
                .thenReturn(detail());

        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.amount")
                        .value("20.000000"))
                .andExpect(jsonPath(
                        "$.data.originalMonthlyCost")
                        .value("20.00"))
                .andExpect(jsonPath(
                        "$.data.originalMonthlyCostDisplay")
                        .value("20.00 USD/月"))
                .andExpect(jsonPath("$.data.cnyMonthlyCost")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.data.includeInCnyTotal")
                        .value(false))
                .andExpect(jsonPath("$.traceId")
                        .value(TRACE_ID));
    }

    @Test
    void exposesListDetailAndUpdateContracts()
            throws Exception {
        when(service.detail(1938L)).thenReturn(detail());
        when(service.list(1, 20, "Chat", null))
                .thenReturn(PageResult.of(
                        List.of(new SubscriptionSummary(
                                1938L,
                                "ChatGPT Plus",
                                "未分类",
                                "20.000000",
                                "USD",
                                "20.00 USD/月",
                                null,
                                "ACTIVE",
                                null,
                                1,
                                LocalDateTime.of(
                                        2026, 7, 28, 10, 0))),
                        new PageQuery(1, 20),
                        1));
        when(service.update(
                eq(1938L), eq(KEY), any()))
                .thenReturn(detail());

        mockMvc.perform(get("/api/v1/subscriptions/1938")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value("1938"));

        mockMvc.perform(get("/api/v1/subscriptions")
                        .param("keyword", "Chat")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.total")
                        .value(1));

        mockMvc.perform(patch(
                        "/api/v1/subscriptions/1938")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate()))
                .andExpect(status().isOk());
    }

    @Test
    void exposesPauseEndResumeDeleteAndRestore()
            throws Exception {
        when(service.pause(1938L, 1L, KEY))
                .thenReturn(detail());
        when(service.end(1938L, 1L, KEY))
                .thenReturn(detail());
        when(service.resume(eq(1938L), eq(KEY), any()))
                .thenReturn(detail());
        when(service.delete(1938L, 1L, KEY))
                .thenReturn(new DeleteSubscriptionResult(
                        1938L,
                        LocalDateTime.of(
                                2026, 7, 28, 10, 1),
                        KEY));
        when(service.restore(1938L, 2L, KEY))
                .thenReturn(detail());

        mockMvc.perform(post(
                        "/api/v1/subscriptions/1938/pause")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/subscriptions/1938/end")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/v1/subscriptions/1938/resume")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "nextRenewalDate":"2026-08-10",
                                  "renewalReminderEnabled":true
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete(
                        "/api/v1/subscriptions/1938")
                        .header("Idempotency-Key", KEY)
                        .param("version", "1")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoreToken")
                        .value(KEY));
        mockMvc.perform(post(
                        "/api/v1/subscriptions/1938/restore")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":2,
                                  "restoreToken":"%s"
                                }
                                """.formatted(KEY)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingKeyAndInvalidCycleContract()
            throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/subscriptions")
                        .header("Idempotency-Key", KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Plus",
                                  "amount":"20",
                                  "currency":"USD",
                                  "billingCycleType":"UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    private static String validCreate() {
        return """
                {
                  "name":"ChatGPT Plus",
                  "categoryId":null,
                  "amount":"20.00",
                  "currency":"USD",
                  "billingCycleType":"MONTHLY",
                  "billingCycleValue":null,
                  "cnyReferenceAmount":null,
                  "nextRenewalDate":null,
                  "autoRenew":"UNKNOWN",
                  "renewalReminderEnabled":null,
                  "remark":null
                }
                """;
    }

    private static String validUpdate() {
        return """
                {
                  "version":1,
                  "name":"ChatGPT Plus",
                  "categoryId":null,
                  "amount":"20.00",
                  "currency":"USD",
                  "billingCycleType":"MONTHLY",
                  "billingCycleValue":null,
                  "cnyReferenceAmount":"140.00",
                  "nextRenewalDate":"2026-08-10",
                  "autoRenew":"YES",
                  "renewalReminderEnabled":true,
                  "remark":null
                }
                """;
    }

    private static SubscriptionDetail detail() {
        LocalDateTime time =
                LocalDateTime.of(2026, 7, 28, 10, 0);
        return new SubscriptionDetail(
                1938L,
                "ChatGPT Plus",
                100L,
                "未分类",
                "20.000000",
                "USD",
                "MONTHLY",
                null,
                null,
                null,
                "UNKNOWN",
                false,
                "ACTIVE",
                null,
                "20.00",
                "20.00 USD/月",
                null,
                null,
                false,
                false,
                1,
                time,
                time);
    }
}
