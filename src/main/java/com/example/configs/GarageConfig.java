package com.example.configs;

import com.example.model.Car;
import com.example.repository.CrudRepository;
import com.example.repository.InMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GarageConfig {
    @Bean
    public CrudRepository<Car> carRepository(){
        return new InMemoryRepository<>(Car.class,1000);
    }
}
