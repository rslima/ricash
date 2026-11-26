package com.rslima.ricash;

import com.rslima.ricash.exceptions.ConflictException;
import com.rslima.ricash.exceptions.EntityNotFoundException;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * The CLAUDE.md conventions as executable rules, so they hold without anyone
 * having to remember them.
 */
@AnalyzeClasses(packages = "com.rslima.ricash", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // MapStruct generates the *MapperImpl classes with componentModel = "spring";
    // those are the one intended exception.
    @ArchTest
    static final ArchRule domainClassesAreWiredManually = noClasses()
            .that().resideInAnyPackage("com.rslima.ricash.ledgers..", "com.rslima.ricash.users..")
            .and().haveSimpleNameNotEndingWith("MapperImpl")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Repository.class)
            .because("services and repositories are registered via @Bean factories in "
                    + "LedgerConfiguration/UserConfiguration (manual bean wiring)");

    @ArchTest
    static final ArchRule controllersNeverTouchRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .orShould().dependOnClassesThat().areAssignableTo(JdbcClient.class)
            .because("layering is Controller -> Service -> Repository");

    // The refresh/fetch sweeps are deliberately left out: each write there is
    // one idempotent upsert statement, and they must not hold a database
    // transaction open around external HTTP calls.
    @ArchTest
    static final ArchRule mutatingServiceMethodsAreTransactional = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("ServiceBean")
            .and().haveNameMatching("(create|update|delete|allocate|set|save|upsert).*")
            .should().beAnnotatedWith(Transactional.class)
            .because("multi-statement writes plus the V12/V13 triggers make partial writes corrupting");

    @ArchTest
    static final ArchRule exceptionHandlingIsCentralized = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("com.rslima.ricash.web..")
            .should().beAnnotatedWith(ExceptionHandler.class)
            .because("web/GlobalExceptionHandler is the single error contract");

    @ArchTest
    static final ArchRule notFoundExceptionsExtendTheBase = classes()
            .that().haveSimpleNameEndingWith("NotFoundException")
            .and().doNotBelongToAnyOf(EntityNotFoundException.class)
            .should().beAssignableTo(EntityNotFoundException.class)
            .because("GlobalExceptionHandler maps EntityNotFoundException to 404");

    @ArchTest
    static final ArchRule conflictExceptionsExtendTheBase = classes()
            .that().haveSimpleNameEndingWith("HasTransactionsException")
            .should().beAssignableTo(ConflictException.class)
            .because("GlobalExceptionHandler maps ConflictException to 409");
}
