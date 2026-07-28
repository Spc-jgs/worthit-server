package com.shaopc.worthit.tracking.item.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.item.application.DeleteItemResult;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemService;
import com.shaopc.worthit.tracking.item.application.ItemSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemControllerTest {

    private static final String TRACE_ID = "trace-item-001";
    private static final String IDEMPOTENCY_KEY =
            UUID.fromString("8d6ea838-b487-4bd5-bc11-297c45ca80a6")
                    .toString();
    private final ItemService itemService = mock(ItemService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ItemController(itemService))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(),
                        traceIdGenerator))
                .build();
    }

    @Test
    void createsItemWithStringMoneyAndIdentifiers() throws Exception {
        when(itemService.create(eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(itemDetail());

        mockMvc.perform(post("/api/v1/items")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"MacBook",
                                  "categoryId":null,
                                  "purchasePrice":"1000.00",
                                  "expectedYears":"1",
                                  "residualValue":null,
                                  "purchaseDate":null,
                                  "warrantyExpireDate":null,
                                  "warrantyReminderEnabled":null,
                                  "brandModel":null,
                                  "remark":null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.purchasePrice")
                        .value("1000.000000"))
                .andExpect(jsonPath("$.data.expectedYears")
                        .value("1.000"))
                .andExpect(jsonPath("$.data.residualValue")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.residualUnset")
                        .value(true))
                .andExpect(jsonPath("$.data.planDailyCost")
                        .value("2.74"))
                .andExpect(jsonPath("$.data.planDailyCostDisplay")
                        .value("¥2.74/天"))
                .andExpect(jsonPath("$.data.planDailyCostTiny")
                        .value(false))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidDecimalsAtBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"MacBook",
                                  "purchasePrice":"-1",
                                  "expectedYears":"0"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    @Test
    void returnsItemDetail() throws Exception {
        when(itemService.detail(1938L)).thenReturn(itemDetail());

        mockMvc.perform(get("/api/v1/items/1938")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.categoryName")
                        .value("未分类"));
    }

    @Test
    void returnsPagedItemsUsingOneBasedPageContract() throws Exception {
        when(itemService.list(1, 20, "Mac", null))
                .thenReturn(PageResult.of(
                        List.of(new ItemSummary(
                                1938L,
                                "MacBook",
                                "未分类",
                                "¥2.74/天",
                                true,
                                "HOLDING",
                                LocalDateTime.of(
                                        2026, 7, 26, 10, 0))),
                        new PageQuery(1, 20),
                        1));

        mockMvc.perform(get("/api/v1/items")
                        .param("page", "1")
                        .param("size", "20")
                        .param("keyword", "Mac")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        verify(itemService).list(1, 20, "Mac", null);
    }

    @Test
    void updatesItemThroughPatchContract() throws Exception {
        when(itemService.update(
                eq(1938L), eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(itemDetail());

        mockMvc.perform(patch("/api/v1/items/1938")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":1,
                                  "name":"MacBook Pro",
                                  "categoryId":"100",
                                  "purchasePrice":"1200.00",
                                  "expectedYears":"2",
                                  "residualValue":"0",
                                  "purchaseDate":"2026-07-01",
                                  "warrantyExpireDate":"2027-07-01",
                                  "warrantyReminderEnabled":true,
                                  "brandModel":"M4",
                                  "remark":"办公使用"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"));
    }

    @Test
    void deletesItemAndReturnsRestoreGrant() throws Exception {
        when(itemService.delete(
                1938L, 1L, IDEMPOTENCY_KEY))
                .thenReturn(new DeleteItemResult(
                        1938L,
                        LocalDateTime.of(
                                2026, 7, 26, 10, 1),
                        "9d757d20-8570-453d-a990-00d1c6332ea5"));

        mockMvc.perform(delete("/api/v1/items/1938")
                        .header(
                                "Idempotency-Key",
                                IDEMPOTENCY_KEY)
                        .param("version", "1")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.restoreToken").value(
                        "9d757d20-8570-453d-a990-00d1c6332ea5"));
    }

    @Test
    void restoresItemWithVersionAndToken() throws Exception {
        when(itemService.restore(
                1938L,
                2L,
                "9d757d20-8570-453d-a990-00d1c6332ea5"))
                .thenReturn(itemDetail());

        mockMvc.perform(post("/api/v1/items/1938/restore")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":2,
                                  "restoreToken":
                                    "9d757d20-8570-453d-a990-00d1c6332ea5"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"));
    }

    private static String validRequest() {
        return """
                {
                  "name":"MacBook",
                  "purchasePrice":"1000.00",
                  "expectedYears":"1"
                }
                """;
    }

    private static ItemDetail itemDetail() {
        LocalDateTime time = LocalDateTime.of(
                2026, 7, 26, 10, 0);
        return new ItemDetail(
                1938L,
                "MacBook",
                100L,
                "未分类",
                "1000.000000",
                "1.000",
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                "HOLDING",
                365,
                "2.74",
                "¥2.74/天",
                false,
                null,
                null,
                null,
                1L,
                time,
                time);
    }
}
