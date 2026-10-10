package com.example.repository;

import com.example.model.ListingStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingStatusChangeRepository extends JpaRepository<ListingStatusChange, Long> {
    List<ListingStatusChange> findByListingIdOrderByChangedAtAscIdAsc(Long listingId);
}
