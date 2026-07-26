package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.ErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.RemoteServiceExceptionHandler;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItErrorHandlingAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            WorthItTraceAutoConfiguration.class,
                            WorthItErrorHandlingAutoConfiguration.class));

    @Test
    void createsUnifiedExceptionRuntimeByDefault() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(ErrorHttpStatusResolver.class);
            assertThat(context.getBean(ErrorHttpStatusResolver.class))
                    .isInstanceOf(DefaultErrorHttpStatusResolver.class);
            assertThat(context)
                    .hasSingleBean(WorthItRestExceptionHandler.class);
            assertThat(context)
                    .hasSingleBean(RemoteServiceExceptionHandler.class);
        });
    }

    @Test
    void backsOffForApplicationStatusResolver() {
        webContextRunner
                .withUserConfiguration(StatusResolverOverride.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(ErrorHttpStatusResolver.class);
                    assertThat(context.getBean(ErrorHttpStatusResolver.class))
                            .isSameAs(context.getBean(
                                    "customErrorHttpStatusResolver",
                                    ErrorHttpStatusResolver.class));
                    assertThat(context)
                            .hasSingleBean(WorthItRestExceptionHandler.class);
                });
    }

    @Test
    void doesNotCreateExceptionRuntimeWhenDisabled() {
        webContextRunner
                .withPropertyValues(
                        "worthit.web.error-handling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ErrorHttpStatusResolver.class)
                        .doesNotHaveBean(WorthItRestExceptionHandler.class)
                        .doesNotHaveBean(RemoteServiceExceptionHandler.class));
    }

    @Test
    void keepsExceptionRuntimeWhenTraceFilterIsDisabled() {
        webContextRunner
                .withPropertyValues("worthit.web.trace.enabled=false")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(WorthItRestExceptionHandler.class);
                    assertThat(context)
                            .hasSingleBean(RemoteServiceExceptionHandler.class);
                });
    }

    @Test
    void keepsCoreHandlerWhenCommonHttpIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader(
                        RemoteServiceException.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(WorthItRestExceptionHandler.class)
                        .doesNotHaveBean(RemoteServiceExceptionHandler.class));
    }

    @Test
    void doesNotCreateExceptionRuntimeOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WorthItTraceAutoConfiguration.class,
                        WorthItErrorHandlingAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(WorthItRestExceptionHandler.class)
                        .doesNotHaveBean(RemoteServiceExceptionHandler.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StatusResolverOverride {

        @Bean
        ErrorHttpStatusResolver customErrorHttpStatusResolver() {
            return errorCode -> HttpStatus.UNPROCESSABLE_ENTITY;
        }
    }
}
