package com.example.dto;

import java.time.Instant;

// Объявление в очереди и кто его проверяет. moderatorId пустой — свободно
public record ModerationItemDto(ListingDto listing, Instant submittedAt, Long moderatorId, Instant claimedAt) {
}
