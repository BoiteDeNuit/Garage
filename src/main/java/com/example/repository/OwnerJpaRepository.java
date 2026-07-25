package com.example.repository;

import com.example.model.Owner;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OwnerJpaRepository extends JpaRepository<Owner,Long> {
    @Query("select o from Owner o join fetch o.cars")
    List<Owner> findAllWithCars();
    @EntityGraph(attributePaths = "cars")
    @Override
    List<Owner> findAll();
}
