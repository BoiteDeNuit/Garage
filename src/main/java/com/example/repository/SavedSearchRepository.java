package com.example.repository;

import com.example.model.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);
    // Поиск ищется вместе с владельцем: чужой id найдётся как «нет такого», а не «не твоё»
    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);

    // Какие сохранённые поиски подходят к объявлению. Условия те же, что у ленты (ListingSpecifications.publicFeed):
    // пустой или пробельный фильтр не фильтрует, строки без учёта регистра, границы включаются, q — полнотекстовый.
    // Совпадение с лентой проверяет SavedSearchMatchTest. ->> отдаёт SQL NULL и для отсутствующего ключа, и для JSON null.
    // Один поиск на пользователя (DISTINCT ON): уведомление одно, даже если подошли несколько его поисков.
    // Продавцу о своём объявлении не сообщаем. Поисков просматривается столько, сколько их есть: это один проход
    // по saved_searches на каждое опубликованное объявление
    @Query(value = """
            select distinct on (s.user_id) s.id as "searchId", s.user_id as "userId"
            from saved_searches s
            join listings l on l.id = :listingId
            where l.status = 'ACTIVE'
              and s.user_id <> l.seller_id
              and (nullif(trim(s.criteria->>'brand'), '') is null or upper(l.brand) = upper(trim(s.criteria->>'brand')))
              and (nullif(trim(s.criteria->>'model'), '') is null or upper(l.model) = upper(trim(s.criteria->>'model')))
              and (nullif(trim(s.criteria->>'city'), '') is null or upper(l.city) = upper(trim(s.criteria->>'city')))
              and (s.criteria->>'yearFrom' is null or l.year >= (s.criteria->>'yearFrom')::int)
              and (s.criteria->>'yearTo' is null or l.year <= (s.criteria->>'yearTo')::int)
              and (s.criteria->>'priceFrom' is null or l.price >= (s.criteria->>'priceFrom')::numeric)
              and (s.criteria->>'priceTo' is null or l.price <= (s.criteria->>'priceTo')::numeric)
              and (s.criteria->>'mileageTo' is null or l.mileage_km <= (s.criteria->>'mileageTo')::int)
              and (s.criteria->>'fuelType' is null or l.fuel_type = s.criteria->>'fuelType')
              and (s.criteria->>'transmission' is null or l.transmission = s.criteria->>'transmission')
              and (s.criteria->>'bodyType' is null or l.body_type = s.criteria->>'bodyType')
              and (nullif(trim(s.criteria->>'q'), '') is null
                   or l.search_vector @@ websearch_to_tsquery('russian', trim(s.criteria->>'q')))
            order by s.user_id, s.id
            """, nativeQuery = true)
    List<SearchMatch> findMatching(@Param("listingId") Long listingId);
}
