package com.example.dto;

import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.ListingStatus;
import com.example.model.Transmission;

import java.math.BigDecimal;
import java.time.Instant;

// Логина продавца здесь нет: публичный список логинов облегчает подбор паролей
public record ListingDto(Long id,
                         Long sellerId,
                         ListingStatus status,
                         String brand,
                         String model,
                         String engineCode,
                         int horsePower,
                         int year,
                         Integer mileageKm,
                         FuelType fuelType,
                         Transmission transmission,
                         BodyType bodyType,
                         BigDecimal price,
                         String city,
                         String description,
                         Instant createdAt,
                         Instant updatedAt,
                         Instant publishedAt,
                         Long version) {
}
