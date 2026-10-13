package com.example.dto;

import com.example.model.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;

// savedSearchId — у NEW_LISTING, oldPrice и newPrice — у PRICE_DROP. У LISTING_EXPIRED только listingId
public record NotificationDto(Long id, NotificationType type, Long listingId, Long savedSearchId,
                              BigDecimal oldPrice, BigDecimal newPrice, Instant createdAt, boolean read) {
}
