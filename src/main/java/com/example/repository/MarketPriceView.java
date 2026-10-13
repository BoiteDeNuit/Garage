package com.example.repository;

import java.math.BigDecimal;
import java.time.Instant;

// Строка market_prices (V33). Проекция, а не сущность: представление только читается, пишет его REFRESH
public interface MarketPriceView {
    long getListings();
    BigDecimal getP25();
    BigDecimal getMedian();
    BigDecimal getP75();
    Instant getCalculatedAt();
}
