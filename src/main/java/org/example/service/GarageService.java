package org.example;

import org.example.model.Car;
import org.example.model.GarageStats;
import org.example.repository.CrudRepository;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

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
}
