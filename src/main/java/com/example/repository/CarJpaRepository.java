package com.example.repository;

import com.example.model.Car;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface CarJpaRepository extends JpaRepository<Car,Long> {
    Page<Car> findByBrandIgnoreCase(String brand, Pageable pageable);
    @Query("select coalesce(avg(c.horsePower),0) from Car c")
    Double averageHorsePower();
    Optional<Car> findFirstByOrderByHorsePowerDesc();

    boolean existsByEngineCode(String code);
}
