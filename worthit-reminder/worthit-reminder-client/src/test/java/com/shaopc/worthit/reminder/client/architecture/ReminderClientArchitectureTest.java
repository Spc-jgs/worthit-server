package com.shaopc.worthit.reminder.client.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.shaopc.worthit.reminder.client",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ReminderClientArchitectureTest {

    @ArchTest
    static final ArchRule clientMustNotDependOnImplementation =
            WorthItArchitectureRules.CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION;

    @ArchTest
    static void importsProductionClientClasses(JavaClasses classes) {
        assertThat(classes).isNotEmpty();
    }
}
