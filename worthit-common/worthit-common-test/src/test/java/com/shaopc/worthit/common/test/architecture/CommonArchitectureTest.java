package com.shaopc.worthit.common.test.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.shaopc.worthit.common",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CommonArchitectureTest {

    @ArchTest
    static final ArchRule commonMustNotDependOnBusiness =
            WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS;

    @ArchTest
    static final ArchRule commonWebMustStayRuntimeNeutral =
            WorthItArchitectureRules.COMMON_WEB_MUST_STAY_RUNTIME_NEUTRAL;

    @ArchTest
    static void importsProductionCommonClasses(JavaClasses classes) {
        assertThat(classes).isNotEmpty();
    }
}
