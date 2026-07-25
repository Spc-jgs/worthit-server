package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.reactor.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySaTokenSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewaySecurityErrorWriter errorWriter =
            new GatewaySecurityErrorWriter(objectMapper, () -> "trace-generated");
    private final SaReactorFilter filter =
            new GatewaySaTokenConfiguration().saReactorFilter(errorWriter);

    @BeforeAll
    static void initializeSaTokenRouteMatcher() {
        new SaTokenContextRegister();
    }

    @Test
    void rejectsMissingLoginWithUnifiedUnauthorizedResponse() throws Exception {
        AtomicBoolean reached = new AtomicBoolean();
        MockServerWebExchange exchange = exchange("/api/v1/items");

        withLoginState(false, () -> StepVerifier.create(filter.filter(
                        exchange,
                        ignored -> {
                            reached.set(true);
                            return Mono.empty();
                        }))
                .verifyComplete());

        JsonNode body = objectMapper.readTree(
                exchange.getResponse().getBodyAsString().block());
        assertThat(reached).isFalse();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-generated");
        assertThat(body.path("success").booleanValue()).isFalse();
        assertThat(body.path("code").textValue())
                .isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(body.path("traceId").textValue())
                .isEqualTo("trace-generated");
        assertThat(body.toString())
                .doesNotContain("NotLoginException", "token-forged");
    }

    @Test
    void allowsValidLoginToReachGatewayChain() {
        AtomicBoolean reached = new AtomicBoolean();

        withLoginState(true, () -> StepVerifier.create(filter.filter(
                        exchange("/api/v1/items"),
                        ignored -> {
                            reached.set(true);
                            return Mono.empty();
                        }))
                .verifyComplete());

        assertThat(reached).isTrue();
    }

    @Test
    void loginAndHealthPathsDoNotRequireExistingLogin() {
        for (String path : new String[]{
                "/api/v1/auth/wechat/login",
                "/actuator/health",
                "/actuator/health/readiness"}) {
            AtomicBoolean reached = new AtomicBoolean();
            withLoginState(false, () -> StepVerifier.create(filter.filter(
                            exchange(path),
                            ignored -> {
                                reached.set(true);
                                return Mono.empty();
                            }))
                    .verifyComplete());
            assertThat(reached)
                    .as("path %s should reach the chain", path)
                    .isTrue();
        }
    }

    @Test
    void excludedLoginPathStillUsesTrustedHeaderRebuild() {
        TrustedHeadersGlobalFilter trustedHeaders = new TrustedHeadersGlobalFilter(
                () -> "trace-trusted",
                () -> "same-token-trusted");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/wechat/login")
                        .header(SecurityHeaderNames.SAME_TOKEN, "same-token-forged")
                        .header(SecurityHeaderNames.CALLER_SERVICE, "caller-forged")
                        .header(SecurityHeaderNames.USER_ID, "user-forged")
                        .header(SecurityHeaderNames.SESSION_ID, "session-forged")
                        .header(SecurityHeaderNames.TRACE_ID, "trace-forged")
                        .build());

        withLoginState(false, () -> StepVerifier.create(filter.filter(
                        exchange,
                        authenticatedExchange -> trustedHeaders.filter(
                                authenticatedExchange,
                                trustedExchange -> {
                                    downstream.set(trustedExchange);
                                    return Mono.empty();
                                })))
                .verifyComplete());

        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(SecurityHeaderNames.SAME_TOKEN))
                .isEqualTo("same-token-trusted");
        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-trusted");
        assertThat(downstream.get().getRequest().getHeaders()).doesNotContainKeys(
                SecurityHeaderNames.CALLER_SERVICE,
                SecurityHeaderNames.USER_ID,
                SecurityHeaderNames.SESSION_ID);
    }

    @Test
    void freezesAuthenticationAndTrustedHeaderFilterOrders() {
        Order order = SaReactorFilter.class.getAnnotation(Order.class);

        assertThat(order.value()).isEqualTo(SaTokenConsts.ASSEMBLY_ORDER);
        assertThat(new TrustedHeadersGlobalFilter(
                () -> "trace",
                () -> "same-token").getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("worthit-token", "token-forged")
                        .build());
    }

    private static void withLoginState(boolean loggedIn, Runnable assertion) {
        StpLogic original = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override
            public void checkLogin() {
                if (!loggedIn) {
                    throw NotLoginException.newInstance(
                            getLoginType(),
                            NotLoginException.INVALID_TOKEN,
                            NotLoginException.INVALID_TOKEN_MESSAGE,
                            "token-forged");
                }
            }
        });
        try {
            assertion.run();
        } finally {
            StpUtil.setStpLogic(original);
        }
    }
}
