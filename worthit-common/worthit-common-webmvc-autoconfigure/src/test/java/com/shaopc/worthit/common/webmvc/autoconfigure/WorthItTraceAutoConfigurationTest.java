package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.core.trace.UuidTraceIdGenerator;
import com.shaopc.worthit.common.webmvc.trace.TrustedTraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItTraceAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            WorthItTraceAutoConfiguration.class));

    @Test
    void createsTraceIdGeneratorByDefault() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context.getBean(TraceIdGenerator.class))
                    .isInstanceOf(UuidTraceIdGenerator.class);
            assertThat(context).hasSingleBean(TrustedTraceIdFilter.class);

            FilterRegistrationBean<?> registration = context.getBean(
                    "trustedTraceIdFilterRegistration",
                    FilterRegistrationBean.class);
            assertThat(registration.getFilter())
                    .isSameAs(context.getBean(TrustedTraceIdFilter.class));
            assertThat(registration.getOrder())
                    .isEqualTo(Integer.MIN_VALUE + 20);
        });
    }

    @Test
    void backsOffForApplicationTraceIdGenerator() {
        webContextRunner
                .withUserConfiguration(TraceOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context.getBean(TraceIdGenerator.class))
                            .isSameAs(context.getBean(
                                    "customTraceIdGenerator",
                                    TraceIdGenerator.class));
                });
    }

    @Test
    void doesNotCreateTraceRuntimeWhenDisabled() {
        webContextRunner
                .withPropertyValues("worthit.web.trace.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context)
                            .doesNotHaveBean(TrustedTraceIdFilter.class);
                    assertThat(context).doesNotHaveBean(
                            "trustedTraceIdFilterRegistration");
                });
    }

    @Test
    void doesNotCreateTraceRuntimeOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WorthItTraceAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(TraceIdGenerator.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TraceOverrideConfiguration {

        @Bean
        TraceIdGenerator customTraceIdGenerator() {
            return () -> "trace-custom";
        }
    }
}
