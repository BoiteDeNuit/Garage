package com.example.event;

import com.example.config.KafkaTopicsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Сервис не знает про Kafka: он публикует событие Spring, а сюда оно приходит и уходит в топик.
// AFTER_COMMIT: откаченная публикация ничего не отправляет. fallbackExecution: событие вне транзакции тоже уходит.
// Это at-most-once: если приложение упадёт между коммитом и отправкой, событие потеряется.
// Надёжный вариант — transactional outbox: событие пишется в таблицу в той же транзакции, а отправляет его отдельный процесс
@Component
public class ListingEventsRelay {
    private static final Logger log = LoggerFactory.getLogger(ListingEventsRelay.class);
    private final KafkaTemplate<Long, Object> kafkaTemplate;
    private final Counter published;
    private final Counter failed;
    public ListingEventsRelay(KafkaTemplate<Long, Object> kafkaTemplate, MeterRegistry meterRegistry)
    {
        this.kafkaTemplate=kafkaTemplate;
        this.published=Counter.builder("listings.published")
                .description("Опубликовано объявлений")
                .register(meterRegistry);
        this.failed=Counter.builder("listings.events.failed")
                .description("Событий об объявлениях, которые не ушли в Kafka")
                .register(meterRegistry);
    }
    // Транзакция уже закоммичена: исключение отсюда ничего не откатит и до клиента не дойдёт, поэтому ловим и считаем.
    // Подтверждение от Kafka не ждём: результат приходит в whenComplete в другом потоке
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingPublished(ListingPublishedEvent event)
    {
        published.increment();
        send(KafkaTopicsConfig.LISTING_PUBLISHED, event.listingId(), event);
    }
    // Снижение цены уходит только из закоммиченной правки: откатилась правка — покупатели ничего не узнают
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceDropped(ListingPriceDroppedEvent event)
    {
        send(KafkaTopicsConfig.LISTING_PRICE_DROPPED, event.listingId(), event);
    }
    private void send(String topic, Long listingId, Object event)
    {
        try
        {
            kafkaTemplate.send(topic, listingId, event)
                    .whenComplete((result, error) -> {
                        if (error != null)
                        {
                            failed.increment();
                            log.warn("Событие {} об объявлении {} не ушло в Kafka: {}", topic, listingId, error.getMessage());
                        }
                    });
        }
        catch (RuntimeException e)
        {
            failed.increment();
            log.warn("Событие {} об объявлении {} не ушло в Kafka: {}", topic, listingId, e.getMessage());
        }
    }
}
