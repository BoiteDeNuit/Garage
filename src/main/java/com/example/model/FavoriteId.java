package com.example.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Составной ключ избранного. Record: equals и hashCode по обоим полям, как и нужно ключу
@Embeddable
public record FavoriteId(
        @Column(name = "user_id") Long userId,
        @Column(name = "listing_id") Long listingId) {
}
