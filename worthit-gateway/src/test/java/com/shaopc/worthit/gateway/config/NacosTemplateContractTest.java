package com.shaopc.worthit.gateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class NacosTemplateContractTest {

    private static final List<String> DATA_IDS = List.of(
            "worthit-common.yaml",
            "worthit-gateway.yaml",
            "worthit-auth.yaml",
            "worthit-tracking.yaml",
            "worthit-reminder.yaml");
    private static final Pattern LITERAL_SECRET = Pattern.compile(
            "(?im)^[ \\t]*[^#\\n]*(password|secret|token)[^:]*:[ \\t]*(?!\\$\\{)[^\\s#].*$");

    private final Path repositoryRoot = Path.of(System.getProperty("user.dir")).getParent();
    private final Path templateDirectory = repositoryRoot.resolve("deploy/nacos/local");
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void templatesExistParseAndDoNotContainLiteralSecrets() throws IOException {
        for (String dataId : DATA_IDS) {
            Path template = templateDirectory.resolve(dataId);
            assertThat(template).as(dataId).isRegularFile();
            assertThat(loader.load(dataId, new FileSystemResource(template)))
                    .as(dataId)
                    .isNotEmpty();
            assertThat(LITERAL_SECRET.matcher(read(template)).find())
                    .as(dataId + " literal secret")
                    .isFalse();
        }
    }

    @Test
    void eachTemplateOwnsOnlyItsRuntimeConcern() throws IOException {
        PropertySource<?> common = load("worthit-common.yaml");
        PropertySource<?> gateway = load("worthit-gateway.yaml");
        PropertySource<?> auth = load("worthit-auth.yaml");
        PropertySource<?> tracking = load("worthit-tracking.yaml");
        PropertySource<?> reminder = load("worthit-reminder.yaml");

        assertThat(common.getProperty("worthit.http.connect-timeout")).isNotNull();
        assertThat(common.getProperty("logging.level.root")).isNotNull();
        assertThat(common.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo(true);

        assertThat(gateway.getProperty("spring.cloud.gateway.routes[0].uri"))
                .isEqualTo("lb://worthit-auth");
        assertThat(gateway.getProperty("spring.cloud.gateway.routes[1].uri"))
                .isEqualTo("lb://worthit-tracking");
        assertThat(gateway.getProperty("spring.cloud.gateway.routes[2].uri"))
                .isEqualTo("lb://worthit-reminder");
        assertThat(read(templateDirectory.resolve("worthit-gateway.yaml")))
                .doesNotContain("localhost");

        String rotationEnabled = "worthit.security.same-token.rotation.enabled";
        assertThat(auth.getProperty(rotationEnabled)).isNotNull();
        assertThat(Set.of(common, gateway, tracking, reminder))
                .allSatisfy(source -> assertThat(source.getProperty(rotationEnabled)).isNull());

        assertThat(tracking.getProperty("worthit.clients.reminder.connect-timeout"))
                .isNotNull();
        assertThat(tracking.getProperty("worthit.clients.reminder.read-timeout"))
                .isNotNull();
        assertThat(read(templateDirectory.resolve("worthit-reminder.yaml")))
                .doesNotContainIgnoringCase("tracking", "datasource", "task", "job");
    }

    @Test
    void synchronizationScriptUsesOnlyNacosThreeAdminApis() throws IOException {
        Path script = repositoryRoot.resolve("scripts/local-infra/nacos-config.sh");
        assertThat(script).isRegularFile();

        assertThat(read(script)).contains(
                "/v3/admin/core/state/readiness",
                "/v3/console/health/readiness",
                "/v3/admin/core/namespace/check",
                "/v3/admin/core/namespace",
                "/v3/admin/cs/config",
                "/v3/admin/ns/instance/list",
                "check)",
                "sync)",
                "verify)",
                "services)")
                .doesNotContain("delete)");
    }

    private PropertySource<?> load(String dataId) throws IOException {
        Path template = templateDirectory.resolve(dataId);
        assertThat(template).as(dataId).isRegularFile();
        return loader.load(dataId, new FileSystemResource(template)).get(0);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
