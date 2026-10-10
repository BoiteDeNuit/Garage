package com.example.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    public static final String LISTING_PUBLISHED = "listing-published";
    public static final String LISTING_PRICE_DROPPED = "listing-price-dropped";
    @Bean
    public NewTopic listingPublishedTopic() {
        return TopicBuilder.name(LISTING_PUBLISHED)
                .partitions(3)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic listingPriceDroppedTopic() {
        return TopicBuilder.name(LISTING_PRICE_DROPPED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
