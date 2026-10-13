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
import com.example.event.ListingPriceDroppedEvent;
import com.example.event.ListingPublishedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.model.ListingStateException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.model.ListingStatus;
import com.example.ratelimit.SpamGuard;
import com.example.repository.AppUserRepository;
import com.example.repository.ListingPhotoRepository;
import com.example.repository.ListingRepository;
import com.example.repository.ListingSpecifications;
import com.example.security.AppUserPrincipal;
import com.example.storage.PhotoFilesRemoved;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private static final List<ListingStatus> PUBLISHED_OR_PENDING = List.of(ListingStatus.ACTIVE, ListingStatus.PENDING_REVIEW);
    private static final Sort FEED_ORDER = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private final ListingRepository repository;
    private final AppUserRepository users;
    private final ListingReader reader;
    private final ListingLoader loader;
    private final ListingPhotoRepository photos;
    private final ListingAccessPolicy policy;
    private final CarModelCatalog catalog;
    private final ListingHistory history;
    private final ModerationSettings moderation;
    private final CurrencyClient currencyClient;
    private final ApplicationEventPublisher events;
    private final SpamGuard spamGuard;
    private final Clock clock;
    private final Counter listingsCreated;
    public ListingService(ListingRepository repository,
                          AppUserRepository users,
                          ListingReader reader,
                          ListingLoader loader,
                          ListingPhotoRepository photos,
                          ListingAccessPolicy policy,
                          CarModelCatalog catalog,
                          ListingHistory history,
                          ModerationSettings moderation,
                          CurrencyClient currencyClient,
                          ApplicationEventPublisher events,
                          SpamGuard spamGuard,
                          MeterRegistry meterRegistry,
                          Clock clock)
    {
        this.repository=repository;
        this.users=users;
        this.reader=reader;
        this.loader=loader;
        this.photos=photos;
        this.policy=policy;
        this.catalog=catalog;
        this.history=history;
        this.moderation=moderation;
        this.currencyClient=currencyClient;
        this.events=events;
        this.spamGuard=spamGuard;
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
        spamGuard.listingCreated(seller.getId());
        Listing listing = Listing.draft(users.getReferenceById(seller.getId()), ListingMapper.toDetails(request), Instant.now(clock));
        Listing saved = repository.save(listing);
        history.record(saved, null, seller.getId(), null);
        listingsCreated.increment();
        return ListingMapper.toDto(saved);
    }
    // Чужой черновик отдаёт 404, а не 403: иначе по коду ответа видно, что объявление существует
    public ListingDto get(Long id, @Nullable AppUserPrincipal viewer)
    {
        ListingDto card = reader.findCard(id);
        if(!policy.canView(card.status(), card.sellerId(), viewer))
        {
            throw EntityNotFoundException.listing(id);
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
        Listing listing = loader.forChange(id, actor, ListingAction.EDIT);
        if(!listing.getVersion().equals(request.version()))
        {
            throw ListingStateException.staleVersion(listing.getVersion());
        }
        BigDecimal oldPrice = listing.getPrice();
        listing.updateDetails(ListingMapper.toDetails(request), Instant.now(clock));
        // Опубликованное видят покупатели: новая марка или модель сразу попадает в подсказки
        if(listing.getStatus() == ListingStatus.ACTIVE)
        {
            catalog.remember(listing.getBrand(), listing.getModel());
            // compareTo, а не equals: 4500000 и 4500000.00 — одна цена, а equals сравнил бы и масштаб.
            // Событие уйдёт в Kafka после коммита (ListingEventsRelay)
            if(listing.getPrice().compareTo(oldPrice) < 0)
            {
                events.publishEvent(new ListingPriceDroppedEvent(listing.getId(), listing.getSeller().getId(), oldPrice, listing.getPrice(), listing.getUpdatedAt()));
            }
        }
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto publish(Long id, AppUserPrincipal actor)
    {
        Listing listing = loader.forChange(id, actor, ListingAction.PUBLISH);
        spamGuard.listingPublished(actor.getId());
        rejectDuplicate(listing);
        ListingStatus from = listing.getStatus();
        listing.publish(Instant.now(clock), moderation.required());
        // С модерацией в ленту, подсказки и Kafka объявление попадёт после одобрения, не сейчас
        if(listing.getStatus() == ListingStatus.ACTIVE)
        {
            catalog.remember(listing.getBrand(), listing.getModel());
            events.publishEvent(ListingPublishedEvent.from(listing));
        }
        return toDtoWithHistory(listing, from, actor);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto markSold(Long id, AppUserPrincipal actor)
    {
        Listing listing = loader.forChange(id, actor, ListingAction.MARK_SOLD);
        ListingStatus from = listing.getStatus();
        listing.markSold(Instant.now(clock));
        return toDtoWithHistory(listing, from, actor);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto archive(Long id, AppUserPrincipal actor)
    {
        Listing listing = loader.forChange(id, actor, ListingAction.ARCHIVE);
        ListingStatus from = listing.getStatus();
        listing.archive(Instant.now(clock));
        return toDtoWithHistory(listing, from, actor);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public void delete(Long id, AppUserPrincipal actor)
    {
        Listing listing = loader.forChange(id, actor, ListingAction.DELETE);
        listing.checkDeletable();
        // Строки фото база удалит каскадом, а файлы — PhotoFilesCleaner после коммита
        events.publishEvent(new PhotoFilesRemoved(photos.findObjectKeys(id)));
        repository.delete(listing);
    }
    // Поиск по словам без сортировки от клиента идёт по релевантности: ListingSort.forTextSearch сортировку не ставит
    @Transactional(readOnly = true)
    public Page<ListingDto> findPublic(ListingSearchCriteria criteria, Pageable pageable)
    {
        Specification<Listing> filter = ListingSpecifications.publicFeed(criteria);
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
        Specification<Listing> filter = ListingSpecifications.publicFeed(criteria);
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
                .orElseThrow(() -> EntityNotFoundException.listing(id));
        if(listing.getPrice() == null)
        {
            throw new EntityNotFoundException("У объявления с id: " + id + " не указана цена");
        }
        BigDecimal rate = currencyClient.rateToRub(currency);
        BigDecimal convertedPrice = listing.getPrice().divide(rate,2, RoundingMode.HALF_UP);
        return new ListingPriceDto(listing.getId(),currency.toUpperCase(),rate,convertedPrice);
    }
    // Та же машина за ту же цену в том же городе у того же продавца уже в ленте или на проверке — это повтор,
    // а не второе объявление: спам поднимает старое объявление в ленте новой публикацией.
    // Проверка не атомарна: две одновременные публикации двух копий пройдут обе. Это защита от спама, а не инвариант
    private void rejectDuplicate(Listing listing)
    {
        List<Long> duplicates = repository.findDuplicates(listing.getSeller().getId(), listing.getId(), PUBLISHED_OR_PENDING,
                listing.getBrand(), listing.getModel(), listing.getYear(), listing.getMileageKm(), listing.getPrice(), listing.getCity(),
                PageRequest.of(0, 1));
        if(!duplicates.isEmpty())
        {
            throw ListingStateException.duplicate(duplicates.getFirst());
        }
    }
    // Hibernate поднимает version только при flush: без него клиент получил бы старую версию
    private ListingDto toDtoWithNewVersion(Listing listing)
    {
        return ListingMapper.toDto(repository.saveAndFlush(listing));
    }
    // Сначала flush: проверка версии отсеет гонку (объявление удалили или изменили — 409), и только потом журнал.
    // В обратном порядке вставка в журнал упала бы на внешнем ключе удалённого объявления — 500
    private ListingDto toDtoWithHistory(Listing listing, ListingStatus from, AppUserPrincipal actor)
    {
        ListingDto dto = toDtoWithNewVersion(listing);
        history.record(listing, from, actor.getId(), null);
        return dto;
    }
}
