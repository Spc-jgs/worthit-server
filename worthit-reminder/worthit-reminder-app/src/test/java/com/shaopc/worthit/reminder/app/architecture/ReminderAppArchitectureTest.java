package com.shaopc.worthit.reminder.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ReminderAppArchitectureTest {

    @Test
    void reminderAppDoesNotDependOnReactiveWebRuntime() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.shaopc.worthit.reminder.app");

        noClasses()
                .that().resideInAPackage("com.shaopc.worthit.reminder.app..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.reactive..",
                        "org.springframework.web.server..")
                .allowEmptyShould(false)
                .check(classes);
    }
}
