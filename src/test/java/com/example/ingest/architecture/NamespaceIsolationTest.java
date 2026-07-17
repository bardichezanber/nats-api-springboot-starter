package com.example.ingest.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Static guarantees behind "changing namespace A cannot affect B":
 * policies cannot see each other, shared code cannot see the roles, and
 * policies cannot smuggle coupling through mutable static state.
 */
@AnalyzeClasses(packages = "com.example.ingest", importOptions = ImportOption.DoNotIncludeTests.class)
class NamespaceIsolationTest {

    @ArchTest
    static final ArchRule policies_do_not_depend_on_sibling_policies =
            classes().that().resideInAPackage("..namespace.policies")
                    .should(notDependOnSiblingsInTheSamePackage());

    @ArchTest
    static final ArchRule shared_code_does_not_depend_on_roles =
            noClasses().that().resideInAnyPackage("..ingest.namespace..", "..ingest.record..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ingest.worker..", "..ingest.api..");

    @ArchTest
    static final ArchRule policies_have_no_mutable_static_state =
            fields().that().areDeclaredInClassesThat().resideInAPackage("..namespace.policies")
                    .and().areStatic()
                    .should().beFinal();

    private static ArchCondition<JavaClass> notDependOnSiblingsInTheSamePackage() {
        return new ArchCondition<>("not depend on sibling classes in the same package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    boolean sibling = target.getPackageName().equals(javaClass.getPackageName())
                            && !target.getFullName().equals(javaClass.getFullName());
                    if (sibling) {
                        events.add(SimpleConditionEvent.violated(javaClass, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
