package com.example;

import com.example.repository.CarJpaRepository;
import com.example.repository.OwnerJpaRepository;
import com.example.service.GarageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoRunner implements CommandLineRunner {
    private final GarageService garage;
    private final CarJpaRepository repository;
    private final OwnerJpaRepository ownerRepository;
    public DemoRunner(GarageService garage, CarJpaRepository repository, OwnerJpaRepository ownerRepository)
    {
        this.garage = garage;
        this.repository = repository;
        this.ownerRepository = ownerRepository;
    }
    @Override
    public void run(String... args)
    {
        System.out.println("---ДЕМО---");
        System.out.println("Машин: " + garage.stats().count());
        garage.findByBrand("Toyota")
                .forEach(System.out::println);
        System.out.println("Статистика: " + garage.stats());
        System.out.println(repository.findByHorsePowerGreaterThan(300));
        System.out.println(repository.existsByEngineCode("2JZ-GTE"));
        System.out.println(repository.findByModelContainingIgnoreCase("SUPRA"));
        garage.demoNPlusOne();
    }
}
