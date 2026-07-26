package com.shaopc.worthit.tracking.app.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TrackingAppArchitectureTest {

    private static final ArchRule CATEGORY_DOMAIN_ISOLATION =
            noClasses()
                    .that()
                    .resideInAPackage("..tracking.category.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou.mybatisplus..",
                            "..tracking.category.interfaces..",
                            "..tracking.category.infrastructure..");

    private static final ArchRule CATEGORY_PERSISTENCE_DOES_NOT_LEAK =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "..tracking.category.domain..",
                            "..tracking.category.interfaces..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(
                            "..tracking.category.infrastructure.persistence..");

    @Test
    void importsProductionTrackingClassesIncludingInfrastructure() {
        JavaClasses classes = importProductionClasses();

        assertThat(classes).isNotEmpty();
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(ReminderClientConfiguration.class.getName());
    }

    @Test
    void trackingAppDoesNotDependOnReactiveWebRuntime() {
        WorthItArchitectureRules
                .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                .check(importProductionClasses());
    }

    @Test
    void trackingAppReceivesWebMvcAutoconfigureThroughStarter() {
        assertThatCode(() -> Class.forName(
                "com.shaopc.worthit.common.webmvc.autoconfigure."
                        + "WorthItTraceAutoConfiguration"))
                .doesNotThrowAnyException();
    }

    @Test
    void categoryKeepsDomainAndPersistenceBoundaries() {
        JavaClasses classes = importProductionClasses();

        CATEGORY_DOMAIN_ISOLATION.check(classes);
        CATEGORY_PERSISTENCE_DOES_NOT_LEAK.check(classes);
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.shaopc.worthit.tracking");
    }
}
