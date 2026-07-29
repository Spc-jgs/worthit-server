package com.shaopc.worthit.tracking.wish.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.wish.application.DeleteWishResult;
import com.shaopc.worthit.tracking.wish.application.WishDetail;
import com.shaopc.worthit.tracking.wish.application.WishPurchaseResult;
import com.shaopc.worthit.tracking.wish.application.WishService;
import com.shaopc.worthit.tracking.wish.application.WishSummary;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishControllerTest {

    private static final String TRACE_ID = "trace-wish-001";
    private static final String IDEMPOTENCY_KEY =
            UUID.fromString("8d6ea838-b487-4bd5-bc11-297c45ca80a6")
                    .toString();
    private static final long WISH_ID = 1938L;
    private final WishService wishService = mock(WishService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WishController(wishService))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(),
                        traceIdGenerator))
                .build();
    }

    @Test
    void createsWishWithStringMoneyAndIdentifiers() throws Exception {
        when(wishService.create(eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(wishDetail());

        mockMvc.perform(post("/api/v1/wishes")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.categoryId")
                        .value("100"))
                .andExpect(jsonPath("$.data.expectedPrice")
                        .value("1000.000000"))
                .andExpect(jsonPath("$.data.expectedYears")
                        .value("1.000"))
                .andExpect(jsonPath("$.data.residualValue")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.residualUnset")
                        .value(true))
                .andExpect(jsonPath("$.data.planDailyCost")
                        .value("2.74"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void rejectsMissingIdempotencyKeyAndOverflowCategory()
            throws Exception {
        mockMvc.perform(post("/api/v1/wishes")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreate()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));

        mockMvc.perform(get("/api/v1/wishes")
                        .param(
                                "categoryId",
                                "9223372036854775808")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    @Test
    void listsWishesWithOneBasedPagination() throws Exception {
        when(wishService.list(1, 20, "显示", 100L))
                .thenReturn(PageResult.of(
                        List.of(new WishSummary(
                                WISH_ID, "显示器", "数码",
                                "1000.000000", "¥2.74/天",
                                true,
                                LocalDate.of(2026, 8, 10),
                                "CONSIDERING", 1,
                                LocalDateTime.of(
                                        2026, 7, 29, 12, 0))),
                        new PageQuery(1, 20),
                        1));

        mockMvc.perform(get("/api/v1/wishes")
                        .param("keyword", "显示")
                        .param("categoryId", "100")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.hasMore")
                        .value(false));

        verify(wishService).list(1, 20, "显示", 100L);
    }

    @Test
    void updatesWishThroughPatchContract() throws Exception {
        when(wishService.update(
                eq(WISH_ID), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(wishDetail());

        mockMvc.perform(patch("/api/v1/wishes/1938")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "name":"显示器",
                                  "categoryId":"100",
                                  "expectedPrice":"1000.00",
                                  "expectedYears":"1",
                                  "residualValue":null,
                                  "reason":"护眼",
                                  "remark":null,
                                  "watchDeadline":"2026-08-10",
                                  "watchReminderEnabled":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"));
    }

    @Test
    void purchasesWishAndReturnsConvertedItem() throws Exception {
        when(wishService.purchase(
                WISH_ID, 1L, IDEMPOTENCY_KEY))
                .thenReturn(new WishPurchaseResult(
                        wishDetail(), itemDetail()));

        mockMvc.perform(post("/api/v1/wishes/1938/purchase")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wish.id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.item.id")
                        .value("2001"))
                .andExpect(jsonPath("$.data.item.purchasePrice")
                        .value("1000.000000"));
    }

    @Test
    void routesAbandonAndReconsiderCommands() throws Exception {
        when(wishService.abandon(
                WISH_ID, 1L, "不需要了", IDEMPOTENCY_KEY))
                .thenReturn(wishDetail());
        when(wishService.reconsider(
                WISH_ID, 1L, IDEMPOTENCY_KEY))
                .thenReturn(wishDetail());

        mockMvc.perform(post("/api/v1/wishes/1938/abandon")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "reason":"不需要了"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/wishes/1938/reconsider")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isOk());

        verify(wishService).abandon(
                WISH_ID, 1L, "不需要了", IDEMPOTENCY_KEY);
        verify(wishService).reconsider(
                WISH_ID, 1L, IDEMPOTENCY_KEY);
    }

    @Test
    void deletesAndRestoresWish() throws Exception {
        String restoreToken =
                "9d757d20-8570-453d-a990-00d1c6332ea5";
        when(wishService.delete(
                WISH_ID, 1L, IDEMPOTENCY_KEY))
                .thenReturn(new DeleteWishResult(
                        WISH_ID,
                        LocalDateTime.of(2026, 7, 29, 12, 1),
                        restoreToken));
        when(wishService.restore(WISH_ID, 2L, restoreToken))
                .thenReturn(wishDetail());

        mockMvc.perform(delete("/api/v1/wishes/1938")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .param("version", "1")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.restoreToken")
                        .value(restoreToken));

        mockMvc.perform(post("/api/v1/wishes/1938/restore")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":2,
                                  "restoreToken":"%s"
                                }
                                """.formatted(restoreToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"));
    }

    private static String validCreate() {
        return """
                {
                  "name":"显示器",
                  "categoryId":"100",
                  "expectedPrice":"1000.00",
                  "expectedYears":"1",
                  "residualValue":null,
                  "reason":"提升效率",
                  "remark":null,
                  "watchDeadline":"2026-08-10",
                  "watchReminderEnabled":null
                }
                """;
    }

    private static WishDetail wishDetail() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 29, 12, 0);
        return new WishDetail(
                WISH_ID, "显示器", 100L, "数码",
                "1000.000000", "1.000", null, true,
                "提升效率", null,
                LocalDate.of(2026, 8, 10), true,
                "CONSIDERING", null, null, null,
                365, "2.74", "¥2.74/天", false,
                1, now, now);
    }

    private static ItemDetail itemDetail() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 29, 12, 0);
        return new ItemDetail(
                2001L, "显示器", 100L, "数码",
                "1000.000000", "1.000", null, true,
                null, null, false, null, null,
                "HOLDING", 365, "2.74", "¥2.74/天",
                false, null, null, null, 1, now, now);
    }
}
