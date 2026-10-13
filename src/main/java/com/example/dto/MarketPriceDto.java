package com.example.dto;

import com.example.model.MarketVerdict;

import java.math.BigDecimal;
import java.time.Instant;

// Где цена объявления среди похожих: та же марка, модель и год. Без оценки (меньше пяти похожих)
// цифры пустые, verdict — NOT_ENOUGH_DATA. deltaPercent — насколько цена выше (+) или ниже (−) медианы
public record MarketPriceDto(Long listingId,
                             BigDecimal price,
                             MarketVerdict verdict,
                             Long comparable,
                             BigDecimal p25,
                             BigDecimal median,
                             BigDecimal p75,
                             BigDecimal deltaPercent,
                             Instant calculatedAt) {
}
