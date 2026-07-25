package com.shaopc.worthit.tracking.infrastructure.client;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ServletTraceIdProviderTest {

    private final ServletTraceIdProvider provider =
            new ServletTraceIdProvider(() -> "trace-generated");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void propagatesOnlyTraceIdTrustedByFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityHeaderNames.TRACE_ID, "trace-forged");
        request.setAttribute(SecurityHeaderNames.TRACE_ID, "trace-trusted");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        assertThat(provider.currentTraceId()).isEqualTo("trace-trusted");
    }

    @Test
    void generatesTraceIdWithoutTrustedRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityHeaderNames.TRACE_ID, "trace-forged");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        assertThat(provider.currentTraceId()).isEqualTo("trace-generated");
        RequestContextHolder.resetRequestAttributes();
        assertThat(provider.currentTraceId()).isEqualTo("trace-generated");
    }
}
