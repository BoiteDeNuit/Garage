package com.example.repository;

import com.example.dto.ListingSearchCriteria;
import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.Listing;
import com.example.model.ListingStatus;
import com.example.model.Listing_;
import com.example.model.Transmission;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;

// Куски фильтра ленты. Пустое значение не фильтрует: unrestricted() в allOf ничего не добавляет к where
public final class ListingSpecifications {
    private ListingSpecifications() {}

    // Фильтр публичной ленты. Статус в нём всегда: какие бы параметры ни пришли, видны только опубликованные.
    // Тем же условиям следует SQL сопоставления сохранённых поисков (SavedSearchRepository.findMatching)
    public static Specification<Listing> publicFeed(ListingSearchCriteria criteria)
    {
        return Specification.allOf(
                hasStatus(ListingStatus.ACTIVE),
                matches(criteria.q()),
                brand(criteria.brand()),
                model(criteria.model()),
                yearBetween(criteria.yearFrom(), criteria.yearTo()),
                priceBetween(criteria.priceFrom(), criteria.priceTo()),
                mileageAtMost(criteria.mileageTo()),
                city(criteria.city()),
                fuelType(criteria.fuelType()),
                transmission(criteria.transmission()),
                bodyType(criteria.bodyType()));
    }
    public static Specification<Listing> hasStatus(ListingStatus status)
    {
        return (root, query, cb) -> cb.equal(root.get(Listing_.status), status);
    }
    public static Specification<Listing> brand(String brand)
    {
        return equalsIgnoreCase(Listing_.brand, brand);
    }
    public static Specification<Listing> model(String model)
    {
        return equalsIgnoreCase(Listing_.model, model);
    }
    public static Specification<Listing> city(String city)
    {
        return equalsIgnoreCase(Listing_.city, city);
    }
    public static Specification<Listing> yearBetween(Integer from, Integer to)
    {
        return between(Listing_.year, from, to);
    }
    public static Specification<Listing> priceBetween(BigDecimal from, BigDecimal to)
    {
        return between(Listing_.price, from, to);
    }
    public static Specification<Listing> mileageAtMost(Integer max)
    {
        return between(Listing_.mileageKm, null, max);
    }
    // Строки строго после курсора в порядке (published_at desc, id desc).
    // Не (a < p) or (a = p and b < id) одним OR: такое условие индекс по published_at не ограничивает,
    // и Postgres читал бы ленту с начала. Отдельное published_at <= p даёт индексу точку старта
    public static Specification<Listing> after(Instant publishedAt, Long id)
    {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get(Listing_.publishedAt), publishedAt),
                cb.or(cb.lessThan(root.get(Listing_.publishedAt), publishedAt),
                        cb.lessThan(root.get(Listing_.id), id)));
    }
    // Полнотекстовый поиск по марке, модели и описанию: search_vector @@ websearch_to_tsquery('russian', ?).
    // Под это условие подходит GIN idx_listings_search (V13). Слова приводятся к основе: «пробегом» найдёт «пробег».
    // Запрос из одних стоп-слов («и», «на») Postgres превращает в пустой, а с пустым запросом @@ ложно — ничего не найдётся
    public static Specification<Listing> matches(String text)
    {
        if(text == null || text.isBlank())
        {
            return Specification.unrestricted();
        }
        String trimmed = text.trim();
        return (root, query, cb) -> cb.isTrue(cb.function("fts_matches", Boolean.class, searchVector(root, cb), ((HibernateCriteriaBuilder) cb).value(trimmed)));
    }
    // Не фильтр, а порядок: сначала релевантные, при равном ранге новые. Свой order by Spring Data ставит,
    // только если в Pageable есть сортировка, а из count-запроса order by убирает сам.
    // ts_rank считается для каждой найденной строки: GIN находит строки, но не упорядочивает их
    public static Specification<Listing> mostRelevantFirst(String text)
    {
        if(text == null || text.isBlank())
        {
            return Specification.unrestricted();
        }
        String trimmed = text.trim();
        return (root, query, cb) -> {
            Expression<Float> rank = cb.function("fts_rank", Float.class, searchVector(root, cb), ((HibernateCriteriaBuilder) cb).value(trimmed));
            query.orderBy(cb.desc(rank), cb.desc(root.get(Listing_.publishedAt)), cb.desc(root.get(Listing_.id)));
            return null;
        };
    }
    public static Specification<Listing> fuelType(FuelType fuelType)
    {
        return equalTo(Listing_.fuelType, fuelType);
    }
    public static Specification<Listing> transmission(Transmission transmission)
    {
        return equalTo(Listing_.transmission, transmission);
    }
    public static Specification<Listing> bodyType(BodyType bodyType)
    {
        return equalTo(Listing_.bodyType, bodyType);
    }
    // upper(колонка) = upper(?), как в ...IgnoreCase у Spring Data. Для марки под левую часть стоит частичный индекс
    // idx_listings_brand_feed (только ACTIVE, V10), модель и город без индекса.
    // Регистр меняет Postgres с обеих сторон: toUpperCase в Java расходится с upper() базы
    // ("Straße" -> "STRASSE" против "STRAßE", а при локали C Postgres кириллицу не трогает).
    // value() — параметр запроса, literal() вписал бы строку прямо в текст SQL
    private static Specification<Listing> equalsIgnoreCase(SingularAttribute<Listing, String> attribute, String value)
    {
        if(value == null || value.isBlank())
        {
            return Specification.unrestricted();
        }
        String trimmed = value.trim();
        return (root, query, cb) -> cb.equal(cb.upper(root.get(attribute)), cb.upper(((HibernateCriteriaBuilder) cb).value(trimmed)));
    }
    // Колонка search_vector не замаплена: функция из FullTextFunctions подставляет её с алиасом таблицы из root
    private static Expression<Object> searchVector(Root<Listing> root, CriteriaBuilder cb)
    {
        return cb.function("listing_search_vector", Object.class, root.get(Listing_.id));
    }
    private static <V> Specification<Listing> equalTo(SingularAttribute<Listing, V> attribute, V value)
    {
        if(value == null)
        {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get(attribute), value);
    }
    // У черновика цена и пробег бывают null: сравнение с null в SQL не истинно, такие строки фильтр отсекает
    private static <V extends Comparable<? super V>> Specification<Listing> between(SingularAttribute<Listing, V> attribute, V from, V to)
    {
        if(from == null && to == null)
        {
            return Specification.unrestricted();
        }
        if(to == null)
        {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(attribute), from);
        }
        if(from == null)
        {
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(attribute), to);
        }
        return (root, query, cb) -> cb.between(root.get(attribute), from, to);
    }
}
