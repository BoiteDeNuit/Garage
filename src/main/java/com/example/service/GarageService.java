package com.example.service;

import com.example.client.CurrencyClient;
import com.example.config.KafkaTopicsConfig;
import com.example.dto.*;
import com.example.event.CarCreatedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.model.Car;
import com.example.model.Owner;
import com.example.repository.CarJpaRepository;
import com.example.repository.OwnerJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
@Service
public class GarageService{
    private final CarJpaRepository repository;
    private final OwnerJpaRepository ownerRepository;
    private final CurrencyClient currencyClient;
    private final KafkaTemplate<Long,CarCreatedEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter carsCreated;
    public GarageService (CarJpaRepository repository,
                          OwnerJpaRepository ownerRepository,
                          CurrencyClient currencyClient,
                          KafkaTemplate<Long, CarCreatedEvent> kafkaTemplate,
                          MeterRegistry meterRegistry
                          )
    {
        this.repository=repository;
        this.ownerRepository=ownerRepository;
        this.currencyClient=currencyClient;
        this.kafkaTemplate=kafkaTemplate;
        this.meterRegistry=meterRegistry;
        this.carsCreated=Counter.builder("garage.cars.added")
                .description("Машин добавлено в гараж")
                .register(meterRegistry);
    }
    @Transactional
    public void demoNPlusOne(){
        List<Owner> owners = ownerRepository.findAll();
        for(Owner owner : owners)
        {
            System.out.println(owner.getName() +" " + owner.getCars().size() + " Машин");
        }
    }
    @Transactional(readOnly = true)
    public OwnerDto getOwner(Long id)
    {
        Owner owner = ownerRepository.findById(id).orElseThrow();
        OwnerDto dto = new OwnerDto(id,owner.getName(), owner.getCity(), owner.getCars().size());
        return dto;
    }
    @Transactional
    @CacheEvict(value = "cars",key = "#carId")
    public void raisePower(Long carId, int delta)
    {
        Car car = repository.findById(carId).orElseThrow();
        car.setHorsePower(car.getHorsePower() + delta);
    }
    public List<Owner> findAllOwners()
    {
        return ownerRepository.findAll();
    }
    public CarDto addCar (CarDto dto)
    {
        Car saved = repository.save(CarMapper.toCar(dto));
        kafkaTemplate.send(KafkaTopicsConfig.CAR_CREATED,saved.getId(), new CarCreatedEvent(saved.getId(), saved.getBrand(), saved.getModel(), Instant.now()));
        carsCreated.increment();
        return CarMapper.toDto(saved);
    }
    @Cacheable(value = "cars",key = "#id")
    public CarDto getCar(Long id)
    {
        return repository.findById(id).
                map(CarMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Машина с id: " + id + " не найдена"));
    }

    public GarageStats stats()
    {
        List<Car> all = repository.findAll();
        int count = all.size();
        double averageHp = all.stream()
                .mapToInt(Car::getHorsePower)
                .average()
                .orElse(0);
        String strongestModel = all.stream()
                .max(Comparator.comparingInt(Car::getHorsePower))
                .map(Car::getModel).orElse("гараж пуст");
        return new GarageStats(count,averageHp,strongestModel);
    }
    private List<CarDto> findBy(Predicate<Car> condition)
    {
        return repository.findAll().stream().filter(condition).map(CarMapper::toDto).toList();
    }
    public List<CarDto> findByBrand(String brand)
    {
        return repository.findByBrandIgnoreCase(brand).stream().map(CarMapper::toDto).toList();
    }
    public List<CarDto> findAll()
    {
       return repository.findAll().stream().map(CarMapper::toDto).toList();
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
