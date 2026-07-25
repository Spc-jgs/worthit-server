package com.shaopc.worthit.gateway.security;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedHeadersGlobalFilterTest {

    @Test
    void removesUntrustedHeadersAndRebuildsOnlyTraceAndSameToken() {
        TrustedHeadersGlobalFilter filter = new TrustedHeadersGlobalFilter(
                () -> "trace-trusted",
                () -> "same-token-trusted");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/items")
                        .header(SecurityHeaderNames.SAME_TOKEN, "same-token-forged")
                        .header(SecurityHeaderNames.CALLER_SERVICE, "caller-forged")
                        .header(SecurityHeaderNames.USER_ID, "1001")
                        .header(SecurityHeaderNames.SESSION_ID, "session-forged")
                        .header(SecurityHeaderNames.TRACE_ID, "trace-forged")
                        .build());
        AtomicReference<ServerWebExchange> downstreamExchange =
                new AtomicReference<>();
        GatewayFilterChain chain = actualExchange -> {
            downstreamExchange.set(actualExchange);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = downstreamExchange.get().getRequest().getHeaders();
        assertThat(headers.getFirst(SecurityHeaderNames.SAME_TOKEN))
                .isEqualTo("same-token-trusted");
        assertThat(headers.getFirst(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-trusted");
        assertThat(headers).doesNotContainKeys(
                SecurityHeaderNames.CALLER_SERVICE,
                SecurityHeaderNames.USER_ID,
                SecurityHeaderNames.SESSION_ID);
    }
}
