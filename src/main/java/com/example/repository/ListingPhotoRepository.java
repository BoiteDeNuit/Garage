package com.example.repository;

import com.example.model.ListingPhoto;
import com.example.model.PhotoStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {
    @Query("select coalesce(max(p.position), 0) from ListingPhoto p where p.listing.id = :listingId")
    int maxPosition(@Param("listingId") Long listingId);
    // Фото ищется вместе с объявлением: чужой photoId под своим объявлением не найдётся
    Optional<ListingPhoto> findByIdAndListingId(Long id, Long listingId);
    List<ListingPhoto> findByListingIdAndStatusOrderByPosition(Long listingId, PhotoStatus status);
    List<ListingPhoto> findByListingIdOrderByPosition(Long listingId);
    @Query("select p.objectKey from ListingPhoto p where p.listing.id = :listingId")
    List<String> findObjectKeys(@Param("listingId") Long listingId);
    // Фото после удалённого сдвигаются на одно место вверх, дыр в нумерации не остаётся.
    // Одним UPDATE: строки обновляются в произвольном порядке, и на полпути два фото могут стоять на одном месте.
    // Это переживает только отложенная уникальность (V15)
    // Брошенные загрузки для уборки, по id после курсора: фото, которое уборка не смогла удалить, не попадётся снова
    @Query("select p.id from ListingPhoto p where p.status = :status and p.createdAt < :before and p.id > :afterId order by p.id")
    List<Long> findStaleIds(@Param("status") PhotoStatus status, @Param("before") Instant before, @Param("afterId") Long afterId, Pageable pageable);
    // Какие из ключей хранилища ещё числятся за фото. Остальные файлы — сироты
    @Query("select p.objectKey from ListingPhoto p where p.objectKey in :keys")
    List<String> findExistingKeys(@Param("keys") Collection<String> keys);
    @Modifying
    @Query("update ListingPhoto p set p.position = p.position - 1 where p.listing.id = :listingId and p.position > :position")
    void closeGap(@Param("listingId") Long listingId, @Param("position") int position);
}
