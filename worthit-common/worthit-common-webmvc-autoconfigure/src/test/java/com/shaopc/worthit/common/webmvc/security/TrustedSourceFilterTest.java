package com.shaopc.worthit.common.webmvc.security;

import cn.dev33.satoken.exception.SameTokenInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedSourceFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean reachedChain = new AtomicBoolean();
    private final AtomicReference<Object> downstreamTrustedSource =
            new AtomicReference<>();
    private TrustedSourceFilter filter;

    @BeforeEach
    void setUp() {
        reachedChain.set(false);
        downstreamTrustedSource.set(null);
        ServletApiErrorWriter errorWriter = new ServletApiErrorWriter(
                objectMapper,
                () -> "trace-generated");
        filter = new TrustedSourceFilter(
                token -> {
                    if (!"same-token-valid".equals(token)) {
                        throw new SameTokenInvalidException(token);
                    }
                },
                errorWriter);
    }

    @Test
    void rejectsMissingOrIncorrectSameTokenWithoutMarkingSourceTrusted()
            throws Exception {
        assertForbidden(execute("/internal/jobs", null));
        assertForbidden(execute("/api/items", "same-token-wrong"));
        assertThat(reachedChain).isFalse();
        assertThat(downstreamTrustedSource).hasValue(null);
    }

    @Test
    void marksSourceTrustedAfterSameTokenVerification() throws Exception {
        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).isEqualTo("reached");
        assertThat(reachedChain).isTrue();
        assertThat(downstreamTrustedSource).hasValue(true);
    }

    @Test
    void skipsNonApiAndDocumentationPaths() throws Exception {
        for (String path : new String[]{
                "/actuator/health",
                "/v3/api-docs/public",
                "/swagger-ui/index.html",
                "/apis/not-protected",
                "/internals/not-protected"}) {
            MockHttpServletResponse response = execute(path, null);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getContentAsString()).isEqualTo("reached");
        }
        assertThat(downstreamTrustedSource).hasValue(null);
    }

    private MockHttpServletResponse execute(String path, String sameToken)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (sameToken != null) {
            request.addHeader(SecurityHeaderNames.SAME_TOKEN, sameToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    reachedChain.set(true);
                    downstreamTrustedSource.set(servletRequest.getAttribute(
                            TrustedRequestAttributes.TRUSTED_SOURCE));
                    servletResponse.getWriter().write("reached");
                });
        return response;
    }

    private void assertForbidden(MockHttpServletResponse response)
            throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("code").asText()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(body.path("traceId").asText()).isEqualTo("trace-generated");
    }
}
