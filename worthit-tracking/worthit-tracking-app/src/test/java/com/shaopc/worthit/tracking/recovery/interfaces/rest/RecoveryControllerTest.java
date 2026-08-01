package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResourceSummary;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResult;
import com.shaopc.worthit.tracking.recovery.application.RecoveryService;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecoveryControllerTest {

    private static final String TRACE_ID = "trace-recovery-001";
    private static final String IDEMPOTENCY_KEY =
            UUID.fromString("a92518f2-b76a-4ec7-8f30-4a7cd5207305")
                    .toString();
    private final RecoveryService recoveryService =
            mock(RecoveryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new RecoveryController(recoveryService))
                .setControllerAdvice(
                        new WorthItRestExceptionHandler(
                                new DefaultErrorHttpStatusResolver(),
                                traceIdGenerator))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(
                                Jackson2ObjectMapperBuilder.json()
                                        .modules(new JavaTimeModule())
                                        .featuresToDisable(
                                                SerializationFeature
                                                        .WRITE_DATES_AS_TIMESTAMPS)
                                        .build()))
                .build();
    }

    @Test
    void listsDeletedResourcesWithStringIdentifiers()
            throws Exception {
        when(recoveryService.list(null, 1, 20))
                .thenReturn(PageResult.of(
                        List.of(new RecoveryResourceSummary(
                                1938L,
                                RecoveryResourceType.ITEM,
                                "相机",
                                100L,
                                "数码",
                                false,
                                "HOLDING",
                                2L,
                                LocalDateTime.of(
                                        2026, 8, 2, 10, 0))),
                        new PageQuery(1, 20),
                        1));

        mockMvc.perform(get("/api/v1/recovery/resources")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id")
                        .value("1938"))
                .andExpect(jsonPath(
                        "$.data.items[0].resourceType")
                        .value("ITEM"))
                .andExpect(jsonPath(
                        "$.data.items[0].categoryId")
                        .value("100"))
                .andExpect(jsonPath(
                        "$.data.items[0].categoryAvailable")
                        .value(false))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void restoresWithUuidIdempotencyAndVersion()
            throws Exception {
        when(recoveryService.restore(
                RecoveryResourceType.ITEM,
                1938L,
                2L,
                IDEMPOTENCY_KEY))
                .thenReturn(new RecoveryResult(
                        1938L,
                        RecoveryResourceType.ITEM,
                        "相机",
                        200L,
                        "未分类",
                        "HOLDING",
                        3L,
                        true));

        mockMvc.perform(post(
                                "/api/v1/recovery/resources/ITEM/1938/restore")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.categoryId")
                        .value("200"))
                .andExpect(jsonPath(
                        "$.data.categoryFallbackApplied")
                        .value(true));

        verify(recoveryService).restore(
                RecoveryResourceType.ITEM,
                1938L,
                2L,
                IDEMPOTENCY_KEY);
    }

    @Test
    void rejectsInvalidTypeVersionAndIdempotencyKey()
            throws Exception {
        mockMvc.perform(get("/api/v1/recovery/resources")
                        .param("resourceType", "OTHER")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(
                                "/api/v1/recovery/resources/ITEM/1938/restore")
                        .header("Idempotency-Key", "not-a-uuid")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest());
    }
}
