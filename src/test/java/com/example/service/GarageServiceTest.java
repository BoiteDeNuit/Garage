package com.example.service;

import com.example.client.CurrencyClient;
import com.example.dto.CarDto;
import com.example.dto.CarPriceDto;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarageServiceTest {
    @Mock
    private CarJpaRepository repository;
    @Mock
    private OwnerJpaRepository ownerJpaRepository;
    @Mock
    private CurrencyClient currencyClient;
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
        garage.addCar(new CarDto(null, "Toyota", "Chaser", "1JZ", 280, 1998, null));
        ArgumentCaptor<Car> captor = ArgumentCaptor.forClass(Car.class);
        verify(repository).save(captor.capture());
        Car saved = captor.getValue();
        assertThat(saved.getBrand()).isEqualTo("Toyota");
        assertThat(saved.getModel()).isEqualTo("Chaser");
        assertThat(saved.getId()).isNull();
    }

    @Test
    void priceInConvertsRublesByRate()
    {
        Car car = new Car("Porsche", "Taycan", "EV", 700, 2024);
        car.setId(1L);
        car.setPrice(new BigDecimal("8500000"));
        when(repository.findById(1L)).thenReturn(Optional.of(car));
        when(currencyClient.rateToRub("usd")).thenReturn(new BigDecimal("84.1975"));

        CarPriceDto result = garage.priceIn(1L, "usd");

        // курс — рубли за единицу валюты, поэтому цена делится, а не умножается
        assertThat(result.price()).isEqualByComparingTo("100953.12");
        assertThat(result.rate()).isEqualByComparingTo("84.1975");
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void priceInWithoutPriceGives404AndSkipsCbr()
    {
        Car car = new Car("Lada", "Niva", "21214", 83, 2020);
        car.setId(2L);
        when(repository.findById(2L)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> garage.priceIn(2L, "USD"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("не указана цена");
        // цена проверяется до запроса к ЦБ: незачем ждать ответа, который выбросим
        verifyNoInteractions(currencyClient);
    }

    @Test
    void priceInForMissingCarGives404AndSkipsCbr()
    {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> garage.priceIn(99L, "USD"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
        verifyNoInteractions(currencyClient);
    }
}