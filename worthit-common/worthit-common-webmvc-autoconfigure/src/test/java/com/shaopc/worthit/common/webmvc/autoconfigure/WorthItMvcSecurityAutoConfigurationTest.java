package com.shaopc.worthit.common.webmvc.autoconfigure;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SaTokenSameTokenVerifier;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import com.shaopc.worthit.common.webmvc.security.PublicAuthenticationFilter;
import com.shaopc.worthit.common.webmvc.security.ServletApiErrorWriter;
import com.shaopc.worthit.common.webmvc.security.TrustedSourceFilter;
import com.shaopc.worthit.common.webmvc.security.UserLoginVerifier;
import com.shaopc.worthit.common.webmvc.trace.TrustedTraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItMvcSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            WorthItTraceAutoConfiguration.class,
                            WorthItMvcSecurityAutoConfiguration.class));

    @Test
    void createsSharedMvcSecurityRuntimeByDefault() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(StpLogic.class);
            assertThat(context.getBean(StpLogic.class))
                    .isInstanceOf(StpLogicJwtForSimple.class);
            assertThat(context).hasSingleBean(SaTokenSameTokenProvider.class);
            assertThat(context).hasSingleBean(SaTokenSameTokenVerifier.class);
            assertThat(context).hasSingleBean(SameTokenProvider.class);
            assertThat(context).hasSingleBean(SameTokenVerifier.class);
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context).hasSingleBean(UserLoginVerifier.class);
            assertThat(context)
                    .hasSingleBean(PublicRequestAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(ServletApiErrorWriter.class);
            assertThat(context).hasSingleBean(TrustedSourceFilter.class);
            assertThat(context)
                    .hasSingleBean(PublicAuthenticationFilter.class);

            FilterRegistrationBean<?> sourceRegistration = context.getBean(
                    "trustedSourceFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> authenticationRegistration =
                    context.getBean(
                            "publicAuthenticationFilterRegistration",
                            FilterRegistrationBean.class);
            assertThat(sourceRegistration.getFilter())
                    .isSameAs(context.getBean(TrustedSourceFilter.class));
            assertThat(sourceRegistration.getOrder())
                    .isEqualTo(Integer.MIN_VALUE + 10);
            assertThat(authenticationRegistration.getFilter())
                    .isSameAs(context.getBean(
                            PublicAuthenticationFilter.class));
            assertThat(authenticationRegistration.getOrder())
                    .isEqualTo(Integer.MIN_VALUE + 30);

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
    void backsOffOnlySameTokenProviderAndKeepsDefaultVerifier() {
        webContextRunner
                .withUserConfiguration(
                        SameTokenProviderOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SameTokenProvider.class);
                    assertThat(context.getBean(SameTokenProvider.class))
                            .isSameAs(context.getBean(
                                    "customSameTokenProvider",
                                    SameTokenProvider.class));
                    assertThat(context).hasSingleBean(SameTokenVerifier.class);
                    assertThat(context).hasSingleBean(TrustedSourceFilter.class);
                });
    }

    @Test
    void backsOffOnlySameTokenVerifierAndKeepsDefaultProvider() {
        webContextRunner
                .withUserConfiguration(
                        SameTokenVerifierOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SameTokenVerifier.class);
                    assertThat(context.getBean(SameTokenVerifier.class))
                            .isSameAs(context.getBean(
                                    "customSameTokenVerifier",
                                    SameTokenVerifier.class));
                    assertThat(context).hasSingleBean(SameTokenProvider.class);
                    assertThat(context).hasSingleBean(TrustedSourceFilter.class);
                });
    }

    @Test
    void backsOffForBothSameTokenOverrides() {
        webContextRunner
                .withUserConfiguration(SameTokenOverridesConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SameTokenProvider.class);
                    assertThat(context).hasSingleBean(SameTokenVerifier.class);
                    assertThat(context.getBean(SameTokenProvider.class))
                            .isSameAs(context.getBean(
                                    "customSameTokenProvider",
                                    SameTokenProvider.class));
                    assertThat(context.getBean(SameTokenVerifier.class))
                            .isSameAs(context.getBean(
                                    "customSameTokenVerifier",
                                    SameTokenVerifier.class));
                    assertThat(context).hasSingleBean(TrustedSourceFilter.class);
                });
    }

    @Test
    void doesNotCreateSecurityRuntimeOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        WorthItTraceAutoConfiguration.class,
                        WorthItMvcSecurityAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(TrustedSourceFilter.class)
                        .doesNotHaveBean(PublicAuthenticationFilter.class));
    }

    @Test
    void doesNotCreateSecurityRuntimeWhenDisabled() {
        webContextRunner
                .withPropertyValues("worthit.security.mvc.enabled=false")
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(StpLogic.class)
                            .doesNotHaveBean(TrustedSourceFilter.class)
                            .doesNotHaveBean(PublicAuthenticationFilter.class);
                    assertThat(context)
                            .hasSingleBean(TrustedTraceIdFilter.class);
                });
    }

    @Test
    void keepsSecurityRuntimeWhenTraceFilterIsDisabled() {
        webContextRunner
                .withPropertyValues("worthit.web.trace.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context)
                            .doesNotHaveBean(TrustedTraceIdFilter.class);
                    assertThat(context)
                            .hasSingleBean(TrustedSourceFilter.class);
                    assertThat(context)
                            .hasSingleBean(PublicAuthenticationFilter.class);
                });
    }

    @Test
    void doesNotCreateSecurityRuntimeWhenSaTokenIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader(StpLogic.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(TrustedSourceFilter.class)
                        .doesNotHaveBean(PublicAuthenticationFilter.class));
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

    @TestConfiguration(proxyBeanMethods = false)
    static class SameTokenProviderOverrideConfiguration {

        @Bean
        SameTokenProvider customSameTokenProvider() {
            return () -> "custom-provider-token";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SameTokenVerifierOverrideConfiguration {

        @Bean
        SameTokenVerifier customSameTokenVerifier() {
            return token -> {
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SameTokenOverridesConfiguration {

        @Bean
        SameTokenProvider customSameTokenProvider() {
            return () -> "custom-provider-token";
        }

        @Bean
        SameTokenVerifier customSameTokenVerifier() {
            return token -> {
            };
        }
    }
}
