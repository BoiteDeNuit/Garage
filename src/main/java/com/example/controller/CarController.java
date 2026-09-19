package com.example.controller;

import com.example.dto.CarDto;
import com.example.dto.CarPriceDto;
import com.example.dto.GarageStats;
import com.example.exception.StorageFullException;
import com.example.service.GarageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final GarageService garage;
    public CarController(GarageService garage) { this.garage=garage; }
    @GetMapping
    public List<CarDto> all(@RequestParam(required = false) String brand) {
        if (brand == null)
        {
            return garage.findAll();
        }
        return garage.findByBrand(brand);
    }
    @GetMapping ("/{id}")
    public CarDto one(@PathVariable Long id)
    {
        return garage.getCar(id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto create(@Valid @RequestBody CarDto dto) throws StorageFullException
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
    @GetMapping("/{id}/price")
    public CarPriceDto price(@PathVariable Long id, @RequestParam(defaultValue = "USD") String currency)
    {
        return garage.priceIn(id,currency);
    }

}
