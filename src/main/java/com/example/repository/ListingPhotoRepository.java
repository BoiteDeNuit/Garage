package com.example.repository;

import com.example.model.ListingPhoto;
import com.example.model.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {
    @Query("select coalesce(max(p.position), 0) from ListingPhoto p where p.listing.id = :listingId")
    int maxPosition(@Param("listingId") Long listingId);
    // Фото ищется вместе с объявлением: чужой photoId под своим объявлением не найдётся
    Optional<ListingPhoto> findByIdAndListingId(Long id, Long listingId);
    List<ListingPhoto> findByListingIdAndStatusOrderByPosition(Long listingId, PhotoStatus status);
}
