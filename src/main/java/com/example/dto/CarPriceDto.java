package com.example.dto;

import java.math.BigDecimal;

public record CarPriceDto (Long carId, String currency, BigDecimal rate, BigDecimal price){
}
