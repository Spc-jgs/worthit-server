package com.shaopc.worthit.common.test.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcModuleArchitectureTest {

    private static final String STARTER =
            "worthit-common-webmvc-starter";
    private static final String AUTOCONFIGURE =
            "worthit-common-webmvc-autoconfigure";
    private static final Path REPOSITORY_ROOT = repositoryRoot();

    @Test
    void autoconfigureOwnsOnlyWebMvcProductionPackages() throws IOException {
        Path sourceRoot = REPOSITORY_ROOT.resolve(
                "worthit-common/worthit-common-webmvc-autoconfigure/"
                        + "src/main/java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            assertThat(files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(sourceRoot::relativize)
                    .map(Path::toString))
                    .isNotEmpty()
                    .allMatch(path -> path.startsWith(
                            "com/shaopc/worthit/common/webmvc/"));
        }
    }

    @Test
    void starterContainsNoImplementationOrAutoConfigurationResources()
            throws IOException {
        Path mainSource = REPOSITORY_ROOT.resolve(
                "worthit-common/worthit-common-webmvc-starter/src/main");
        List<Path> files = new ArrayList<>();
        if (Files.exists(mainSource)) {
            try (Stream<Path> paths = Files.walk(mainSource)) {
                paths.filter(Files::isRegularFile).forEach(files::add);
            }
        }

        assertThat(files).isEmpty();
    }

    @Test
    void appAndRuntimeNeutralModulesDeclareExpectedDirectDependencies()
            throws Exception {
        assertThat(dependencyArtifacts(
                "worthit-auth/worthit-auth-app/pom.xml"))
                .contains(STARTER)
                .doesNotContain("worthit-common-security");
        assertThat(dependencyArtifacts(
                "worthit-tracking/worthit-tracking-app/pom.xml"))
                .contains(
                        STARTER,
                        "worthit-common-security",
                        "worthit-common-http");
        assertThat(dependencyArtifacts(
                "worthit-reminder/worthit-reminder-app/pom.xml"))
                .contains(STARTER)
                .doesNotContain(
                        "worthit-common-security",
                        "worthit-common-http");

        for (String pom : List.of(
                "worthit-gateway/pom.xml",
                "worthit-reminder/worthit-reminder-client/pom.xml",
                "worthit-common/worthit-common-web/pom.xml")) {
            assertThat(dependencyArtifacts(pom))
                    .doesNotContain(STARTER, AUTOCONFIGURE);
        }
        assertThat(dependencyArtifacts(
                "worthit-common/worthit-common-webmvc-starter/pom.xml"))
                .contains(AUTOCONFIGURE);
    }

    @Test
    void localDevAndTestProfilesExplicitlyEnableWorthItOpenApi()
            throws IOException {
        for (String application : List.of(
                "worthit-auth/worthit-auth-app/src/main/resources/"
                        + "application.yml",
                "worthit-tracking/worthit-tracking-app/src/main/resources/"
                        + "application.yml",
                "worthit-reminder/worthit-reminder-app/src/main/resources/"
                        + "application.yml")) {
            String yaml = Files.readString(REPOSITORY_ROOT.resolve(application));
            String[] documents = yaml.split("\\R---\\R", -1);

            assertThat(documents).hasSize(2);
            assertThat(documents[0])
                    .contains("api-docs:\n    enabled: false")
                    .doesNotContain("worthit:\n  web:\n    openapi:");
            assertThat(documents[1])
                    .contains("on-profile: \"local | dev | test\"")
                    .contains("""
                            worthit:
                              web:
                                openapi:
                                  enabled: true""");
        }
    }

    private static Set<String> dependencyArtifacts(String relativePom)
            throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        Document document = factory
                .newDocumentBuilder()
                .parse(REPOSITORY_ROOT.resolve(relativePom).toFile());
        Element dependencies =
                directChild(document.getDocumentElement(), "dependencies");
        List<String> artifacts = new ArrayList<>();
        if (dependencies != null) {
            NodeList children = dependencies.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child instanceof Element dependency
                        && "dependency".equals(dependency.getTagName())) {
                    Element artifactId =
                            directChild(dependency, "artifactId");
                    if (artifactId != null) {
                        artifacts.add(artifactId.getTextContent().trim());
                    }
                }
            }
        }
        return Set.copyOf(artifacts);
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element
                    && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve(
                            "worthit-common/worthit-common-webmvc-starter/"
                                    + "pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到worthit-server仓库根目录");
    }
}
