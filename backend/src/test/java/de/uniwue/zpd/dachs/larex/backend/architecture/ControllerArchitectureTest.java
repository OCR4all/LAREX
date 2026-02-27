package de.uniwue.zpd.dachs.larex.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "de.uniwue.zpd.dachs.larex.backend")
class ControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllersShouldNotDependOnRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("de.uniwue.zpd.dachs.larex.backend.repository..");
}
