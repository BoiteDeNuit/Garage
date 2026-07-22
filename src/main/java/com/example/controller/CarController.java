package com.example.controller;

import com.example.dto.CarDto;
import com.example.dto.GarageStats;
import com.example.exception.EntityNotFoundException;
import com.example.exception.StorageFullException;
import com.example.model.Car;
import com.example.service.GarageService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final GarageService garage;
    public CarController(GarageService garage) { this.garage=garage; }
    @GetMapping
    public List<CarDto> all() {
        return garage.findAll();
    }
    @GetMapping ("/{id}")
    public CarDto one(@PathVariable Long id)
    {
        return garage.getCar(id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto create(@Validated @RequestBody CarDto dto) throws StorageFullException
    {
        return garage.addCar(dto);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void carDelete(@PathVariable Long id){
            garage.deleteCar(id);
    }
    @GetMapping("/stats")
    public GarageStats stats()
    {
        return garage.stats();
    }

}
