package com.example;

import com.example.model.Car;
import com.example.service.GarageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoRunner implements CommandLineRunner {
    private final GarageService garage;
    public DemoRunner(GarageService garage)
    {
        this.garage = garage;
    }
    @Override
    public void run(String... args)
    {
        System.out.println("---ДЕМО---");
        System.out.println("Машин: " + garage.stats().count());
        garage.findBy(c -> c.getHorsePower() > 300)
                .forEach(System.out::println);
        System.out.println("Статистика: " + garage.stats());
    }
}
