package com.example.controller;

import com.example.service.GarageService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final GarageService garage;
    public CarController(GarageService garage)
    {
        this.garage=garage;
    }
}
