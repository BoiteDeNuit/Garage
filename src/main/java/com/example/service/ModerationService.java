package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.ListingMapper;
import com.example.dto.ModerationItemDto;
import com.example.event.ListingPublishedEvent;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.exception.ListingStateException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.model.ListingStatus;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Очередь модерации, захват на проверку и решение. Доступ — роль MODERATOR или ADMIN: правило URL в SecurityConfig
// и @PreAuthorize здесь, как у админки
@Service
public class ModerationService {
    private final ListingRepository repository;
    private final ListingAccessPolicy policy;
    private final ListingHistory history;
    private final CarModelCatalog catalog;
    private final ApplicationEventPublisher events;
    private final Duration claimTtl;
    private final Clock clock;
    public ModerationService(ListingRepository repository,
                             ListingAccessPolicy policy,
                             ListingHistory history,
                             CarModelCatalog catalog,
                             ApplicationEventPublisher events,
                             @Value("${moderation.claim-ttl:30m}") Duration claimTtl,
                             Clock clock)
    {
        this.repository=repository;
        this.policy=policy;
        this.history=history;
        this.catalog=catalog;
        this.events=events;
        this.claimTtl=claimTtl;
        this.clock=clock;
    }
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public Page<ModerationItemDto> queue(Pageable pageable)
    {
        return repository.findByStatusOrderBySubmittedAtAscIdAsc(ListingStatus.PENDING_REVIEW, pageable).map(this::toItem);
    }
    // Захват — условный UPDATE (ListingRepository.claim). Если он не изменил строку, причина ищется отдельно,
    // чтобы ответить точно: нет объявления — 404, своё — 403, не на проверке или взял другой — 409
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ModerationItemDto claim(Long id, AppUserPrincipal moderator)
    {
        Instant now = Instant.now(clock);
        int claimed = repository.claim(id, moderator.getId(), ListingStatus.PENDING_REVIEW, now, now.minus(claimTtl));
        Listing listing = repository.findById(id).orElseThrow(() -> EntityNotFoundException.listing(id));
        if(claimed == 1)
        {
            return toItem(listing);
        }
        if(!policy.canPerform(ListingAction.MODERATE, listing.getSeller().getId(), moderator))
        {
            throw new AccessDeniedException("Своё объявление проверяет другой модератор");
        }
        if(listing.getStatus() != ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.notUnderReview();
        }
        throw ListingStateException.claimedByOther();
    }
    // Одобрить может только тот, кто взял объявление: двое не примут разных решений по одному объявлению.
    // Объявление уходит в ленту, подсказки и Kafka так же, как при публикации без модерации
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto approve(Long id, AppUserPrincipal moderator)
    {
        Listing listing = claimedListing(id, moderator);
        listing.approve(Instant.now(clock));
        // flush до журнала: если продавец успел удалить объявление, проверка версии даст 409, а не 500 на внешнем ключе
        ListingDto dto = ListingMapper.toDto(repository.saveAndFlush(listing));
        history.record(listing, ListingStatus.PENDING_REVIEW, moderator.getId(), null);
        catalog.remember(listing.getBrand(), listing.getModel());
        events.publishEvent(ListingPublishedEvent.from(listing));
        return dto;
    }
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Transactional
    @CacheEvict(cacheNames = "listings", key = "#id")
    public ListingDto reject(Long id, String reason, AppUserPrincipal moderator)
    {
        // @Size в запросе считает и пробелы по краям: "  x  " прошло бы. Длину проверяем после обрезки
        String trimmed = reason.trim();
        if(trimmed.length() < 5 || trimmed.length() > 500)
        {
            throw new InvalidRequestException("Причина отказа от 5 до 500 символов");
        }
        Listing listing = claimedListing(id, moderator);
        listing.reject(trimmed, Instant.now(clock));
        ListingDto dto = ListingMapper.toDto(repository.saveAndFlush(listing));
        history.record(listing, ListingStatus.PENDING_REVIEW, moderator.getId(), trimmed);
        return dto;
    }
    private Listing claimedListing(Long id, AppUserPrincipal moderator)
    {
        Listing listing = repository.findById(id).orElseThrow(() -> EntityNotFoundException.listing(id));
        if(!policy.canPerform(ListingAction.MODERATE, listing.getSeller().getId(), moderator))
        {
            throw new AccessDeniedException("Своё объявление проверяет другой модератор");
        }
        if(listing.getStatus() != ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.notUnderReview();
        }
        if(!listing.isClaimedBy(moderator.getId()))
        {
            throw ListingStateException.notClaimed();
        }
        return listing;
    }
    private ModerationItemDto toItem(Listing listing)
    {
        return new ModerationItemDto(ListingMapper.toDto(listing), listing.getSubmittedAt(), listing.getModeratorId(), listing.getClaimedAt());
    }
}
