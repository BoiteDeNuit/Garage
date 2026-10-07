package com.example.listener;

import com.example.config.KafkaTopicsConfig;
import com.example.event.ListingPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationsListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationsListener.class);
    @KafkaListener(topics = KafkaTopicsConfig.LISTING_PUBLISHED,groupId = "notifications")
    public void onListingPublished(ListingPublishedEvent event)
    {
        log.info("Уведомление: опубликовано объявление {} {} (id={})",event.brand(),event.model(),event.listingId());
    }
}
