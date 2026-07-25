package com.shaopc.worthit.gateway.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class GatewayArchitectureTest {

    @Test
    void gatewayStaysReactive() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.shaopc.worthit.gateway");

        WorthItArchitectureRules.GATEWAY_MUST_STAY_REACTIVE.check(classes);
    }
}
