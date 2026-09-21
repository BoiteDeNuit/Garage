package com.example.repository;

import com.example.model.AppUser;
import com.example.model.Car;
import com.example.model.Owner;
import com.example.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

        assertThat(carRepository.findByBrandIgnoreCase("toyota"))
                .extracting(Car::getModel)
                .containsExactly("Supra");
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
