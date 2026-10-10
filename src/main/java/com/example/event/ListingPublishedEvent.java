package com.example.event;

import com.example.model.Listing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record ListingPublishedEvent(Long listingId, Long sellerId, String brand, String model, BigDecimal price, Instant publishedAt) {
    public static ListingPublishedEvent from(Listing listing)
    {
        return new ListingPublishedEvent(listing.getId(), listing.getSeller().getId(), listing.getBrand(),
                listing.getModel(), listing.getPrice(), listing.getPublishedAt());
    }
    // id события выводится из объявления и момента публикации: повторная доставка того же сообщения даёт тот же id,
    // новая публикация из архива — новый. Это метод, а не поле: в JSON он не попадает, формат сообщения прежний,
    // и старые сообщения в топике читаются так же
    public UUID eventId()
    {
        return UUID.nameUUIDFromBytes((listingId + ":" + publishedAt).getBytes(StandardCharsets.UTF_8));
    }
}
