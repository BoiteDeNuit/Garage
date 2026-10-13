package com.example.dto;

import java.time.Instant;

public record SavedSearchDto(Long id, String name, ListingSearchCriteria criteria, Instant createdAt) {
}
