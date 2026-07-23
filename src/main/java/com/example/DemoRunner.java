package com.example;

import com.example.model.Car;
import com.example.repository.CarJpaRepository;
import com.example.service.GarageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoRunner implements CommandLineRunner {
    private final GarageService garage;
    private final CarJpaRepository repository;
    public DemoRunner(GarageService garage,CarJpaRepository repository)
    {
        this.garage = garage;
        this.repository = repository;
    }
    @Override
    public void run(String... args)
    {
        System.out.println("---ДЕМО---");
        System.out.println("Машин: " + garage.stats().count());
        garage.findByBrand("Toyota")
                .forEach(System.out::println);
        System.out.println("Статистика: " + garage.stats());
        repository.findByHorsePowerGreaterThan(300);
        repository.existsByEngineCode("2JZ-GTE");
        repository.findByModelContainingIgnoreCase("SUPRA");
    }
}
