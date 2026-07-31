package com.ecommerce.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit enforcement of the 11 architectural boundary rules documented in
 * {@code docs/architecture/backend-architecture.md §7} and {@code ADR-0001 Compliance}.
 *
 * <p>Rules are plain JUnit Jupiter {@code @Test} methods, each checking an {@link ArchRule}
 * against classes imported once in {@link #importClasses()}. This deliberately avoids
 * ArchUnit's separate {@code @AnalyzeClasses}/{@code @ArchTest} JUnit5 TestEngine: that
 * engine is never discovered by Maven Surefire's default class scan, which issues a
 * {@code ClasspathRootSelector} — a selector type the archunit-junit5-engine does not
 * support (confirmed empirically; it only responds to explicit Class/Package/UniqueId
 * selectors). Using plain {@code @Test} methods routes discovery through the Jupiter
 * engine instead, which Surefire already discovers reliably for every other test class.
 *
 * <p>Rules pass on an empty package skeleton because there are no classes to violate them
 * yet. As production code accumulates, any violation surfaces as a test failure in CI
 * (required gate).
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.ecommerce";

    /** The 9 bounded contexts (ADR-0001) — used to scope the cross-context rule (rule 6). */
    private static final Set<String> BOUNDED_CONTEXTS = Set.of(
            "auth", "catalog", "cart", "checkout", "inventory",
            "order", "payment", "pricing", "promotions");

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    // =========================================================================
    // Rule 1 — domain_is_framework_free
    //
    // No class in any ..domain.. sub-package may import Spring, JPA, or Jackson.
    // Domain objects must be testable without the container.
    // =========================================================================

    @Test
    void domain_is_framework_free() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.."
                )
                .because("domain layer must be framework-free (ADR-0001 compliance rule 1)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 2 — layers_point_inward (presentation does NOT touch infrastructure)
    // =========================================================================

    @Test
    void presentation_does_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("presentation layer must not depend on infrastructure (ADR-0001 compliance rule 2)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 3 — domain does NOT depend on application, infrastructure, or presentation
    // =========================================================================

    @Test
    void domain_does_not_depend_on_outer_layers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure..",
                        "..presentation.."
                )
                .because("domain must only point inward toward shared-kernel (ADR-0001 dependency rule)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 4 — application does NOT depend on infrastructure or presentation
    // =========================================================================

    @Test
    void application_does_not_depend_on_infrastructure_or_presentation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..presentation.."
                )
                .because("application layer must not depend on infrastructure or presentation (ADR-0001 dependency rule)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 5 — infrastructure does NOT depend on presentation
    // =========================================================================

    @Test
    void infrastructure_does_not_depend_on_presentation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..presentation..")
                .because("infrastructure must not depend on presentation (ADR-0001 dependency rule)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 6 — cross-context communication only through ports or domain events
    //
    // A class in bounded context A may depend on bounded context B ONLY through
    // B's application.port package or domain.event package. Direct references to
    // B's use cases, listeners, infrastructure, or presentation are forbidden.
    //
    // NOTE: this is a custom ArchCondition, not a plain resideInAPackage("(*)..")
    // rule. ArchUnit's fluent capture groups in "com.ecommerce.(*).." are NOT
    // correlated between the "that()" and "should()" clauses — a naive rule using
    // the same wildcard on both sides also matches same-context dependencies
    // (e.g. auth's own AuthConfiguration depending on auth's own JwtProperties),
    // producing false positives. This condition explicitly extracts and compares
    // origin/target bounded-context names.
    // =========================================================================

    @Test
    void contexts_communicate_only_through_ports_or_events() {
        ArchRule rule = classes()
                .should(onlyReachOtherContextsThroughPortsOrEvents())
                .because("contexts must communicate only via application.port or domain.event packages (ADR-0001 compliance rule 3)");
        rule.check(importedClasses);
    }

    private static ArchCondition<JavaClass> onlyReachOtherContextsThroughPortsOrEvents() {
        return new ArchCondition<JavaClass>(
                "only reach other bounded contexts through application.port or domain.event packages") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String originContext = boundedContextOf(javaClass.getPackageName());
                if (originContext == null) {
                    return;
                }
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetContext = boundedContextOf(target.getPackageName());
                    if (targetContext == null || targetContext.equals(originContext)) {
                        continue;
                    }
                    String targetPackage = target.getPackageName();
                    boolean forbidden = targetPackage.contains(".application.usecase")
                            || targetPackage.contains(".application.listener")
                            || targetPackage.contains(".infrastructure")
                            || targetPackage.contains(".presentation");
                    if (forbidden) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                                dependency.getDescription() + " — bounded contexts must communicate only "
                                        + "through application.port or domain.event packages"));
                    }
                }
            }
        };
    }

    private static String boundedContextOf(String packageName) {
        String prefix = BASE_PACKAGE + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        String first = dot == -1 ? rest : rest.substring(0, dot);
        return BOUNDED_CONTEXTS.contains(first) ? first : null;
    }

    // =========================================================================
    // Rule 7 — no cycles between bounded-context slices
    // =========================================================================

    @Test
    void no_cycles_between_bounded_contexts() {
        ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .should().beFreeOfCycles()
                .because("bounded context slices must be acyclic (ADR-0001 compliance rule 4)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 8 — repository interfaces live only in the domain layer
    // =========================================================================

    @Test
    void repository_interfaces_live_in_domain() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .and().resideOutsideOfPackage("org.springframework..")
                .should().resideInAPackage("..domain..")
                .because("repository interfaces are domain concerns; JPA adapters implement them in infrastructure (ADR-0001 compliance rule 5)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 9 — JPA @Entity classes live only in infrastructure.persistence
    // =========================================================================

    @Test
    void jpa_entities_only_in_infrastructure() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .should().resideInAPackage("..infrastructure.persistence..")
                .because("JPA entities are an infrastructure concern; domain aggregates are framework-free (backend-architecture §7 rule 8)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 10 — @RestController only in presentation layer
    // =========================================================================

    @Test
    void controllers_only_in_presentation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage("..presentation..")
                .because("REST controllers must reside in the presentation layer (backend-architecture §7 rule 10)");
        rule.check(importedClasses);
    }

    // =========================================================================
    // Rule 11 — shared-kernel depends on nothing inside com.ecommerce (contexts)
    //           and on nothing in Spring/JPA/Jackson
    // =========================================================================

    @Test
    void shared_kernel_depends_on_nothing_in_ecommerce() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ecommerce.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ecommerce.auth..",
                        "com.ecommerce.catalog..",
                        "com.ecommerce.cart..",
                        "com.ecommerce.checkout..",
                        "com.ecommerce.inventory..",
                        "com.ecommerce.order..",
                        "com.ecommerce.payment..",
                        "com.ecommerce.pricing..",
                        "com.ecommerce.promotions..",
                        "com.ecommerce.common..",
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson.."
                )
                .because("shared-kernel is pure value objects; no framework or context dependency allowed (backend-architecture §7 rule 11)");
        rule.check(importedClasses);
    }
}
