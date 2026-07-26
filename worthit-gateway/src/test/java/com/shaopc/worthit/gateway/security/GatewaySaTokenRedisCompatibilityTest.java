package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.same.SaSameUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenVerifier;
import com.shaopc.worthit.gateway.WorthItGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ServerWebExchange;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = WorthItGatewayApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false"
        })
class GatewaySaTokenRedisCompatibilityTest {

    private static final String SAME_TOKEN_KEY = "worthit-token:var:same-token";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TrustedHeadersGlobalFilter trustedHeadersFilter;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("sa-token.token-name", () -> "worthit-token");
        registry.add(
                "sa-token.jwt-secret-key",
                () -> "worthit-test-jwt-secret-key-at-least-thirty-two-bytes");
    }

    @Test
    void persistsSameTokenAndOverwritesForgedGatewayHeader() {
        assertThat(SaManager.getSaTokenDao())
                .isInstanceOf(SaTokenDaoForRedisTemplate.class);
        assertThat(StpUtil.getStpLogic()).isInstanceOf(StpLogicJwtForSimple.class);

        String token = SaSameUtil.getToken();
        SaTokenSameTokenProvider sameTokenProvider =
                new SaTokenSameTokenProvider();
        SaTokenSameTokenVerifier sameTokenVerifier =
                new SaTokenSameTokenVerifier();

        assertThat(token).isNotBlank();
        assertThat(redis.hasKey(SAME_TOKEN_KEY)).isTrue();
        assertThat(redis.getExpire(SAME_TOKEN_KEY, TimeUnit.SECONDS)).isPositive();
        assertThat(sameTokenProvider.currentToken()).isEqualTo(token);
        assertThatCode(() -> sameTokenVerifier.verify(token))
                .doesNotThrowAnyException();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/items")
                        .header(SecurityHeaderNames.SAME_TOKEN, "same-token-forged")
                        .build());
        AtomicReference<ServerWebExchange> downstreamExchange =
                new AtomicReference<>();
        GatewayFilterChain chain = actualExchange -> {
            downstreamExchange.set(actualExchange);
            return Mono.empty();
        };

        StepVerifier.create(trustedHeadersFilter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = downstreamExchange.get().getRequest().getHeaders();
        assertThat(headers.getFirst(SecurityHeaderNames.SAME_TOKEN)).isEqualTo(token);
    }
}
