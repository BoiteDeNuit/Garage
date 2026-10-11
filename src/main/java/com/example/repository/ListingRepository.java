package com.example.repository;

import com.example.model.Listing;
import com.example.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing,Long>, JpaSpecificationExecutor<Listing> {
    Page<Listing> findBySellerId(Long sellerId, Pageable pageable);
    Page<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status, Pageable pageable);
    Optional<Listing> findByIdAndStatusIn(Long id, Collection<ListingStatus> statuses);
    // Продавец подтягивается тем же запросом через join: без графа на странице из 10 строк было бы 12 SQL
    @EntityGraph(attributePaths = "seller")
    @Query("select l from Listing l")
    Page<Listing> findAllWithSeller(Pageable pageable);
    @EntityGraph(attributePaths = "seller")
    Page<Listing> findWithSellerByStatus(ListingStatus status, Pageable pageable);
    long countByStatus(ListingStatus status);
    @Query("select coalesce(avg(l.horsePower),0) from Listing l where l.status = :status")
    Double averageHorsePower(@Param("status") ListingStatus status);
    Optional<Listing> findFirstByStatusOrderByHorsePowerDesc(ListingStatus status);

    // Оценка из market_prices (V33) по уникальному индексу. Марка и модель там в верхнем регистре
    @Query(value = "select listings, p25, median, p75, calculated_at as calculatedAt from market_prices "
            + "where brand = upper(:brand) and model = upper(:model) and year = :year", nativeQuery = true)
    Optional<MarketPriceView> findMarketPrice(@Param("brand") String brand, @Param("model") String model, @Param("year") int year);

    // Очередь модерации: сначала те, что ждут дольше. idx_listings_review_queue (V22)
    Page<Listing> findByStatusOrderBySubmittedAtAscIdAsc(ListingStatus status, Pageable pageable);

    // Захват на проверку одним условным UPDATE: проверка «никто не взял» и запись идут в одном операторе,
    // поэтому из двух одновременных захватов строку изменит один, второй получит 0.
    // Отдельный SELECT «свободно?» и потом UPDATE пропустили бы обоих. Свой же захват можно повторить,
    // брошенный дольше claim-ttl — забрать. Своё объявление модератор не берёт.
    // version + 1: массовый UPDATE идёт мимо @Version, а версия должна меняться при каждом изменении строки
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Listing l set l.moderatorId = :moderatorId, l.claimedAt = :now, l.version = l.version + 1 "
            + "where l.id = :id and l.status = :pending and l.seller.id <> :moderatorId "
            + "and (l.moderatorId is null or l.moderatorId = :moderatorId or l.claimedAt < :expiredBefore)")
    int claim(@Param("id") Long id, @Param("moderatorId") Long moderatorId, @Param("pending") ListingStatus pending,
              @Param("now") Instant now, @Param("expiredBefore") Instant expiredBefore);

    // Блокировка продавца убирает его объявления с витрины и из очереди одним оператором: опубликованные — в архив,
    // ждущие проверки — отклонены с причиной блокировки, захват модератора снят. Запись в журнал — в том же
    // операторе (CTE), по строке на объявление. Внутри SET статус ещё старый, в RETURNING уже новый.
    // Возвращает id: их карточки надо убрать из кэша. Черновики, проданные и архив не трогает
    @Query(value = """
            with hidden as (
                update listings
                set status = case status when 'ACTIVE' then 'ARCHIVED' else 'REJECTED' end,
                    rejection_reason = case status when 'PENDING_REVIEW' then :reason else rejection_reason end,
                    moderator_id = null,
                    claimed_at = null,
                    version = version + 1,
                    updated_at = :now
                where seller_id = :sellerId and status in ('ACTIVE', 'PENDING_REVIEW')
                returning id, status
            ), logged as (
                insert into listing_status_history (listing_id, from_status, to_status, actor_id, reason, changed_at)
                select id, case status when 'ARCHIVED' then 'ACTIVE' else 'PENDING_REVIEW' end, status, :adminId, :reason, :now
                from hidden
            )
            select id from hidden order by id
            """, nativeQuery = true)
    List<Long> hideAllOfSeller(@Param("sellerId") Long sellerId, @Param("adminId") Long adminId,
                               @Param("reason") String reason, @Param("now") Instant now);

    // Пачка опубликованных дольше срока — в архив, запись в журнал и уведомление продавцу, всё одним оператором.
    // Старые идут первыми по idx_listings_feed. SKIP LOCKED: объявление, которое продавец сейчас правит, ждать
    // не будем, его заберёт следующий запуск. event_id из id и времени публикации: повтор пачки не задвоит уведомление,
    // а после новой публикации и нового срока уведомление будет новым
    @Query(value = """
            with expired as (
                update listings
                set status = 'ARCHIVED',
                    version = version + 1,
                    updated_at = :now
                where id in (
                    select id from listings
                    where status = 'ACTIVE' and published_at < :before
                    order by published_at, id
                    limit :batch
                    for update skip locked
                )
                returning id, seller_id, published_at
            ), logged as (
                insert into listing_status_history (listing_id, from_status, to_status, actor_id, reason, changed_at)
                select id, 'ACTIVE', 'ARCHIVED', null, :reason, :now
                from expired
            ), notified as (
                insert into notifications (user_id, event_id, type, listing_id, created_at)
                select seller_id, md5('expired:' || id || ':' || published_at)::uuid, 'LISTING_EXPIRED', id, :now
                from expired
                on conflict (user_id, event_id) do nothing
            )
            select id from expired order by id
            """, nativeQuery = true)
    List<Long> expireBatch(@Param("before") Instant before, @Param("now") Instant now,
                           @Param("reason") String reason, @Param("batch") int batch);
}
