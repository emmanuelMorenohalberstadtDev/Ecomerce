package com.ecommerce.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable compliance for ADR-0001 and backend-architecture.md §ArchUnit.
 * These rules are the architecture — a red rule is an architecture breach, not a test failure.
 * allowEmptyShould: contexts start empty in the skeleton and fill in feature by feature.
 */
@AnalyzeClasses(packages = "com.ecommerce", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAnyPackage("..domain..", "com.ecommerce.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.transaction..",
                    "org.hibernate..", "com.fasterxml.jackson..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_depends_only_inward = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..domain..", "com.ecommerce.shared..", "com.ecommerce.common.exception..",
                    "java..", "javax..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule presentation_does_not_touch_repositories = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule contexts_are_cycle_free = slices()
            .matching("com.ecommerce.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule shared_kernel_depends_on_nothing_internal = classes()
            .that().resideInAPackage("com.ecommerce.shared..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.ecommerce.shared..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactions_only_in_application_layer = noClasses()
            .that().resideOutsideOfPackages("..application..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule jpa_entities_confined_to_infrastructure = noClasses()
            .that().resideOutsideOfPackages("..infrastructure..")
            .should().beAnnotatedWith("jakarta.persistence.Entity")
            .allowEmptyShould(true);
}
