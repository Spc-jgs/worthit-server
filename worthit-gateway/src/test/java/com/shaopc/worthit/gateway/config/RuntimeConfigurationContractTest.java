package com.shaopc.worthit.gateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationContractTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void declaresStableRuntimeContractWithoutCommittedSecrets() throws IOException {
        PropertySource<?> base = load("application.yml");
        PropertySource<?> local = load("application-local-infra.yml");

        assertThat(base.getProperty("spring.application.name")).isEqualTo("worthit-gateway");
        assertThat(base.getProperty("server.port")).isEqualTo(18080);
        assertThat(base.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
        assertThat(local.getProperty("spring.config.import[0]"))
                .asString()
                .contains("nacos:worthit-common.yaml");
        assertThat(local.getProperty("spring.cloud.nacos.config.namespace"))
                .isEqualTo("${NACOS_NAMESPACE:worthit-local}");
        assertThat(local.getProperty("spring.datasource.url")).isNull();

        assertSafeYaml("application.yml");
        assertSafeYaml("application-local-infra.yml");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }

    private void assertSafeYaml(String resourceName) throws IOException {
        String yaml = new ClassPathResource(resourceName)
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).doesNotContain(
                "password: root",
                "password: 123456",
                "jwt-secret-key: worthit",
                "spring.redis.");
    }
}
