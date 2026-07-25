package com.shaopc.worthit.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInfraProbeRouteContractTest {

    private final Path repositoryRoot =
            Path.of(System.getProperty("user.dir")).getParent();
    private final Path templateDirectory =
            repositoryRoot.resolve("deploy/nacos/local");
    private final YamlPropertySourceLoader loader =
            new YamlPropertySourceLoader();

    @Test
    void localTemplateRoutesOnlyExplicitProbePathsThroughDiscovery()
            throws IOException {
        PropertySource<?> gateway = load("worthit-gateway.yaml");
        String yaml = read(templateDirectory.resolve("worthit-gateway.yaml"));

        assertRoute(
                gateway,
                3,
                "local-infra-auth-readiness",
                "lb://worthit-auth",
                "Path=/__infra/auth/readiness",
                "SetPath=/actuator/health/readiness");
        assertRoute(
                gateway,
                4,
                "local-infra-reminder-ping",
                "lb://worthit-reminder",
                "Path=/__infra/reminder/ping",
                "SetPath=/internal/__infra/ping");
        assertRoute(
                gateway,
                5,
                "local-infra-tracking-reminder-ping",
                "lb://worthit-tracking",
                "Path=/__infra/tracking/reminder/ping",
                "SetPath=/internal/__infra/reminder/ping");
        assertRoute(
                gateway,
                6,
                "local-infra-tracking-config",
                "lb://worthit-tracking",
                "Path=/__infra/tracking/config",
                "SetPath=/internal/__infra/config");
        assertThat(yaml).doesNotContain(
                "http://127.0.0.1",
                "localhost");
        assertThat(yaml)
                .doesNotContainPattern("(?m)^\\s*- Path=/internal");
    }

    @Test
    void commonTemplateAndSyncScriptOwnRefreshProbeContract()
            throws IOException {
        PropertySource<?> common = load("worthit-common.yaml");
        String script = read(
                repositoryRoot.resolve(
                        "scripts/local-infra/nacos-config.sh"));

        assertThat(common.getProperty("worthit.runtime.probe-message"))
                .isEqualTo("phase0-local");
        assertThat(script).contains(
                "WORTHIT_PROBE_MESSAGE",
                "set-probe-message)",
                "set_probe_message")
                .doesNotContain("delete)");
    }

    private void assertRoute(
            PropertySource<?> gateway,
            int index,
            String id,
            String uri,
            String predicate,
            String filter) {
        String prefix = "spring.cloud.gateway.routes[" + index + "]";
        assertThat(gateway.getProperty(prefix + ".id")).isEqualTo(id);
        assertThat(gateway.getProperty(prefix + ".uri")).isEqualTo(uri);
        assertThat(gateway.getProperty(prefix + ".predicates[0]"))
                .isEqualTo(predicate);
        assertThat(gateway.getProperty(prefix + ".filters[0]"))
                .isEqualTo(filter);
    }

    private PropertySource<?> load(String dataId) throws IOException {
        Path template = templateDirectory.resolve(dataId);
        return loader.load(dataId, new FileSystemResource(template)).get(0);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
