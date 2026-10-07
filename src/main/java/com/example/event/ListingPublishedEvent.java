package com.example.event;

import com.example.model.Listing;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingPublishedEvent(Long listingId, Long sellerId, String brand, String model, BigDecimal price, Instant publishedAt) {
    public static ListingPublishedEvent from(Listing listing)
    {
        return new ListingPublishedEvent(listing.getId(), listing.getSeller().getId(), listing.getBrand(),
                listing.getModel(), listing.getPrice(), listing.getPublishedAt());
    }
}
