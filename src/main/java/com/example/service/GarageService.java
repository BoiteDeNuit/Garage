package com.example.service;

import com.example.dto.CarDto;
import com.example.dto.CarMapper;
import com.example.exception.EntityNotFoundException;
import com.example.exception.StorageFullException;
import com.example.model.Car;
import com.example.dto.GarageStats;
import com.example.repository.CrudRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
@Service
public class GarageService{
    private final CrudRepository<Car> repository;
    public GarageService (CrudRepository<Car> repository)
    {
        this.repository=repository;
    }
    public CarDto addCar (CarDto dto) throws StorageFullException
    {
        Car car = CarMapper.toCar(dto);
        return CarMapper.toDto(repository.save(car));
    }
    @PostConstruct
    void seedGarage()
    {
        try
        {
            repository.save(new Car("Toyota", "Supra", "2JZ-GTE", 320, 1998));
            repository.save(new Car("Subaru", "Impreza", "EJ20", 280, 1999));
        }
        catch (StorageFullException e)
        {
            System.out.println("Гараж полон");
        }
    }
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
    public List<CarDto> findBy(Predicate<Car> condition)
    {
        return repository.findAll().stream().filter(condition).map(CarMapper::toDto).toList();
    }
    public List<CarDto> findByBrand(String brand)
    {
        return repository.findAll().stream().filter(c -> c.getBrand().equalsIgnoreCase(brand)).map(CarMapper::toDto).toList();
    }
    public List<CarDto> findAll()
    {
       return repository.findAll().stream().map(CarMapper::toDto).toList();
    }

    public void deleteCar(Long id)
    {
        repository.deleteById(id);
    }


}
