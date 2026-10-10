package com.example.dto;

import com.example.model.ListingStatus;

import java.time.Instant;

// fromStatus пустой у первой записи: объявление создано черновиком
public record StatusChangeDto(ListingStatus fromStatus, ListingStatus toStatus, Long actorId, String reason, Instant changedAt) {
}
