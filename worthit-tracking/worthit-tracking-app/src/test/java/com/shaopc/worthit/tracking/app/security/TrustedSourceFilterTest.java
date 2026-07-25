package com.shaopc.worthit.tracking.app.security;

import cn.dev33.satoken.exception.SameTokenInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedSourceFilterTest {

    private final AtomicReference<Object> downstreamTraceId =
            new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrustedSourceFilter filter = new TrustedSourceFilter(
            token -> {
                if (!"same-token-valid".equals(token)) {
                    throw new SameTokenInvalidException(token);
                }
            },
            () -> "trace-generated",
            objectMapper);

    @Test
    void rejectsMissingOrIncorrectSameTokenWithUnifiedForbiddenResponse()
            throws Exception {
        assertForbidden(execute("/internal/jobs", null, null));
        assertForbidden(execute("/api/items", "same-token-wrong", "trace-forged"));
    }

    @Test
    void allowsValidSameTokenAndReturnsTrustedTraceId() throws Exception {
        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid", "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).isEqualTo("reached");
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-gateway");
        assertThat(downstreamTraceId.get()).isEqualTo("trace-gateway");
    }

    @Test
    void skipsHealthAndDocumentationPaths() throws Exception {
        for (String path : new String[]{
                "/actuator/health",
                "/v3/api-docs/public",
                "/swagger-ui/index.html"}) {
            MockHttpServletResponse response = execute(path, null, null);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getContentAsString()).isEqualTo("reached");
        }
    }

    private MockHttpServletResponse execute(
            String path, String sameToken, String traceId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (sameToken != null) {
            request.addHeader(SecurityHeaderNames.SAME_TOKEN, sameToken);
        }
        if (traceId != null) {
            request.addHeader(SecurityHeaderNames.TRACE_ID, traceId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    downstreamTraceId.set(servletRequest.getAttribute(
                            SecurityHeaderNames.TRACE_ID));
                    servletResponse.getWriter().write("reached");
                });
        return response;
    }

    private void assertForbidden(MockHttpServletResponse response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(body.path("success").booleanValue()).isFalse();
        assertThat(body.path("code").textValue()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(body.path("traceId").textValue()).isEqualTo("trace-generated");
    }
}
