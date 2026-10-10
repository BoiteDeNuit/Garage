package com.example.dto;

import com.example.model.NotificationType;

import java.time.Instant;

public record NotificationDto(Long id, NotificationType type, Long listingId, Long savedSearchId, Instant createdAt, boolean read) {
}
