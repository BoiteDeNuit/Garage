package com.example.listener;

import com.example.config.KafkaTopicsConfig;
import com.example.event.ListingPriceDroppedEvent;
import com.example.event.ListingPublishedEvent;
import com.example.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Новое объявление сопоставляется с сохранёнными поисками, снижение цены — с избранным
@Component
public class NotificationsListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationsListener.class);
    private final NotificationService notifications;
    public NotificationsListener(NotificationService notifications) { this.notifications=notifications; }
    @KafkaListener(topics = KafkaTopicsConfig.LISTING_PUBLISHED,groupId = "notifications")
    public void onListingPublished(ListingPublishedEvent event)
    {
        int created = notifications.notifyMatchingSearches(event);
        log.info("Опубликовано объявление {} {} (id={}), уведомлений: {}",event.brand(),event.model(),event.listingId(),created);
    }
    @KafkaListener(topics = KafkaTopicsConfig.LISTING_PRICE_DROPPED,groupId = "notifications")
    public void onPriceDropped(ListingPriceDroppedEvent event)
    {
        int created = notifications.notifyFavorites(event);
        log.info("Цена объявления {} снизилась с {} до {}, уведомлений: {}",event.listingId(),event.oldPrice(),event.newPrice(),created);
    }
}
