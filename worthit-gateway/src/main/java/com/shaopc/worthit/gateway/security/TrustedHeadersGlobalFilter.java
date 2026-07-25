package com.shaopc.worthit.gateway.security;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * 清理外部请求伪造的内部头并重建可信链路头。
 */
@Component
public final class TrustedHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> UNTRUSTED_HEADERS = List.of(
            SecurityHeaderNames.SAME_TOKEN,
            SecurityHeaderNames.CALLER_SERVICE,
            SecurityHeaderNames.USER_ID,
            SecurityHeaderNames.SESSION_ID,
            SecurityHeaderNames.TRACE_ID);

    private final TraceIdGenerator traceIdGenerator;
    private final SameTokenProvider sameTokenProvider;

    /**
     * 创建可信请求头过滤器。
     *
     * @param traceIdGenerator TraceId 生成器
     * @param sameTokenProvider Same-Token 提供器
     */
    public TrustedHeadersGlobalFilter(
            TraceIdGenerator traceIdGenerator,
            SameTokenProvider sameTokenProvider) {
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
        this.sameTokenProvider = Objects.requireNonNull(
                sameTokenProvider, "Same-Token提供器不能为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = requireText(traceIdGenerator.generate(), "TraceId");
        String sameToken = requireText(sameTokenProvider.currentToken(), "Same-Token");
        ServerWebExchange trustedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> rebuildTrustedHeaders(
                                headers, traceId, sameToken))
                        .build())
                .build();
        return chain.filter(trustedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void rebuildTrustedHeaders(
            HttpHeaders headers, String traceId, String sameToken) {
        UNTRUSTED_HEADERS.forEach(headers::remove);
        headers.set(SecurityHeaderNames.TRACE_ID, traceId);
        headers.set(SecurityHeaderNames.SAME_TOKEN, sameToken);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }
}
