package com.example.event;

import com.example.config.KafkaTopicsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Сервис не знает про Kafka: он публикует событие Spring, а сюда оно приходит и уходит в топик.
// Пока событие отправляется внутри транзакции, как было в гараже: если транзакция откатится, в Kafka уже ушло
@Component
public class ListingEventsRelay {
    private final KafkaTemplate<Long, ListingPublishedEvent> kafkaTemplate;
    private final Counter published;
    public ListingEventsRelay(KafkaTemplate<Long, ListingPublishedEvent> kafkaTemplate, MeterRegistry meterRegistry)
    {
        this.kafkaTemplate=kafkaTemplate;
        this.published=Counter.builder("listings.published")
                .description("Опубликовано объявлений")
                .register(meterRegistry);
    }
    @EventListener
    public void onListingPublished(ListingPublishedEvent event)
    {
        published.increment();
        kafkaTemplate.send(KafkaTopicsConfig.LISTING_PUBLISHED, event.listingId(), event);
    }
}
