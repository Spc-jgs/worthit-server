package com.shaopc.worthit.tracking.client;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TrackingClientArchitectureTest {

    @Test
    void remainsRuntimeNeutral() {
        var classes = new ClassFileImporter()
                .importPackages("com.shaopc.worthit.tracking.client");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.boot..",
                        "org.springframework.context..",
                        "org.springframework.stereotype..",
                        "org.springframework.web.bind.annotation.RestController")
                .check(classes);
    }
}
