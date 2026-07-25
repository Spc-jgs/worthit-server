package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenService;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import com.shaopc.worthit.common.webmvc.security.TrustedSourceFilter;
import com.shaopc.worthit.common.webmvc.security.UserLoginVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItMvcSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            WorthItMvcSecurityAutoConfiguration.class));

    @Test
    void createsSharedMvcSecurityRuntimeByDefault() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(StpLogic.class);
            assertThat(context.getBean(StpLogic.class))
                    .isInstanceOf(StpLogicJwtForSimple.class);
            assertThat(context).hasSingleBean(SaTokenSameTokenService.class);
            assertThat(context).hasSingleBean(SameTokenProvider.class);
            assertThat(context).hasSingleBean(SameTokenVerifier.class);
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context).hasSingleBean(UserLoginVerifier.class);
            assertThat(context)
                    .hasSingleBean(PublicRequestAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(TrustedSourceFilter.class);

            PublicRequestAuthorizationPolicy policy =
                    context.getBean(PublicRequestAuthorizationPolicy.class);
            assertThat(policy.requiresLogin("/api/items")).isTrue();
        });
    }

    @Test
    void backsOffForApplicationAuthorizationOverrides() {
        webContextRunner
                .withUserConfiguration(SecurityOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(UserLoginVerifier.class);
                    assertThat(context)
                            .hasSingleBean(PublicRequestAuthorizationPolicy.class);
                    assertThat(context.getBean(UserLoginVerifier.class))
                            .isSameAs(context.getBean(
                                    "customUserLoginVerifier",
                                    UserLoginVerifier.class));
                    assertThat(context.getBean(
                            PublicRequestAuthorizationPolicy.class)
                            .requiresLogin("/api/v1/auth/wechat/login"))
                            .isFalse();
                    assertThat(context).hasSingleBean(TrustedSourceFilter.class);
                });
    }

    @Test
    void doesNotCreateSecurityRuntimeOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        WorthItMvcSecurityAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(TrustedSourceFilter.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityOverrideConfiguration {

        @Bean
        UserLoginVerifier customUserLoginVerifier() {
            return () -> {
            };
        }

        @Bean
        PublicRequestAuthorizationPolicy customPublicRequestAuthorizationPolicy() {
            return path -> !"/api/v1/auth/wechat/login".equals(path);
        }
    }
}
