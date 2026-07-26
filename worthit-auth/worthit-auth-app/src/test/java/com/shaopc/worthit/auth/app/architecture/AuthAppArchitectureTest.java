package com.shaopc.worthit.auth.app.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuthAppArchitectureTest {

    @Test
    void importsProductionAuthClasses() {
        assertThat(importProductionClasses()).isNotEmpty();
    }

    @Test
    void authAppDoesNotDependOnReactiveWebRuntime() {
        WorthItArchitectureRules
                .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                .check(importProductionClasses());
    }

    @Test
    void authAppReceivesWebMvcAutoconfigureThroughStarter() {
        assertThatCode(() -> Class.forName(
                "com.shaopc.worthit.common.webmvc.autoconfigure."
                        + "WorthItTraceAutoConfiguration"))
                .doesNotThrowAnyException();
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.shaopc.worthit.auth");
    }
}
