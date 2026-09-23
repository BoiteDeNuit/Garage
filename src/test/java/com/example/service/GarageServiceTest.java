package com.example.service;

import com.example.client.CurrencyClient;
import com.example.config.KafkaTopicsConfig;
import com.example.dto.CarDto;
import com.example.dto.CarPriceDto;
import com.example.dto.GarageStats;
import com.example.event.CarCreatedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.model.Car;
import com.example.repository.CarJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarageServiceTest {
    @Mock
    private CarJpaRepository repository;
    @Mock
    private CurrencyClient currencyClient;
    @Mock
    private KafkaTemplate<Long, CarCreatedEvent> kafkaTemplate;
    @Spy
    private MeterRegistry registry = new SimpleMeterRegistry();
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
    void addCarPublishesEventWithIdAsKey()
    {
        when(repository.save(any(Car.class))).thenAnswer(inv -> {
            Car car = inv.getArgument(0);
            car.setId(42L);
            return car;
        });

        garage.addCar(new CarDto(null, "Toyota", "Chaser", "1JZ", 280, 1998, null));

        ArgumentCaptor<CarCreatedEvent> captor = ArgumentCaptor.forClass(CarCreatedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopicsConfig.CAR_CREATED), eq(42L), captor.capture());
        CarCreatedEvent event = captor.getValue();
        assertThat(event.id()).isEqualTo(42L);
        assertThat(event.brand()).isEqualTo("Toyota");
        assertThat(event.createdAt()).isNotNull();
    }

    @Test
    void addCarIncrementsCreatedCounter()
    {
        when(repository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

        garage.addCar(new CarDto(null, "Toyota", "Chaser", "1JZ", 280, 1998, null));

        assertThat(registry.get("garage.cars.added").counter().count()).isEqualTo(1.0);
    }

    @Test
    void findAllMapsPageToDto()
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        car.setId(1L);
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(car), pageable, 1));

        Page<CarDto> result = garage.findAll(pageable);

        assertThat(result.getContent()).extracting(CarDto::brand).containsExactly("Toyota");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void statsOfEmptyGarage()
    {
        when(repository.count()).thenReturn(0L);
        when(repository.averageHorsePower()).thenReturn(0.0);
        when(repository.findFirstByOrderByHorsePowerDesc()).thenReturn(Optional.empty());

        GarageStats stats = garage.stats();

        assertThat(stats.count()).isZero();
        assertThat(stats.averageHp()).isZero();
        assertThat(stats.strongestModel()).isEqualTo("Гараж пуст");
    }

    @Test
    void statsTakesAggregatesFromDatabase()
    {
        Car strongest = new Car("Porsche", "Taycan", "EV", 700, 2024);
        when(repository.count()).thenReturn(3L);
        when(repository.averageHorsePower()).thenReturn(400.0);
        when(repository.findFirstByOrderByHorsePowerDesc()).thenReturn(Optional.of(strongest));

        GarageStats stats = garage.stats();

        assertThat(stats).isEqualTo(new GarageStats(3, 400.0, "Taycan"));
        verify(repository, never()).findAll();
    }

    @Test
    void deleteCarRemovesExistingCar()
    {
        Car car = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        car.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(car));

        garage.deleteCar(1L);

        verify(repository).delete(car);
    }

    @Test
    void deleteCarThrowsWhenCarMissing()
    {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> garage.deleteCar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
        verify(repository, never()).delete(any(Car.class));
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