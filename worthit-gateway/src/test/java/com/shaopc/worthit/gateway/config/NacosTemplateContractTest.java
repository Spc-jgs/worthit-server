package com.shaopc.worthit.gateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
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
        assertThat(common.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics");

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
    void gatewayRoutesFrozenPublicApisToTheirOwningServices()
            throws IOException {
        String gateway = read(
                templateDirectory.resolve("worthit-gateway.yaml"));

        assertThat(gateway).contains(
                        "- Path=/api/v1/auth/**",
                        "- Path=/api/v1/categories/**,"
                                + "/api/v1/items/**,"
                                + "/api/v1/wishes/**,"
                                + "/api/v1/subscriptions/**,"
                                + "/api/v1/dashboard,"
                                + "/api/v1/lifecycle/**,"
                                + "/api/v1/recovery/**",
                        "- Path=/api/v1/reminders/**")
                .doesNotContain(
                        "- Path=/api/auth/**",
                        "- Path=/api/tracking/**",
                        "- Path=/api/reminders/**");
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

    @Test
    void synchronizationScriptSupportsDisabledClientAuthentication()
            throws IOException, InterruptedException {
        Path fakeBin = Files.createTempDirectory("nacos-script-test");
        Path fakeCurl = fakeBin.resolve("curl");
        Files.writeString(
                fakeCurl,
                "#!/usr/bin/env bash\nprintf '{\"code\":0}'\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeCurl, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));

        ProcessBuilder processBuilder = new ProcessBuilder(
                "/bin/bash",
                repositoryRoot.resolve("scripts/local-infra/nacos-config.sh").toString(),
                "check");
        processBuilder.environment().remove("NACOS_USERNAME");
        processBuilder.environment().remove("NACOS_PASSWORD");
        processBuilder.environment().put(
                "PATH",
                fakeBin + ":" + processBuilder.environment().get("PATH"));
        Process process = processBuilder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).contains("Nacos server and console: READY");
    }

    @Test
    void fullRecoveryScriptUsesPublicApisAndWaitsForShortWindow()
            throws IOException, InterruptedException {
        Path script = repositoryRoot.resolve(
                "scripts/local-infra/verify-m3-full-recovery.sh");
        assertThat(script).isRegularFile();
        String content = read(script);
        assertThat(content)
                .contains(
                        "/api/v1/recovery/resources",
                        "/api/v1/categories/",
                        "WORTHIT_RECOVERY_WAIT_SECONDS:-61",
                        "WORTHIT_AUTH_SECONDARY_USERNAME",
                        "categoryFallbackApplied")
                .doesNotContain(
                        "docker compose",
                        "MYSQL_PWD",
                        "dev-stack/.env");

        Process process = new ProcessBuilder(
                "/bin/bash", "-n", script.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
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
