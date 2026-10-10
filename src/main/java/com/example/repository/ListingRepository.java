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
}
