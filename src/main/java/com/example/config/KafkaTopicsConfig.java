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
    // DLT пишет сообщение в ту же партицию, что у исходного, поэтому партиций не меньше
    @Bean
    public NewTopic listingPublishedDeadLetterTopic() {
        return TopicBuilder.name(LISTING_PUBLISHED + "-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic listingPriceDroppedDeadLetterTopic() {
        return TopicBuilder.name(LISTING_PRICE_DROPPED + "-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
