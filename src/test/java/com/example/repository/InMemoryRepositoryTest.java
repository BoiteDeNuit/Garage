package com.example.repository;

import com.example.exception.StorageFullException;
import com.example.model.Car;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoryTest {
    private InMemoryRepository<Car> repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository<>(Car.class,100);
    }

    @Test
    void saveAssignsId() throws StorageFullException {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car saved = repository.save(car);
        assertNotNull(saved.getId());
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findAll() throws StorageFullException {
        List<Car> all = new ArrayList<>();
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car1 = new Car("Toyot2a", "Supra", "2JZ", 320, 1998);
        repository.save(car);
        repository.save(car1);
        assertEquals(2,repository.findAll().size());
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void deleteByIdReturnsEmptyByMissing() {
        assertFalse(repository.deleteById(999L));
        assertThat(repository.deleteById(999L)).isFalse();
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        assertTrue(repository.findById(999L).isEmpty());
        assertThat(repository.findById(999L)).isEmpty();
    }
    @Test
    void saveAssignsGrowingIds() throws StorageFullException
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car1 = new Car("Toyot2", "Supra", "2JZ", 320, 1998);

        Car saved = repository.save(car);
        Car saved1 = repository.save(car1);
        assertTrue(saved1.getId() > saved.getId());
        assertThat(saved1.getId()).isGreaterThan(saved.getId());
    }
    @Test
    void saveThrowsFull() throws StorageFullException
    {
        InMemoryRepository<Car> tiny = new InMemoryRepository<>(Car.class,1);
        tiny.save(new Car("Toyota","BW","2JZ-RTE",200,2000));
        StorageFullException e = assertThrows(StorageFullException.class,
                () -> tiny.save(new Car("BMW","M4","S58",510,2024)));
        assertTrue(e.getMessage().contains("переполнен"));
        assertThat(e.getMessage()).contains("переполнен");
        assertThatThrownBy(() -> tiny.save(new Car("BMW","M4","S58",510,2024)))
                .isInstanceOf(StorageFullException.class)
                .hasMessageContaining("переполнен");
    }
    @Test
    void savePreservesExistingId() throws StorageFullException
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        car.setId(42L);
        Car saved = repository.save(car);
        assertEquals(42L,saved.getId());
        assertThat(saved.getId()).isEqualTo(42L);
    }
}