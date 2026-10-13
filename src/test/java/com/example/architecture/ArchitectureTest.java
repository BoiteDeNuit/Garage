package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// Правила, которые README обещает словами, здесь проверяются по байткоду. Нарушение — упавший тест
// с перечнем классов, а не замечание на ревью
class ArchitectureTest {
    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example");

    // Контроллер разбирает HTTP и зовёт сервис. Репозиторий напрямую — мимо проверок доступа и транзакций сервиса
    @Test
    void controllersDoNotUseRepositories()
    {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .check(MAIN);
    }

    // Сущности не выходят из сервиса: наружу только DTO. Сущность в контроллере — ленивая связь,
    // которая загрузится при сериализации, и поля, которые клиенту видеть не надо
    @Test
    void controllersDoNotSeeEntities()
    {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(MAIN);
    }

    // Транзакция открывается в сервисе. На контроллере она держала бы соединение с базой, пока пишется ответ
    @Test
    void controllersAreNotTransactional()
    {
        noClasses().that().resideInAPackage("..controller..")
                .should().beAnnotatedWith(Transactional.class)
                .check(MAIN);
        methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .should().notBeAnnotatedWith(Transactional.class)
                .check(MAIN);
    }

    @Test
    void restControllersLiveInControllerPackage()
    {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..controller..")
                .andShould().haveSimpleNameEndingWith("Controller")
                .check(MAIN);
    }

    // Зависимости через конструктор: поле с @Autowired не final, и класс не создать в тесте без Spring
    @Test
    void noFieldInjection()
    {
        noFields().should().beAnnotatedWith(Autowired.class).check(MAIN);
    }

    // Сервис про Kafka не знает: он публикует событие Spring, в Kafka его отправляет ListingEventsRelay после коммита.
    // Отправка из сервиса ушла бы и при откате транзакции
    @Test
    void servicesDoNotTalkToKafka()
    {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.kafka..")
                .check(MAIN);
    }

    // SDK хранилища — только в storage. Остальной код знает PhotoStorage, а не S3
    @Test
    void awsSdkOnlyInStorage()
    {
        noClasses().that().resideOutsideOfPackage("..storage..")
                .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")
                .check(MAIN);
    }

    // Пакеты без циклов. Этот тест нашёл два: GlobalExceptionHandler лежал в exception и зависел от dto, а dto
    // бросает исключения из exception; ListingStateException лежал в exception и зависел от ListingStatus,
    // а Listing бросает его. Обработчик ушёл в controller (это веб-слой), исключение — в model (его бросает сущность)
    @Test
    void packagesAreFreeOfCycles()
    {
        slices().matching("com.example.(*)..").should().beFreeOfCycles().check(MAIN);
    }

    // Запросы и ответы API — record: неизменяемые, equals и toString без ручного кода
    @Test
    void requestsAndResponsesAreRecords()
    {
        classes().that().resideInAPackage("..dto..")
                .and().haveSimpleNameEndingWith("Dto")
                .or().resideInAPackage("..dto..").and().haveSimpleNameEndingWith("Request")
                .should(beRecords())
                .check(MAIN);
    }

    private static ArchCondition<JavaClass> beRecords()
    {
        return new ArchCondition<>("be records")
        {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events)
            {
                boolean record = javaClass.isRecord();
                events.add(new SimpleConditionEvent(javaClass, record, javaClass.getName() + (record ? " — record" : " — не record")));
            }
        };
    }
}
