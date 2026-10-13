package com.example.model;

import jakarta.persistence.*;

import java.time.Instant;

// Только для чтения через JPQL: добавляет и удаляет FavoriteRepository запросами, без загрузки сущности
@Entity
@Table(name = "favorites")
public class Favorite {
    @EmbeddedId
    private FavoriteId id;
    @MapsId("listingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    protected Favorite() {}

    public FavoriteId getId()
    {
        return id;
    }
    public Listing getListing()
    {
        return listing;
    }
    public Instant getCreatedAt()
    {
        return createdAt;
    }
}
