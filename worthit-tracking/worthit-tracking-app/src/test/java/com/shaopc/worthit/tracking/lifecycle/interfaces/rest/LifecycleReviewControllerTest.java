package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleService;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleDisposalReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleItemBrief;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReplacementReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewEntry;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LifecycleReviewControllerTest {

    private static final String TRACE_ID = "trace-review-001";
    private final ItemLifecycleService lifecycleService =
            mock(ItemLifecycleService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(
                        SerializationFeature
                                .WRITE_DATES_AS_TIMESTAMPS);
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LifecycleReviewController(
                                lifecycleService))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(
                                objectMapper))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                traceIdGenerator))
                .build();
    }

    @Test
    void returnsExplicitDiscriminatedUnionWithStringIds()
            throws Exception {
        when(lifecycleService.review(1, 20))
                .thenReturn(PageResult.of(
                        List.of(disposal(), replacement()),
                        new PageQuery(1, 20),
                        2));

        mockMvc.perform(get("/api/v1/lifecycle/review")
                        .param("page", "1")
                        .param("size", "20")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("9002"))
                .andExpect(jsonPath("$.data.items[0].entryType")
                        .value("DISPOSAL"))
                .andExpect(jsonPath("$.data.items[0].disposal.item.id")
                        .value("1938"))
                .andExpect(jsonPath("$.data.items[0].disposal.saleAmount")
                        .value("800.000000"))
                .andExpect(jsonPath("$.data.items[0].replacement")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id")
                        .value("9001"))
                .andExpect(jsonPath("$.data.items[1].entryType")
                        .value("REPLACEMENT"))
                .andExpect(jsonPath("$.data.items[1].disposal")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.data.items[1].replacement.newItem.id")
                        .value("1939"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    private static LifecycleReviewEntry disposal() {
        return new LifecycleReviewEntry(
                9002L,
                LifecycleReviewEntryType.DISPOSAL,
                LocalDate.of(2026, 7, 31),
                LocalDateTime.of(2026, 7, 31, 16, 0),
                new LifecycleDisposalReview(
                        new LifecycleItemBrief(1938L, "旧手机"),
                        "SOLD",
                        LocalDate.of(2026, 7, 31),
                        "800.000000",
                        "200.000000"),
                null);
    }

    private static LifecycleReviewEntry replacement() {
        return new LifecycleReviewEntry(
                9001L,
                LifecycleReviewEntryType.REPLACEMENT,
                LocalDate.of(2026, 7, 31),
                LocalDateTime.of(2026, 7, 31, 15, 0),
                null,
                new LifecycleReplacementReview(
                        new LifecycleItemBrief(1938L, "旧手机"),
                        new LifecycleItemBrief(1939L, "新手机")));
    }
}
