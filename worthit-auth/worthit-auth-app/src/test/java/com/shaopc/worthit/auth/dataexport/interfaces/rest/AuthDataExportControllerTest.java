package com.shaopc.worthit.auth.dataexport.interfaces.rest;

import com.shaopc.worthit.auth.dataexport.application.AuthDataExportService;
import com.shaopc.worthit.auth.dataexport.application.DataExportArchive;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthDataExportControllerTest {

    private static final String TRACE_ID = "trace-export-001";
    private final AuthDataExportService service =
            mock(AuthDataExportService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator generator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthDataExportController(service))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(), generator))
                .build();
    }

    @Test
    void returnsCompleteZipWithFrozenHeaders() throws Exception {
        byte[] body = {80, 75, 3, 4};
        when(service.exportCurrentUserData()).thenReturn(
                new DataExportArchive(
                        "worthit-data-export-20260804T101112Z.zip", body));

        mockMvc.perform(get("/api/v1/auth/data-export")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(content().bytes(body))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4L))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"worthit-data-export-"
                                + "20260804T101112Z.zip\""));
    }

    @Test
    void returnsJsonErrorBeforeAnyDownloadHeaders() throws Exception {
        when(service.exportCurrentUserData()).thenThrow(
                new BusinessException(
                        CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED));

        mockMvc.perform(get("/api/v1/auth/data-export")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.code")
                        .value("DATA_EXPORT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(header().doesNotExist(
                        HttpHeaders.CONTENT_DISPOSITION));
    }
}
