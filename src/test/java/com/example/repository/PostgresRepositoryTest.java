package com.example.repository;

import com.example.model.AppUser;
import com.example.model.Car;
import com.example.model.Owner;
import com.example.model.Role;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @Autowired
    private CarJpaRepository carRepository;
    @Autowired
    private OwnerJpaRepository ownerRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void migrationsCreateAdmin()
    {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    void userWithoutRoleIsRejected()
    {
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into users (username, password_hash) values ('ghost', 'x')")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("role");
    }

    @Test
    void userWithUnknownRoleIsRejected()
    {
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into users (username, password_hash, role) values ('ghost', 'x', 'SUPERUSER')")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_users_role");
    }

    @Test
    void brandIndexUsesUpperLikeHibernate()
    {
        List<String> indexes = entityManager.getEntityManager()
                .createNativeQuery("select indexname from pg_indexes where tablename = 'cars'")
                .getResultList().stream().map(Object::toString).toList();

        assertThat(indexes).contains("idx_cars_brand_upper").doesNotContain("idx_cars_brand");
    }

    @Test
    void savesAndReadsCarWithPrice()
    {
        Car car = new Car("Porsche", "Taycan", "EV", 700, 2024);
        car.setPrice(new BigDecimal("8500000"));
        Long id = carRepository.save(car).getId();
        entityManager.flush();
        entityManager.clear();

        Car found = carRepository.findById(id).orElseThrow();
        assertThat(found.getBrand()).isEqualTo("Porsche");
        assertThat(found.getPrice()).isEqualByComparingTo("8500000.00");
    }

    @Test
    void findsBrandIgnoringCase()
    {
        carRepository.save(new Car("Toyota", "Supra", "2JZ", 320, 1998));
        carRepository.save(new Car("BMW", "M4", "S58", 510, 2024));
        entityManager.flush();

        assertThat(carRepository.findByBrandIgnoreCase("toyota", PageRequest.of(0, 10)).getContent())
                .extracting(Car::getModel)
                .containsExactly("Supra");
    }

    @Test
    void pagesAreSortedAndCounted()
    {
        carRepository.save(new Car("Toyota", "Supra", "2JZ", 320, 1998));
        carRepository.save(new Car("BMW", "M4", "S58", 510, 2024));
        carRepository.save(new Car("Lada", "Niva", "21214", 83, 2020));
        entityManager.flush();

        Page<Car> page = carRepository.findAll(PageRequest.of(0, 2, Sort.by("year")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Car::getYear)
                .containsExactly(1998, 2020);
    }

    @Test
    void aggregatesAreCalculatedByDatabase()
    {
        carRepository.save(new Car("Toyota", "Supra", "2JZ", 320, 1998));
        carRepository.save(new Car("Porsche", "Taycan", "EV", 700, 2024));
        carRepository.save(new Car("Lada", "Niva", "21214", 83, 2020));
        entityManager.flush();

        assertThat(carRepository.count()).isEqualTo(3);
        assertThat(carRepository.averageHorsePower()).isCloseTo(367.67, within(0.01));
        assertThat(carRepository.findFirstByOrderByHorsePowerDesc()).map(Car::getModel).contains("Taycan");
    }

    @Test
    void averageOfEmptyGarageIsZero()
    {
        assertThat(carRepository.averageHorsePower()).isZero();
    }

    @Test
    void existsByEngineCodeChecksDatabase()
    {
        carRepository.save(new Car("Toyota", "Supra", "2JZ", 320, 1998));
        entityManager.flush();

        assertThat(carRepository.existsByEngineCode("2JZ")).isTrue();
        assertThat(carRepository.existsByEngineCode("1JZ")).isFalse();
    }

    @Test
    void ownerLoadsCarsWithJoinFetch()
    {
        Owner owner = new Owner("Иван", "Москва");
        Car car = new Car("Lada", "Niva", "21214", 83, 2020);
        owner.addCar(car);
        ownerRepository.save(owner);
        carRepository.save(car);
        entityManager.flush();
        entityManager.clear();

        List<Owner> owners = ownerRepository.findAllWithCars();
        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).getCars())
                .extracting(Car::getModel)
                .containsExactly("Niva");
    }
}
