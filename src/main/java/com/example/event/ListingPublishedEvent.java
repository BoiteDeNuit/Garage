package com.example.event;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingPublishedEvent(Long listingId, Long sellerId, String brand, String model, BigDecimal price, Instant publishedAt) {}
