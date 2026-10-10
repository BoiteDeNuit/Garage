package com.example.event;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

// Цена опубликованного объявления снизилась. Ключ сообщения — id объявления: два снижения подряд попадут
// в одну партицию и придут по порядку
public record ListingPriceDroppedEvent(Long listingId, Long sellerId, BigDecimal oldPrice, BigDecimal newPrice, Instant changedAt) {
    // Как у ListingPublishedEvent: повторная доставка — тот же id, следующее снижение — новый
    public UUID eventId()
    {
        return UUID.nameUUIDFromBytes(("price-drop:" + listingId + ":" + changedAt).getBytes(StandardCharsets.UTF_8));
    }
}
