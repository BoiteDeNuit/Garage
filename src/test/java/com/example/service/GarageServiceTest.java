package com.example.service;

import com.example.dto.CarDto;
import com.example.exception.EntityNotFoundException;
import com.example.model.Car;
import com.example.repository.CarJpaRepository;
import com.example.repository.OwnerJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarageServiceTest {
    @Mock
    private CarJpaRepository repository;
    @Mock
    private OwnerJpaRepository ownerJpaRepository;
    @InjectMocks
    private GarageService garage;

    @Test
    void getCarReturnsCarWhenExists()
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        car.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(car));
        CarDto result = garage.getCar(1L);
        assertThat(result.brand()).isEqualTo("Toyota");
        assertThat(result.id()).isEqualTo(1L);
        verify(repository).findById(1L);
    }

    @Test
    void getCarReturnsSmthWhenDontExists()
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        car.setId(1L);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(()-> garage.getCar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
    @Test
    void addCarSavesCorrectlyMappedEntity()
    {
        when(repository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));
        garage.addCar(new CarDto(null, "Toyota", "Chaser", "1JZ", 280, 1998));
        ArgumentCaptor<Car> captor = ArgumentCaptor.forClass(Car.class);
        verify(repository).save(captor.capture());
        Car saved = captor.getValue();
        assertThat(saved.getBrand()).isEqualTo("Toyota");
        assertThat(saved.getModel()).isEqualTo("Chaser");
        assertThat(saved.getId()).isNull();
    }
}