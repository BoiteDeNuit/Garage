package com.example.repository;

import com.example.model.Favorite;
import com.example.model.FavoriteId;
import com.example.model.Listing;
import com.example.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface FavoriteRepository extends Repository<Favorite, FavoriteId> {
    // Идемпотентно и без гонки: проверка «уже есть?» отдельным SELECT пропустила бы два одновременных PUT
    @Modifying
    @Query(value = "insert into favorites (user_id, listing_id, created_at) values (:userId, :listingId, :now) on conflict do nothing", nativeQuery = true)
    void add(@Param("userId") Long userId, @Param("listingId") Long listingId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Favorite f where f.id.userId = :userId and f.id.listingId = :listingId")
    void remove(@Param("userId") Long userId, @Param("listingId") Long listingId);

    // Недавно добавленные первыми. Порядок задан в запросе, сортировку клиента контроллер не передаёт
    @Query(value = "select l from Favorite f join f.listing l where f.id.userId = :userId and l.status in :statuses "
            + "order by f.createdAt desc, l.id desc",
            countQuery = "select count(f) from Favorite f join f.listing l where f.id.userId = :userId and l.status in :statuses")
    Page<Listing> findListings(@Param("userId") Long userId, @Param("statuses") Collection<ListingStatus> statuses, Pageable pageable);
}
