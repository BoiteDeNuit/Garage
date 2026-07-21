package com.example.service;

import com.example.exception.EntityNotFoundException;
import com.example.exception.StorageFullException;
import com.example.model.Car;
import com.example.model.GarageStats;
import com.example.repository.CrudRepository;
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
    public Car addCar (Car car) throws StorageFullException
    {
        return repository.save(car);
    }
    public Car getCar(Long id)
    {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Машина с id: " + id + " не найдена"));
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
    public List<Car> findBy(Predicate<Car> condition)
    {
        return repository.findAll().stream().filter(condition).toList();
    }
    public List<Car> returnAll()
    {
       List<Car> all = repository.findAll();
       if(all.isEmpty())
       {
           System.out.println("Гараж пуст");
       }
       else
       {
           System.out.println("Все машины: \t");
           for(Car car : all)
           {
               System.out.println(car);
           }
       }
       return all;
    }
}
