package com.shaopc.worthit.common.webmvc.security;

import cn.dev33.satoken.exception.NotLoginException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedSourceFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean userLoginValid = new AtomicBoolean(true);
    private final AtomicBoolean publicRequestRequiresLogin =
            new AtomicBoolean(true);
    private final AtomicInteger userLoginChecks = new AtomicInteger();
    private final AtomicReference<Object> downstreamTraceId =
            new AtomicReference<>();
    private TrustedSourceFilter filter;

    @BeforeEach
    void setUp() {
        userLoginValid.set(true);
        publicRequestRequiresLogin.set(true);
        userLoginChecks.set(0);
        downstreamTraceId.set(null);
        filter = new TrustedSourceFilter(
                token -> {
                    if (!"same-token-valid".equals(token)) {
                        throw new SameTokenInvalidException(token);
                    }
                },
                () -> "trace-generated",
                objectMapper,
                () -> {
                    userLoginChecks.incrementAndGet();
                    if (!userLoginValid.get()) {
                        throw notLoggedIn();
                    }
                },
                path -> publicRequestRequiresLogin.get());
    }

    @Test
    void rejectsMissingOrIncorrectSameTokenWithUnifiedForbiddenResponse()
            throws Exception {
        assertForbidden(execute("/internal/jobs", null, null));
        assertForbidden(execute(
                "/api/items",
                "same-token-wrong",
                "trace-untrusted"));
    }

    @Test
    void propagatesTrustedTraceIdToRequestAndResponse() throws Exception {
        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid", "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).isEqualTo("reached");
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-gateway");
        assertThat(downstreamTraceId.get()).isEqualTo("trace-gateway");
        assertThat(userLoginChecks).hasValue(1);
    }

    @Test
    void generatesTraceIdWhenTrustedHeaderIsMissingOrBlank() throws Exception {
        MockHttpServletResponse missing =
                execute("/internal/jobs", "same-token-valid", null);
        MockHttpServletResponse blank =
                execute("/internal/jobs", "same-token-valid", " ");

        assertThat(missing.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(blank.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(downstreamTraceId.get()).isEqualTo("trace-generated");
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
        assertThat(response.getContentAsString()).isEqualTo("reached");
        assertThat(userLoginChecks).hasValue(0);
    }

    @Test
    void internalPathNeverChecksUserLogin() throws Exception {
        userLoginValid.set(false);

        MockHttpServletResponse response =
                execute("/internal/jobs", "same-token-valid", "trace-gateway");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(userLoginChecks).hasValue(0);
    }

    @Test
    void rejectsPublicApiWithoutUserLoginAndDoesNotReachChain() throws Exception {
        userLoginValid.set(false);

        MockHttpServletResponse response =
                execute("/api/items", "same-token-valid", "trace-gateway");

        assertUnauthorized(response, "trace-gateway");
        assertThat(response.getContentAsString()).doesNotContain("reached");
    }

    @Test
    void skipsNonApiAndDocumentationPaths() throws Exception {
        for (String path : new String[]{
                "/actuator/health",
                "/v3/api-docs/public",
                "/swagger-ui/index.html",
                "/apis/not-protected",
                "/internals/not-protected"}) {
            MockHttpServletResponse response = execute(path, null, null);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getContentAsString()).isEqualTo("reached");
        }
        assertThat(userLoginChecks).hasValue(0);
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

    private void assertForbidden(MockHttpServletResponse response)
            throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(body.path("success").booleanValue()).isFalse();
        assertThat(body.path("code").textValue()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(body.path("traceId").textValue())
                .isEqualTo("trace-generated");
    }

    private void assertUnauthorized(
            MockHttpServletResponse response, String expectedTraceId)
            throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.path("success").booleanValue()).isFalse();
        assertThat(body.path("code").textValue())
                .isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(body.path("traceId").textValue()).isEqualTo(expectedTraceId);
    }

    private static NotLoginException notLoggedIn() {
        return NotLoginException.newInstance(
                "login",
                NotLoginException.NOT_TOKEN,
                NotLoginException.NOT_TOKEN_MESSAGE,
                null);
    }
}
