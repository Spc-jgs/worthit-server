package com.shaopc.worthit.common.webmvc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertyBindingConfiguration.class);

    @Test
    void appliesDocumentedDefaults() {
        contextRunner.run(context -> {
            WorthItWebProperties webProperties =
                    context.getBean(WorthItWebProperties.class);
            WorthItSecurityProperties securityProperties =
                    context.getBean(WorthItSecurityProperties.class);

            assertThat(webProperties.getTrace().isEnabled()).isTrue();
            assertThat(webProperties.getErrorHandling().isEnabled()).isTrue();
            assertThat(webProperties.getOpenapi().isEnabled()).isFalse();
            assertThat(securityProperties.getMvc().isEnabled()).isTrue();
        });
    }

    @Test
    void bindsIndependentFeatureSwitches() {
        contextRunner
                .withPropertyValues(
                        "worthit.web.trace.enabled=false",
                        "worthit.web.error-handling.enabled=false",
                        "worthit.web.openapi.enabled=true",
                        "worthit.security.mvc.enabled=false")
                .run(context -> {
                    WorthItWebProperties webProperties =
                            context.getBean(WorthItWebProperties.class);
                    WorthItSecurityProperties securityProperties =
                            context.getBean(WorthItSecurityProperties.class);

                    assertThat(webProperties.getTrace().isEnabled()).isFalse();
                    assertThat(webProperties.getErrorHandling().isEnabled())
                            .isFalse();
                    assertThat(webProperties.getOpenapi().isEnabled()).isTrue();
                    assertThat(securityProperties.getMvc().isEnabled()).isFalse();
                });
    }

    @Test
    void publishesExactPropertyNamesAndDefaults() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();

            JsonNode properties = new ObjectMapper()
                    .readTree(input)
                    .path("properties");
            Map<String, Boolean> actual = new LinkedHashMap<>();
            properties.forEach(property -> actual.put(
                    property.path("name").asText(),
                    property.path("defaultValue").asBoolean()));

            assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "worthit.web.trace.enabled", true,
                    "worthit.web.error-handling.enabled", true,
                    "worthit.web.openapi.enabled", false,
                    "worthit.security.mvc.enabled", true));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            WorthItWebProperties.class,
            WorthItSecurityProperties.class
    })
    static class PropertyBindingConfiguration {
    }
}
