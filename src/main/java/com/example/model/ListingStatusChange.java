package com.example.model;

import jakarta.persistence.*;

import java.time.Instant;

// Строка журнала смены статусов. Только добавляется, никогда не меняется
@Entity
@Table(name = "listing_status_history")
public class ListingStatusChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "listing_id", nullable = false, updatable = false)
    private Long listingId;
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private ListingStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ListingStatus toStatus;
    @Column(name = "actor_id", updatable = false)
    private Long actorId;
    @Column(updatable = false, length = 500)
    private String reason;
    @Column(nullable = false, updatable = false)
    private Instant changedAt;
    protected ListingStatusChange() {}
    public ListingStatusChange(Long listingId, ListingStatus fromStatus, ListingStatus toStatus, Long actorId, String reason, Instant changedAt)
    {
        this.listingId=listingId;
        this.fromStatus=fromStatus;
        this.toStatus=toStatus;
        this.actorId=actorId;
        this.reason=reason;
        this.changedAt=changedAt;
    }

    public Long getId()
    {
        return id;
    }
    public Long getListingId()
    {
        return listingId;
    }
    public ListingStatus getFromStatus()
    {
        return fromStatus;
    }
    public ListingStatus getToStatus()
    {
        return toStatus;
    }
    public Long getActorId()
    {
        return actorId;
    }
    public String getReason()
    {
        return reason;
    }
    public Instant getChangedAt()
    {
        return changedAt;
    }
}
