package com.example.service;

import com.example.event.ListingCardsChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Сброс карточек после коммита, по той же причине, что и @CacheEvict (CacheConfig): сброс до коммита
// дал бы GET между сбросом и коммитом положить в кэш старую строку
@Component
public class ListingCardsEvictor {
    private static final Logger log = LoggerFactory.getLogger(ListingCardsEvictor.class);
    private final CacheManager cacheManager;
    public ListingCardsEvictor(CacheManager cacheManager)
    {
        this.cacheManager=cacheManager;
    }
    // Исключение из AFTER_COMMIT ушло бы вызывающему, хотя транзакция уже закоммичена. Redis недоступен —
    // карточки доживут до TTL (10 минут), это видно в логе
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingCardsChanged(ListingCardsChanged event)
    {
        Cache cards = cacheManager.getCache("listings");
        if(cards == null || event.listingIds().isEmpty())
        {
            return;
        }
        try
        {
            for(Long id : event.listingIds())
            {
                cards.evict(id);
            }
        }
        catch (RuntimeException e)
        {
            log.warn("Карточки {} не убраны из кэша, устаревшие данные до конца TTL. Причина: {}", event.listingIds(), e.getMessage());
        }
    }
}
