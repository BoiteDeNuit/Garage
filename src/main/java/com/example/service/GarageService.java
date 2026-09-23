package com.example.service;

import com.example.client.CurrencyClient;
import com.example.config.KafkaTopicsConfig;
import com.example.dto.CarDto;
import com.example.dto.CarMapper;
import com.example.dto.CarPriceDto;
import com.example.dto.GarageStats;
import com.example.event.CarCreatedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.model.Car;
import com.example.repository.CarJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class GarageService{
    private final CarJpaRepository repository;
    private final CurrencyClient currencyClient;
    private final KafkaTemplate<Long,CarCreatedEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter carsCreated;
    public GarageService (CarJpaRepository repository,
                          CurrencyClient currencyClient,
                          KafkaTemplate<Long, CarCreatedEvent> kafkaTemplate,
                          MeterRegistry meterRegistry
                          )
    {
        this.repository=repository;
        this.currencyClient=currencyClient;
        this.kafkaTemplate=kafkaTemplate;
        this.meterRegistry=meterRegistry;
        this.carsCreated=Counter.builder("garage.cars.added")
                .description("Машин добавлено в гараж")
                .register(meterRegistry);
    }
    @Transactional
    public CarDto addCar (CarDto dto)
    {
        Car saved = repository.save(CarMapper.toCar(dto));
        kafkaTemplate.send(KafkaTopicsConfig.CAR_CREATED,saved.getId(), new CarCreatedEvent(saved.getId(), saved.getBrand(), saved.getModel(), Instant.now()));
        carsCreated.increment();
        return CarMapper.toDto(saved);
    }
    @Cacheable(value = "cars",key = "#id")
    @Transactional(readOnly = true)
    public CarDto getCar(Long id)
    {
        return repository.findById(id).
                map(CarMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Машина с id: " + id + " не найдена"));
    }
    @Transactional(readOnly = true)
    public GarageStats stats()
    {
        long count = repository.count();
        double averageHp = repository.averageHorsePower();
        String strongestModel = repository.findFirstByOrderByHorsePowerDesc().map(Car::getModel).orElse("Гараж пуст");
        return new GarageStats(count,averageHp,strongestModel);
    }
    @Transactional(readOnly = true)
    public Page<CarDto> findByBrand(String brand, Pageable pageable)
    {
        return repository.findByBrandIgnoreCase(brand,pageable).map(CarMapper::toDto);
    }
    @Transactional(readOnly = true)
    public Page<CarDto> findAll(Pageable pageable)
    {
       return repository.findAll(pageable).map(CarMapper::toDto);
    }
    @CacheEvict(value = "cars",key = "#id")
    @Transactional
    public void deleteCar(Long id)
    {
        Car car = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Машина с id: " + id + " не найдена"));
        repository.delete(car);
    }
    
    public CarPriceDto priceIn(Long id,String currency)
    {
        Car car = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Машина с id: " + id + " не найдена" ));
        if(car.getPrice() == null)
        {
            throw new EntityNotFoundException("У машины с id: " + id + " не указана цена");
        }
        BigDecimal rate = currencyClient.rateToRub(currency);
        BigDecimal convertedPrice = car.getPrice().divide(rate,2, RoundingMode.HALF_UP);
        return new CarPriceDto(car.getId(),currency.toUpperCase(),rate,convertedPrice);
    }


}
