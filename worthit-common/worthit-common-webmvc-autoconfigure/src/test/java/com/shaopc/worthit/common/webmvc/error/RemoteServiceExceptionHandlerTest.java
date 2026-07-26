package com.shaopc.worthit.common.webmvc.error;

import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RemoteServiceExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RemoteFailureController())
                .setControllerAdvice(
                        new RemoteServiceExceptionHandler(
                                () -> "trace-generated"))
                .build();
    }

    @Test
    void mapsRemoteFailureToSafeUpstreamResponseWithLocalTraceId()
            throws Exception {
        mockMvc.perform(get("/fail/remote")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(
                        SecurityHeaderNames.TRACE_ID,
                        "trace-request"))
                .andExpect(jsonPath("$.code").value("SYS_UPSTREAM"))
                .andExpect(jsonPath("$.message")
                        .value("下游服务暂时不可用"))
                .andExpect(jsonPath("$.traceId").value("trace-request"))
                .andExpect(jsonPath("$.traceId").value(not("trace-remote")));
    }

    @RestController
    static class RemoteFailureController {

        @GetMapping("/fail/remote")
        void remote() {
            throw new RemoteServiceException(
                    "worthit-reminder",
                    503,
                    "REMOTE_UNAVAILABLE",
                    "trace-remote",
                    "下游服务暂时不可用");
        }
    }
}
