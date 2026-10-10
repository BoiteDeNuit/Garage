package com.example.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "listing_photos")
public class ListingPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    private Listing listing;
    @Column(nullable = false, unique = true, updatable = false, length = 200)
    private String objectKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoStatus status;
    @Column(nullable = false, updatable = false, length = 50)
    private String contentType;
    @Column(nullable = false, updatable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    protected ListingPhoto() {}
    public static ListingPhoto pending(Listing listing, String objectKey, String contentType, long sizeBytes, int position, Instant now)
    {
        ListingPhoto photo = new ListingPhoto();
        photo.listing=listing;
        photo.objectKey=objectKey;
        photo.status=PhotoStatus.PENDING;
        photo.contentType=contentType;
        photo.sizeBytes=sizeBytes;
        photo.position=position;
        photo.createdAt=now;
        return photo;
    }

    public Long getId()
    {
        return id;
    }
    public Listing getListing()
    {
        return listing;
    }
    public String getObjectKey()
    {
        return objectKey;
    }
    public PhotoStatus getStatus()
    {
        return status;
    }
    public String getContentType()
    {
        return contentType;
    }
    public long getSizeBytes()
    {
        return sizeBytes;
    }
    public int getPosition()
    {
        return position;
    }
    public Instant getCreatedAt()
    {
        return createdAt;
    }
}
