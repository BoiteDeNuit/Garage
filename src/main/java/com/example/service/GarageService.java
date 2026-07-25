package com.example.service;

import com.example.dto.CarDto;
import com.example.dto.CarMapper;
import com.example.exception.EntityNotFoundException;
import com.example.model.Car;
import com.example.dto.GarageStats;
import com.example.model.Owner;
import com.example.repository.CarJpaRepository;
import com.example.repository.OwnerJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
@Service
public class GarageService{
    private final CarJpaRepository repository;
    private final OwnerJpaRepository ownerRepository;
    public GarageService (CarJpaRepository repository, OwnerJpaRepository ownerRepository)
    {
        this.repository=repository;
        this.ownerRepository=ownerRepository;
    }
    @Transactional
    public void demoNPlusOne(){
        List<Owner> owners = ownerRepository.findAll();
        for(Owner owner : owners)
        {
            System.out.println(owner.getName() +" " + owner.getCars().size() + " Машин");
        }
    }
    public List<Owner> findAllOwners()
    {
        return ownerRepository.findAll();
    }
    public CarDto addCar (CarDto dto)
    {
        Car car = CarMapper.toCar(dto);
        return CarMapper.toDto(repository.save(car));
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

    public void deleteCar(Long id)
    {
        repository.deleteById(id);
    }


}
