package com.shaopc.worthit.common.http.interceptor;

import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRequestHeadersInterceptorTest {

    @Test
    void overwritesTrustedHeadersBeforeExecutingRequest() throws Exception {
        InternalRequestContext context = new InternalRequestContext(
                "worthit-tracking",
                () -> "same-token-test",
                () -> "trace-test");
        InternalRequestHeadersInterceptor interceptor =
                new InternalRequestHeadersInterceptor(context);
        MockClientHttpRequest request =
                new MockClientHttpRequest(HttpMethod.GET, URI.create("http://example.test"));
        request.getHeaders().set(SecurityHeaderNames.SAME_TOKEN, "untrusted-same");
        request.getHeaders().set(SecurityHeaderNames.CALLER_SERVICE, "untrusted-caller");
        request.getHeaders().set(SecurityHeaderNames.TRACE_ID, "untrusted-trace");
        AtomicReference<HttpHeaders> capturedHeaders = new AtomicReference<>();

        interceptor.intercept(request, new byte[0], (actualRequest, body) -> {
            capturedHeaders.set(HttpHeaders.readOnlyHttpHeaders(
                    new HttpHeaders(actualRequest.getHeaders())));
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        });

        HttpHeaders headers = capturedHeaders.get();
        assertThat(headers.getFirst(SecurityHeaderNames.SAME_TOKEN))
                .isEqualTo("same-token-test");
        assertThat(headers.getFirst(SecurityHeaderNames.CALLER_SERVICE))
                .isEqualTo("worthit-tracking");
        assertThat(headers.getFirst(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-test");
    }
}
