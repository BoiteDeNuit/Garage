package com.example.model;

import jakarta.persistence.*;

// Пара «марка модель» из справочника подсказок (V14). Пишется только нативным upsert-ом в CarModelRepository
@Entity
@Table(name = "car_models")
public class CarModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String model;
    protected CarModel() {}

    public Long getId()
    {
        return id;
    }
    public String getBrand()
    {
        return brand;
    }
    public String getModel()
    {
        return model;
    }
}
