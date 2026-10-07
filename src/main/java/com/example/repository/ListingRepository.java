package com.example.repository;

import com.example.model.Listing;
import com.example.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing,Long> {
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);
    Page<Listing> findByStatusAndBrandIgnoreCase(ListingStatus status, String brand, Pageable pageable);
    Optional<Listing> findByIdAndStatusIn(Long id, Collection<ListingStatus> statuses);
    long countByStatus(ListingStatus status);
    @Query("select coalesce(avg(l.horsePower),0) from Listing l where l.status = :status")
    Double averageHorsePower(@Param("status") ListingStatus status);
    Optional<Listing> findFirstByStatusOrderByHorsePowerDesc(ListingStatus status);
}
