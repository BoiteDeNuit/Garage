package com.example.listener;

import com.example.config.KafkaTopicsConfig;
import com.example.event.ListingPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsListener {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);
    @KafkaListener(topics = KafkaTopicsConfig.LISTING_PUBLISHED,groupId = "analytics")
    public void onListingPublished(ListingPublishedEvent event)
    {
        log.info("Аналитика: +1 объявление марки {} в {}",event.brand(),event.publishedAt());
    }
}

