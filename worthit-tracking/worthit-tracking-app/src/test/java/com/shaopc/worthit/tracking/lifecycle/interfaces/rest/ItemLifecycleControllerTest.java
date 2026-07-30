package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyErrorCode;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleResult;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleService;
import com.shaopc.worthit.tracking.lifecycle.application.SellItemCommand;
import com.shaopc.worthit.tracking.lifecycle.application.ScrapItemCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemLifecycleControllerTest {

    private static final String TRACE_ID = "trace-life-001";
    private static final String IDEMPOTENCY_KEY =
            UUID.fromString(
                    "30a71455-6c32-4dba-bf59-918020f74da1")
                    .toString();
    private final ItemLifecycleService lifecycleService =
            mock(ItemLifecycleService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ItemLifecycleController(
                                lifecycleService))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                traceIdGenerator))
                .build();
    }

    @Test
    void returnsItemThroughFrozenContract() throws Exception {
        when(lifecycleService.returnItem(
                eq(1938L), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(returned());

        mockMvc.perform(post("/api/v1/items/1938/return")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "returnDate":"2026-07-30",
                                  "remark":"尺寸不合适"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId")
                        .value("1938"))
                .andExpect(jsonPath("$.data.lifecycleStatus")
                        .value("RETURNED"))
                .andExpect(jsonPath("$.data.disposal.type")
                        .value("RETURNED"))
                .andExpect(jsonPath("$.data.disposal.saleAmount")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void rejectsInvalidHeaderAndBodyBeforeUseCase()
            throws Exception {
        mockMvc.perform(post("/api/v1/items/1938/return")
                        .header("Idempotency-Key", "not-a-uuid")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":0,
                                  "returnDate":null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    @Test
    void mapsActiveProcessingToConflictInsteadOfServerError()
            throws Exception {
        when(lifecycleService.returnItem(
                eq(1938L), eq(IDEMPOTENCY_KEY), any()))
                .thenThrow(new BusinessException(
                        IdempotencyErrorCode.IDEM_IN_PROGRESS));

        mockMvc.perform(post("/api/v1/items/1938/return")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "returnDate":"2026-07-30"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("IDEM_IN_PROGRESS"));
    }

    @Test
    void sellsItemWithStringDecimalContract()
            throws Exception {
        when(lifecycleService.sellItem(
                eq(1938L), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(sold());

        mockMvc.perform(post("/api/v1/items/1938/sell")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "saleDate":"2026-07-30",
                                  "saleAmount":"800.00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus")
                        .value("SOLD"))
                .andExpect(jsonPath("$.data.disposal.saleAmount")
                        .value("800.000000"))
                .andExpect(jsonPath("$.data.disposal.netCost")
                        .value("200.000000"));
    }

    @Test
    void rejectsInvalidSaleAmountBeforeUseCase()
            throws Exception {
        mockMvc.perform(post("/api/v1/items/1938/sell")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "saleDate":"2026-07-30",
                                  "saleAmount":"1.0000001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    @Test
    void scrapsItemThroughFrozenContract()
            throws Exception {
        when(lifecycleService.scrapItem(
                eq(1938L), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(scrapped());

        mockMvc.perform(post("/api/v1/items/1938/scrap")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "scrapDate":"2026-07-30",
                                  "remark":"无法维修"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus")
                        .value("SCRAPPED"))
                .andExpect(jsonPath("$.data.disposal.type")
                        .value("SCRAPPED"))
                .andExpect(jsonPath("$.data.disposal.netCost")
                        .doesNotExist());
    }

    private static ItemLifecycleResult returned() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 30, 10, 0);
        return new ItemLifecycleResult(
                1938L,
                "RETURNED",
                new com.shaopc.worthit.tracking.lifecycle
                        .application.ItemDisposalDetail(
                        "RETURNED",
                        LocalDate.of(2026, 7, 30),
                        null,
                        "尺寸不合适",
                        null),
                2L,
                now);
    }

    private static ItemLifecycleResult sold() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 30, 10, 0);
        return new ItemLifecycleResult(
                1938L,
                "SOLD",
                new com.shaopc.worthit.tracking.lifecycle
                        .application.ItemDisposalDetail(
                        "SOLD",
                        LocalDate.of(2026, 7, 30),
                        new BigDecimal("800.000000")
                                .toPlainString(),
                        null,
                        new BigDecimal("200.000000")
                                .toPlainString()),
                2L,
                now);
    }

    private static ItemLifecycleResult scrapped() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 30, 10, 0);
        return new ItemLifecycleResult(
                1938L,
                "SCRAPPED",
                new com.shaopc.worthit.tracking.lifecycle
                        .application.ItemDisposalDetail(
                        "SCRAPPED",
                        LocalDate.of(2026, 7, 30),
                        null,
                        "无法维修",
                        null),
                2L,
                now);
    }
}
