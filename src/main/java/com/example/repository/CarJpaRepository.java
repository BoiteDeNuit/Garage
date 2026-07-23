package com.example.repository;

import com.example.model.Car;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarJpaRepository extends JpaRepository<Car,Long> {
    List<Car> findByBrandIgnoreCase(String brand);
    List<Car> findByHorsePowerGreaterThan(int hp);
    List<Car> findByModelContainingIgnoreCase(String part);
    boolean existsByEngineCode(String code);
}
