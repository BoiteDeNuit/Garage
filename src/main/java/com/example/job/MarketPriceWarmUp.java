package com.example.job;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Отдельный бин, а не метод в MarketPriceRefresh: вызов изнутри того же класса прошёл бы мимо прокси ShedLock,
// и два экземпляра посчитали бы представление оба
@Component
public class MarketPriceWarmUp {
    private final MarketPriceRefresh refresh;
    public MarketPriceWarmUp(MarketPriceRefresh refresh)
    {
        this.refresh=refresh;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady()
    {
        refresh.populateIfEmpty();
    }
}
