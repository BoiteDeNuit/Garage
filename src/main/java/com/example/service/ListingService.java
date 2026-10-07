package com.example.service;

import com.example.client.CurrencyClient;
import com.example.dto.ListingDto;
import com.example.dto.ListingMapper;
import com.example.dto.ListingPriceDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingStats;
import com.example.event.ListingPublishedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.model.Listing;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
public class ListingService {
    // Опубликованные и проданные видят все, черновики и архив только продавец и админ
    private static final Set<ListingStatus> PUBLIC_STATUSES = EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD);
    private final ListingRepository repository;
    private final AppUserRepository users;
    private final ListingReader reader;
    private final CurrencyClient currencyClient;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Counter listingsCreated;
    public ListingService(ListingRepository repository,
                          AppUserRepository users,
                          ListingReader reader,
                          CurrencyClient currencyClient,
                          ApplicationEventPublisher events,
                          MeterRegistry meterRegistry,
                          Clock clock)
    {
        this.repository=repository;
        this.users=users;
        this.reader=reader;
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
        if(!isVisible(card.status(), card.sellerId(), viewer))
        {
            throw notFound(id);
        }
        return card;
    }
    // Смена статуса. Порядок проверок: нет или не видно -> 404, видно, но не твоё -> 403, переход запрещён -> 409.
    // Кэш карточки сбрасывается после коммита: кэш-прокси снаружи транзакционного (порядок в CacheConfig)
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto publish(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, false);
        listing.publish(Instant.now(clock));
        events.publishEvent(ListingPublishedEvent.from(listing));
        return toDtoWithNewVersion(listing);
    }
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto markSold(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, false);
        listing.markSold(Instant.now(clock));
        return toDtoWithNewVersion(listing);
    }
    // Снять с публикации может и админ: это модерация, а не правка за продавца
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto archive(Long id, AppUserPrincipal actor)
    {
        Listing listing = loadForChange(id, actor, true);
        listing.archive(Instant.now(clock));
        return toDtoWithNewVersion(listing);
    }
    @Transactional(readOnly = true)
    public Page<ListingDto> findPublic(@Nullable String brand, Pageable pageable)
    {
        Page<Listing> page = brand == null
                ? repository.findByStatus(ListingStatus.ACTIVE, pageable)
                : repository.findByStatusAndBrandIgnoreCase(ListingStatus.ACTIVE, brand, pageable);
        return page.map(ListingMapper::toDto);
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
        Listing listing = repository.findByIdAndStatusIn(id, PUBLIC_STATUSES)
                .orElseThrow(() -> notFound(id));
        if(listing.getPrice() == null)
        {
            throw new EntityNotFoundException("У объявления с id: " + id + " не указана цена");
        }
        BigDecimal rate = currencyClient.rateToRub(currency);
        BigDecimal convertedPrice = listing.getPrice().divide(rate,2, RoundingMode.HALF_UP);
        return new ListingPriceDto(listing.getId(),currency.toUpperCase(),rate,convertedPrice);
    }
    private Listing loadForChange(Long id, AppUserPrincipal actor, boolean adminAllowed)
    {
        Listing listing = repository.findById(id).orElseThrow(() -> notFound(id));
        Long sellerId = listing.getSeller().getId();
        if(!isVisible(listing.getStatus(), sellerId, actor))
        {
            throw notFound(id);
        }
        boolean isSeller = sellerId.equals(actor.getId());
        boolean isModerator = adminAllowed && actor.getRole() == Role.ADMIN;
        if(!isSeller && !isModerator)
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
    // id сравниваются через equals: Long больше 127 из разных мест — разные объекты
    private boolean isVisible(ListingStatus status, Long sellerId, @Nullable AppUserPrincipal viewer)
    {
        if(PUBLIC_STATUSES.contains(status))
        {
            return true;
        }
        return viewer != null && (viewer.getId().equals(sellerId) || viewer.getRole() == Role.ADMIN);
    }
    private EntityNotFoundException notFound(Long id)
    {
        return new EntityNotFoundException("Объявление с id: " + id + " не найдено");
    }
}
