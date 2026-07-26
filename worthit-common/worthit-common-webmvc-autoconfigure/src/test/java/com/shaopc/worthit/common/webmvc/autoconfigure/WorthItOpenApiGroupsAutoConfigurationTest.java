package com.shaopc.worthit.common.webmvc.autoconfigure;

import com.shaopc.worthit.common.webmvc.openapi.OpenApiGroupConstants;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItOpenApiGroupsAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            WorthItOpenApiGroupsAutoConfiguration.class));

    @Test
    void doesNotCreateGroupsWhenApiDocsAreDisabledByDefault() {
        webContextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
                .doesNotHaveBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME));
    }

    @Test
    void createsPublicAndInternalGroupsWhenEnabled() {
        webContextRunner
                .withPropertyValues(
                        "worthit.web.openapi.enabled=true",
                        "springdoc.api-docs.enabled=true")
                .run(context -> {
                    assertThat(context)
                            .hasBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
                            .hasBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME);
                    assertThat(context.getBean(
                            OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME,
                            GroupedOpenApi.class).getGroup())
                            .isEqualTo(OpenApiGroupConstants.PUBLIC_GROUP_NAME);
                    assertThat(context.getBean(
                            OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME,
                            GroupedOpenApi.class).getGroup())
                            .isEqualTo(OpenApiGroupConstants.INTERNAL_GROUP_NAME);
                });
    }

    @Test
    void doesNotCreateGroupsWhenOnlySpringdocIsEnabled() {
        webContextRunner
                .withPropertyValues("springdoc.api-docs.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(
                                OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
                        .doesNotHaveBean(
                                OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME));
    }

    @Test
    void doesNotCreateGroupsOutsideServletApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WorthItOpenApiGroupsAutoConfiguration.class))
                .withPropertyValues("springdoc.api-docs.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
                        .doesNotHaveBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME));
    }

    @Test
    void doesNotCreateGroupsWhenSpringdocIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader(GroupedOpenApi.class))
                .withPropertyValues(
                        "worthit.web.openapi.enabled=true",
                        "springdoc.api-docs.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(
                                OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
                        .doesNotHaveBean(
                                OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME));
    }

    @Test
    void backsOffWhenApplicationProvidesPublicGroup() {
        webContextRunner
                .withUserConfiguration(PublicGroupOverrideConfiguration.class)
                .withPropertyValues(
                        "worthit.web.openapi.enabled=true",
                        "springdoc.api-docs.enabled=true")
                .run(context -> {
                    GroupedOpenApi publicGroup = context.getBean(
                            OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME,
                            GroupedOpenApi.class);

                    assertThat(publicGroup.getGroup()).isEqualTo("custom-public");
                    assertThat(context)
                            .hasBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PublicGroupOverrideConfiguration {

        @Bean(name = OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
        GroupedOpenApi customPublicGroup() {
            return GroupedOpenApi.builder()
                    .group("custom-public")
                    .pathsToMatch("/custom/**")
                    .build();
        }
    }
}
