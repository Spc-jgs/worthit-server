package com.shaopc.worthit.tracking.app.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TrackingAppArchitectureTest {

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

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.shaopc.worthit.tracking");
    }
}
