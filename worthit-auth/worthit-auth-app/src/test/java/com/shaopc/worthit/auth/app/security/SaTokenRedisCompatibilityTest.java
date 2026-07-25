package com.shaopc.worthit.auth.app.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.shaopc.worthit.auth.app.WorthItAuthApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = WorthItAuthApplication.class,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
class SaTokenRedisCompatibilityTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redis;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("sa-token.token-name", () -> "worthit-token");
        registry.add(
                "sa-token.jwt-secret-key",
                () -> "worthit-test-jwt-secret-key-at-least-thirty-two-bytes");
    }

    @AfterEach
    void clearRequestContext() {
        SaTokenContextJakartaServletUtil.clearContext();
    }

    @Test
    void persistsJwtSimpleLoginStateInRedis() {
        SaTokenContextJakartaServletUtil.setContext(
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(SaManager.getSaTokenDao())
                .isInstanceOf(SaTokenDaoForRedisTemplate.class);
        assertThat(StpUtil.getStpLogic()).isInstanceOf(StpLogicJwtForSimple.class);

        StpUtil.login(1001L);
        String token = StpUtil.getTokenValue();

        assertThat(token).isNotBlank();
        assertThat(StpUtil.getLoginIdAsLong()).isEqualTo(1001L);
        assertThat(redis.keys("worthit-token:*")).isNotEmpty();

        StpUtil.logout();
        assertThatThrownBy(StpUtil::checkLogin).isInstanceOf(NotLoginException.class);
    }
}
