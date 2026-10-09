package com.example.service;

import com.example.client.CurrencyClient;
import com.example.dto.AdminListingDto;
import com.example.dto.FeedCursor;
import com.example.dto.FeedPage;
import com.example.dto.ListingDto;
import com.example.dto.ListingMapper;
import com.example.dto.ListingPriceDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingSearchCriteria;
import com.example.dto.ListingStats;
import com.example.dto.ListingUpdateRequest;
import com.example.event.ListingPublishedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.exception.ListingStateException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.model.ListingStatus;
import com.example.repository.AppUserRepository;
import com.example.repository.ListingRepository;
import com.example.repository.ListingSpecifications;
import com.example.security.AppUserPrincipal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ListingService {
    private static final Sort FEED_ORDER = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private final ListingRepository repository;
    private final AppUserRepository users;
    private final ListingReader reader;
    private final ListingAccessPolicy policy;
    private final CarModelCatalog catalog;
    private final CurrencyClient currencyClient;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Counter listingsCreated;
    public ListingService(ListingRepository repository,
                          AppUserRepository users,
                          ListingReader reader,
                          ListingAccessPolicy policy,
                          CarModelCatalog catalog,
                          CurrencyClient currencyClient,
                          ApplicationEventPublisher events,
                          MeterRegistry meterRegistry,
                          Clock clock)
    {
        this.repository=repository;
        this.users=users;
        this.reader=reader;
        this.policy=policy;
        this.catalog=catalog;
        this.currencyClient=currencyClient;
        this.events=events;
        this.clock=clock;
        // Не listings.created: Prometheus-клиент считает _created служебным суффиксом счётчика и отрезает его
        this.listingsCreated=Counter.builder("listings.added")
                .description("Создано объявлений")
                .register(meterRegistry);
    }
    // Продавец берётся из токена, а не из запроса. getReferenceById не делает лишний SELECT в users
    @Transactional
    public ListingDto create(ListingRequest request, AppUserPrincipal seller)
    {
        Listing listing = Listing.draft(users.getReferenceById(seller.getId()), ListingMapper.toDetails(request), Instant.now(clock));
        Listing saved = repository.save(listing);
        listingsCreated.increment();
        return ListingMapper.toDto(saved);
    }
    // Чужой черновик отдаёт 404, а не 403: иначе по коду ответа видно, что объявление существует
    public ListingDto get(Long id, @Nullable AppUserPrincipal viewer)
    {
        ListingDto card = reader.findCard(id);
        if(!policy.canView(card.status(), card.sellerId(), viewer))
        {
            throw notFound(id);
        }
        return card;
    }
    // Смена статуса. Порядок проверок: нет или не видно -> 404, видно, но не твоё -> 403, переход запрещён -> 409.
    // Кэш карточки сбрасывается после коммита: кэш-прокси снаружи транзакционного (порядок в CacheConfig)
    // Версия от клиента закрывает lost update между GET и PUT: двое открыли форму, второй затёр бы первого.
    // @Version ловит только пересекающиеся транзакции, а не правки с разницей в минуты.
    // Записать версию клиента в сущность нельзя: Hibernate проверяет ту, что прочитал сам, поэтому сравниваем руками
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto update(Long id, ListingUpdateRequest request, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, ListingAction.EDIT);
        if(!listing.getVersion().equals(request.version()))
        {
            throw ListingStateException.staleVersion(listing.getVersion());
        }
        listing.updateDetails(ListingMapper.toDetails(request), Instant.now(clock));
        // Опубликованное видят покупатели: новая марка или модель сразу попадает в подсказки
        if(listing.getStatus() == ListingStatus.ACTIVE)
        {
            catalog.remember(listing.getBrand(), listing.getModel());
        }
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto publish(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, ListingAction.PUBLISH);
        listing.publish(Instant.now(clock));
        catalog.remember(listing.getBrand(), listing.getModel());
        events.publishEvent(ListingPublishedEvent.from(listing));
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto markSold(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, ListingAction.MARK_SOLD);
        listing.markSold(Instant.now(clock));
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto archive(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, ListingAction.ARCHIVE);
        listing.archive(Instant.now(clock));
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public void delete(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, ListingAction.DELETE);
        listing.checkDeletable();
        repository.delete(listing);
    }
    // Статус в фильтре всегда: какие бы параметры ни пришли, в ленту попадают только опубликованные.
    // Поиск по словам без сортировки от клиента идёт по релевантности: ListingSort.forTextSearch сортировку не ставит
    @Transactional(readOnly = true)
    public Page<ListingDto> findPublic(ListingSearchCriteria criteria, Pageable pageable)
    {
        Specification<Listing> filter = publicFilter(criteria);
        if(criteria.hasText() && pageable.getSort().isUnsorted())
        {
            filter = filter.and(ListingSpecifications.mostRelevantFirst(criteria.q()));
        }
        return repository.findAll(filter, pageable).map(ListingMapper::toDto);
    }
    // Лента по курсору: без count и без offset. Берём на одну строку больше, чтобы узнать, есть ли продолжение.
    // Порядок фиксированный — по нему построен курсор и индекс idx_listings_feed. С q лента тоже по дате:
    // курсор по рангу не построить, ранг не хранится и не индексируется
    @Transactional(readOnly = true)
    public FeedPage findFeed(ListingSearchCriteria criteria, @Nullable FeedCursor after, int size)
    {
        Specification<Listing> filter = publicFilter(criteria);
        if(after != null)
        {
            filter = filter.and(ListingSpecifications.after(after.publishedAt(), after.id()));
        }
        List<Listing> rows = repository.findBy(filter, query -> query.sortBy(FEED_ORDER).limit(size + 1).all());
        boolean hasNext = rows.size() > size;
        List<Listing> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? FeedCursor.after(page.getLast()).encode() : null;
        return new FeedPage(page.stream().map(ListingMapper::toDto).toList(), nextCursor);
    }
    private Specification<Listing> publicFilter(ListingSearchCriteria criteria)
    {
        return Specification.allOf(
                ListingSpecifications.hasStatus(ListingStatus.ACTIVE),
                ListingSpecifications.matches(criteria.q()),
                ListingSpecifications.brand(criteria.brand()),
                ListingSpecifications.model(criteria.model()),
                ListingSpecifications.yearBetween(criteria.yearFrom(), criteria.yearTo()),
                ListingSpecifications.priceBetween(criteria.priceFrom(), criteria.priceTo()),
                ListingSpecifications.mileageAtMost(criteria.mileageTo()),
                ListingSpecifications.city(criteria.city()),
                ListingSpecifications.fuelType(criteria.fuelType()),
                ListingSpecifications.transmission(criteria.transmission()),
                ListingSpecifications.bodyType(criteria.bodyType()));
    }
    // Свои объявления всех статусов. Продавец — из токена: чужой id сюда не передать
    @Transactional(readOnly = true)
    public Page<ListingDto> findMine(AppUserPrincipal actor, @Nullable ListingStatus status, Pageable pageable)
    {
        Page<Listing> page = status == null
                ? repository.findBySellerId(actor.getId(), pageable)
                : repository.findBySellerIdAndStatus(actor.getId(), status, pageable);
        return page.map(ListingMapper::toDto);
    }
    // Второй уровень защиты админки: URL-правило в SecurityConfig плюс аннотация здесь
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AdminListingDto> findAllForAdmin(@Nullable ListingStatus status, Pageable pageable)
    {
        Page<Listing> page = status == null
                ? repository.findAllWithSeller(pageable)
                : repository.findWithSellerByStatus(status, pageable);
        return page.map(ListingMapper::toAdminDto);
    }
    @Transactional(readOnly = true)
    public ListingStats stats()
    {
        long count = repository.countByStatus(ListingStatus.ACTIVE);
        double averageHp = repository.averageHorsePower(ListingStatus.ACTIVE);
        String strongestModel = repository.findFirstByStatusOrderByHorsePowerDesc(ListingStatus.ACTIVE).map(Listing::getModel).orElse("Каталог пуст");
        return new ListingStats(count,averageHp,strongestModel);
    }
    // Без @Transactional: соединение с базой не держится, пока ждём ответ ЦБ
    public ListingPriceDto priceIn(Long id,String currency)
    {
        Listing listing = repository.findByIdAndStatusIn(id, policy.publicStatuses())
                .orElseThrow(() -> notFound(id));
        if(listing.getPrice() == null)
        {
            throw new EntityNotFoundException("У объявления с id: " + id + " не указана цена");
        }
        BigDecimal rate = currencyClient.rateToRub(currency);
        BigDecimal convertedPrice = listing.getPrice().divide(rate,2, RoundingMode.HALF_UP);
        return new ListingPriceDto(listing.getId(),currency.toUpperCase(),rate,convertedPrice);
    }
    private Listing loadForChange(Long id, AppUserPrincipal actor, ListingAction action)
    {
        Listing listing = repository.findById(id).orElseThrow(() -> notFound(id));
        Long sellerId = listing.getSeller().getId();
        if(!policy.canView(listing.getStatus(), sellerId, actor))
        {
            throw notFound(id);
        }
        if(!policy.canPerform(action, sellerId, actor))
        {
            throw new AccessDeniedException("Недостаточно прав");
        }
        return listing;
    }
    // Hibernate поднимает version только при flush: без него клиент получил бы старую версию
    private ListingDto toDtoWithNewVersion(Listing listing)
    {
        return ListingMapper.toDto(repository.saveAndFlush(listing));
    }
    private EntityNotFoundException notFound(Long id)
    {
        return new EntityNotFoundException("Объявление с id: " + id + " не найдено");
    }
}
