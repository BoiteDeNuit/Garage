package com.example.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Создаётся только запросом NotificationRepository.insertIfAbsent: дубль по (user_id, event_id) молча пропускается
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(nullable = false, updatable = false)
    private UUID eventId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private NotificationType type;
    @Column(name = "listing_id", nullable = false, updatable = false)
    private Long listingId;
    @Column(name = "saved_search_id")
    private Long savedSearchId;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant readAt;
    protected Notification() {}

    public Long getId()
    {
        return id;
    }
    public Long getUserId()
    {
        return userId;
    }
    public UUID getEventId()
    {
        return eventId;
    }
    public NotificationType getType()
    {
        return type;
    }
    public Long getListingId()
    {
        return listingId;
    }
    public Long getSavedSearchId()
    {
        return savedSearchId;
    }
    public Instant getCreatedAt()
    {
        return createdAt;
    }
    public Instant getReadAt()
    {
        return readAt;
    }
}
