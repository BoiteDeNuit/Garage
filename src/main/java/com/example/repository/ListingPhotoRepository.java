package com.example.repository;

import com.example.model.ListingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {
    @Query("select coalesce(max(p.position), 0) from ListingPhoto p where p.listing.id = :listingId")
    int maxPosition(@Param("listingId") Long listingId);
}
