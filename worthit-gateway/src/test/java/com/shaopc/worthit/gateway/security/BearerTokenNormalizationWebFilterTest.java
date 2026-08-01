package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.util.SaTokenConsts;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenNormalizationWebFilterTest {

    private static final String INTERNAL_TOKEN_HEADER = "worthit-token";

    private final BearerTokenNormalizationWebFilter filter =
            new BearerTokenNormalizationWebFilter(INTERNAL_TOKEN_HEADER);

    @Test
    void translatesSingleBearerTokenAndOverwritesForgedInternalHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items")
                        .header(SecurityHeaderNames.AUTHORIZATION,
                                "Bearer token-trusted")
                        .header(INTERNAL_TOKEN_HEADER, "token-forged")
                        .build());

        HttpHeaders headers = filter(exchange);

        assertThat(headers.getFirst(INTERNAL_TOKEN_HEADER))
                .isEqualTo("token-trusted");
        assertThat(headers.getFirst(SecurityHeaderNames.AUTHORIZATION))
                .isEqualTo("Bearer token-trusted");
    }

    @Test
    void acceptsCaseInsensitiveBearerSchemeAndMultipleSeparatingSpaces() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items")
                        .header(SecurityHeaderNames.AUTHORIZATION,
                                "bEaReR   token-trusted")
                        .build());

        assertThat(filter(exchange).getFirst(INTERNAL_TOKEN_HEADER))
                .isEqualTo("token-trusted");
    }

    @Test
    void removesForgedInternalHeaderWhenAuthorizationIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items")
                        .header(INTERNAL_TOKEN_HEADER, "token-forged")
                        .build());

        assertThat(filter(exchange)).doesNotContainKey(INTERNAL_TOKEN_HEADER);
    }

    @Test
    void doesNotTranslateMalformedAuthorization() {
        for (String value : new String[]{
                "Basic token",
                "Bearer",
                "Bearer   ",
                "Bearer token extra"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/items")
                            .header(SecurityHeaderNames.AUTHORIZATION, value)
                            .header(INTERNAL_TOKEN_HEADER, "token-forged")
                            .build());

            assertThat(filter(exchange))
                    .as("Authorization=%s", value)
                    .doesNotContainKey(INTERNAL_TOKEN_HEADER);
        }
    }

    @Test
    void doesNotTranslateAmbiguousAuthorizationHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items")
                        .header(SecurityHeaderNames.AUTHORIZATION,
                                "Bearer token-first", "Bearer token-second")
                        .header(INTERNAL_TOKEN_HEADER, "token-forged")
                        .build());

        assertThat(filter(exchange)).doesNotContainKey(INTERNAL_TOKEN_HEADER);
    }

    @Test
    void runsBeforeSaTokenAuthenticationFilter() {
        assertThat(filter.getOrder())
                .isLessThan(SaTokenConsts.ASSEMBLY_ORDER);
    }

    private HttpHeaders filter(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        WebFilterChain chain = actualExchange -> {
            downstream.set(actualExchange);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        return downstream.get().getRequest().getHeaders();
    }
}
