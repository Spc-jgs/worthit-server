package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.util.SaTokenConsts;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 将公网 Bearer 凭证规范化为 Sa-Token 内部请求头。
 */
@Component
public final class BearerTokenNormalizationWebFilter
        implements WebFilter, Ordered {

    private static final String BEARER_SCHEME = "Bearer";
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "[A-Za-z0-9\\-._~+/]+=*");

    private final String internalTokenHeader;

    /**
     * 创建 Bearer 凭证规范化过滤器。
     *
     * @param internalTokenHeader Sa-Token 内部请求头名称
     */
    public BearerTokenNormalizationWebFilter(
            @Value("${sa-token.token-name:satoken}")
            String internalTokenHeader) {
        this.internalTokenHeader = requireInternalHeader(
                internalTokenHeader);
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders sourceHeaders = exchange.getRequest().getHeaders();
        Optional<String> bearerToken = extractBearerToken(
                sourceHeaders.get(SecurityHeaderNames.AUTHORIZATION));
        ServerWebExchange normalizedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(internalTokenHeader);
                            bearerToken.ifPresent(token ->
                                    headers.set(internalTokenHeader, token));
                        })
                        .build())
                .build();
        return chain.filter(normalizedExchange);
    }

    @Override
    public int getOrder() {
        return SaTokenConsts.ASSEMBLY_ORDER - 1;
    }

    private static Optional<String> extractBearerToken(
            List<String> authorizationHeaders) {
        if (authorizationHeaders == null
                || authorizationHeaders.size() != 1) {
            return Optional.empty();
        }
        String authorization = authorizationHeaders.get(0);
        if (authorization == null
                || authorization.length() <= BEARER_SCHEME.length()
                || !authorization.regionMatches(
                        true,
                        0,
                        BEARER_SCHEME,
                        0,
                        BEARER_SCHEME.length())) {
            return Optional.empty();
        }
        int tokenStart = BEARER_SCHEME.length();
        if (authorization.charAt(tokenStart) != ' ') {
            return Optional.empty();
        }
        while (tokenStart < authorization.length()
                && authorization.charAt(tokenStart) == ' ') {
            tokenStart++;
        }
        if (tokenStart == authorization.length()) {
            return Optional.empty();
        }
        String token = authorization.substring(tokenStart);
        return BEARER_TOKEN.matcher(token).matches()
                ? Optional.of(token)
                : Optional.empty();
    }

    private static String requireInternalHeader(String headerName) {
        String normalized = Objects.requireNonNull(
                headerName, "Sa-Token请求头名称不能为空").trim();
        if (normalized.isEmpty()
                || SecurityHeaderNames.AUTHORIZATION
                .equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException(
                    "Sa-Token内部请求头必须与Authorization隔离");
        }
        return normalized;
    }
}
