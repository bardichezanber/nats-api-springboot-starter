package com.example.ingest.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Static guarantees behind hard rule 6: the three roles (worker, api,
 * gateway) never depend on each other's code, and the packages whose beans
 * are role-exclusive carry the matching {@code @Profile} annotation.
 *
 * <p>The profile rules cover only the packages where the invariant holds
 * for every stereotyped class today. Deliberately profile-less beans —
 * the resolvers in {@code worker/source}, {@code IngestMetrics}, and
 * composition plan policies — load wherever their callers load and are
 * left out on purpose.
 */
@AnalyzeClasses(packages = "com.example.ingest", importOptions = ImportOption.DoNotIncludeTests.class)
class RoleBoundariesTest {

    @ArchTest
    static final ArchRule api_does_not_depend_on_worker_or_gateway =
            noClasses().that().resideInAPackage("..ingest.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ingest.worker..", "..ingest.gateway..");

    @ArchTest
    static final ArchRule gateway_does_not_depend_on_worker =
            noClasses().that().resideInAPackage("..ingest.gateway..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..ingest.worker..");

    @ArchTest
    static final ArchRule worker_does_not_depend_on_api_or_gateway =
            noClasses().that().resideInAPackage("..ingest.worker..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ingest.api..", "..ingest.gateway..");

    @ArchTest
    static final ArchRule api_beans_are_profile_api =
            classes().that().resideInAPackage("..ingest.api..")
                    .and().areMetaAnnotatedWith(Component.class)
                    .should(carryProfile("api"));

    @ArchTest
    static final ArchRule gateway_beans_are_profile_gateway =
            classes().that().resideInAPackage("..ingest.gateway..")
                    .and().areMetaAnnotatedWith(Component.class)
                    .should(carryProfile("gateway"));

    @ArchTest
    static final ArchRule nats_beans_are_profile_worker =
            classes().that().resideInAPackage("..ingest.worker.nats..")
                    .and().areMetaAnnotatedWith(Component.class)
                    .should(carryProfile("worker"));

    private static ArchCondition<JavaClass> carryProfile(String expected) {
        return new ArchCondition<>("be annotated with @Profile(\"" + expected + "\")") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean satisfied = javaClass.isAnnotatedWith(Profile.class)
                        && Arrays.asList(javaClass.getAnnotationOfType(Profile.class).value())
                                .contains(expected);
                if (!satisfied) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " is a bean in a " + expected
                                    + "-only package but is not @Profile(\"" + expected + "\")"));
                }
            }
        };
    }
}
