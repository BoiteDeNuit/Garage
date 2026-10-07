package com.example.dto;

import java.math.BigDecimal;

public record ListingPriceDto(Long listingId, String currency, BigDecimal rate, BigDecimal price){
}
