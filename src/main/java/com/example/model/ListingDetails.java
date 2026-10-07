package com.example.model;

import java.math.BigDecimal;

// Что продавец пишет про машину. Статус, продавец и время сюда не входят
public record ListingDetails(String brand,
                             String model,
                             String engineCode,
                             int horsePower,
                             int year,
                             Integer mileageKm,
                             BigDecimal price,
                             String city,
                             String description) {
}
