package com.example.repository;

import com.example.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Повторная доставка события упирается в UNIQUE (user_id, event_id). Возвращает 1, если строка добавилась
    @Modifying
    @Query(value = "insert into notifications (user_id, event_id, type, listing_id, saved_search_id, created_at) "
            + "values (:userId, :eventId, :type, :listingId, :searchId, :now) on conflict (user_id, event_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("eventId") UUID eventId, @Param("type") String type,
                       @Param("listingId") Long listingId, @Param("searchId") Long searchId, @Param("now") Instant now);

    // Всем, у кого объявление в избранном, кроме продавца. Одним запросом, без загрузки списка в приложение.
    // Объявление к этому времени могли снять в архив: тогда сообщать не о чем
    @Modifying
    @Query(value = """
            insert into notifications (user_id, event_id, type, listing_id, old_price, new_price, created_at)
            select f.user_id, :eventId, 'PRICE_DROP', l.id, :oldPrice, :newPrice, :now
            from favorites f
            join listings l on l.id = f.listing_id
            where f.listing_id = :listingId and l.status = 'ACTIVE' and f.user_id <> l.seller_id
            on conflict (user_id, event_id) do nothing
            """, nativeQuery = true)
    int insertPriceDrops(@Param("listingId") Long listingId, @Param("eventId") UUID eventId,
                         @Param("oldPrice") BigDecimal oldPrice, @Param("newPrice") BigDecimal newPrice, @Param("now") Instant now);

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
