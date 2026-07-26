package com.shaopc.worthit.common.webmvc.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SameTokenInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.trace.TrustedTraceIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedSecurityFilterChainTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean userLoginValid = new AtomicBoolean(true);
    private final AtomicBoolean publicRequestRequiresLogin =
            new AtomicBoolean(true);
    private final AtomicReference<Object> downstreamTraceId =
            new AtomicReference<>();
    private TrustedSourceFilter trustedSourceFilter;
    private TrustedTraceIdFilter trustedTraceIdFilter;
    private PublicAuthenticationFilter publicAuthenticationFilter;

    @BeforeEach
    void setUp() {
        userLoginValid.set(true);
        publicRequestRequiresLogin.set(true);
        downstreamTraceId.set(null);
        ServletApiErrorWriter errorWriter = new ServletApiErrorWriter(
                objectMapper,
                () -> "trace-generated");
        trustedSourceFilter = new TrustedSourceFilter(
                token -> {
                    if (!"same-token-valid".equals(token)) {
                        throw new SameTokenInvalidException(token);
                    }
                },
                errorWriter);
        trustedTraceIdFilter =
                new TrustedTraceIdFilter(() -> "trace-generated");
        publicAuthenticationFilter = new PublicAuthenticationFilter(
                () -> {
                    if (!userLoginValid.get()) {
                        throw notLoggedIn();
                    }
                },
                path -> publicRequestRequiresLogin.get(),
                errorWriter);
    }

    @Test
    void rejectsInvalidSameTokenWithoutTrustingForgedTraceId()
            throws Exception {
        MockHttpServletResponse response =
                execute("/internal/jobs", "same-token-wrong", "trace-forged");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated")
                .isNotEqualTo("trace-forged");
        assertThat(body.path("traceId").asText()).isEqualTo("trace-generated");
        assertThat(downstreamTraceId).hasValue(null);
    }

    @Test
    void propagatesTraceIdOnlyAfterGatewaySourceIsTrusted() throws Exception {
        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid", "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-gateway");
        assertThat(downstreamTraceId).hasValue("trace-gateway");
    }

    @Test
    void loginFailureUsesTraceIdEstablishedByTrustedTraceFilter()
            throws Exception {
        userLoginValid.set(false);

        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid", "trace-gateway");

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-gateway");
        assertThat(body.path("traceId").asText()).isEqualTo("trace-gateway");
        assertThat(downstreamTraceId).hasValue(null);
    }

    @Test
    void replacesIncomingTraceIdWhenSecurityFilterIsDisabled()
            throws Exception {
        MockHttpServletRequest request =
                request("/api/items", null, "trace-forged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        trustedTraceIdFilter.doFilter(
                request,
                response,
                downstreamChain());

        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated")
                .isNotEqualTo("trace-forged");
        assertThat(downstreamTraceId).hasValue("trace-generated");
    }

    @Test
    void internalPathNeverChecksUserLogin() throws Exception {
        userLoginValid.set(false);

        MockHttpServletResponse response =
                execute("/internal/jobs", "same-token-valid", "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(downstreamTraceId).hasValue("trace-gateway");
    }

    @Test
    void policyCanAllowPublicRequestWithoutUserLogin() throws Exception {
        publicRequestRequiresLogin.set(false);
        userLoginValid.set(false);

        MockHttpServletResponse response = execute(
                "/api/v1/auth/wechat/login",
                "same-token-valid",
                "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(downstreamTraceId).hasValue("trace-gateway");
    }

    @Test
    void allFiltersSkipPathsOutsidePublicAndInternalApis() throws Exception {
        MockHttpServletResponse response =
                execute("/actuator/health", null, "trace-forged");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID)).isNull();
        assertThat(downstreamTraceId).hasValue(null);
    }

    private MockHttpServletResponse execute(
            String path, String sameToken, String traceId) throws Exception {
        MockHttpServletRequest request = request(path, sameToken, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        trustedSourceFilter.doFilter(
                request,
                response,
                (sourceRequest, sourceResponse) ->
                        trustedTraceIdFilter.doFilter(
                                sourceRequest,
                                sourceResponse,
                                (traceRequest, traceResponse) ->
                                        publicAuthenticationFilter.doFilter(
                                                traceRequest,
                                                traceResponse,
                                                downstreamChain())));
        return response;
    }

    private FilterChain downstreamChain() {
        return (request, response) -> {
            downstreamTraceId.set(request.getAttribute(
                    SecurityHeaderNames.TRACE_ID));
            response.getWriter().write("reached");
        };
    }

    private static MockHttpServletRequest request(
            String path, String sameToken, String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (sameToken != null) {
            request.addHeader(SecurityHeaderNames.SAME_TOKEN, sameToken);
        }
        if (traceId != null) {
            request.addHeader(SecurityHeaderNames.TRACE_ID, traceId);
        }
        return request;
    }

    private static NotLoginException notLoggedIn() {
        return NotLoginException.newInstance(
                "login",
                NotLoginException.NOT_TOKEN,
                NotLoginException.NOT_TOKEN_MESSAGE,
                null);
    }
}
