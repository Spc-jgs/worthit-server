package com.shaopc.worthit.gateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutableApplicationPackagingContractTest {

    private static final List<String> APPLICATION_POMS = List.of(
            "worthit-gateway/pom.xml",
            "worthit-auth/worthit-auth-app/pom.xml",
            "worthit-tracking/worthit-tracking-app/pom.xml",
            "worthit-reminder/worthit-reminder-app/pom.xml");

    private final Path repositoryRoot = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void runtimeApplicationsApplySpringBootRepackage() throws IOException {
        String parentPom = read(repositoryRoot.resolve("pom.xml"));
        assertThat(parentPom)
                .contains("<artifactId>spring-boot-maven-plugin</artifactId>")
                .contains("<goal>repackage</goal>");

        for (String applicationPom : APPLICATION_POMS) {
            assertThat(read(repositoryRoot.resolve(applicationPom)))
                    .as(applicationPom)
                    .contains("<artifactId>spring-boot-maven-plugin</artifactId>");
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
