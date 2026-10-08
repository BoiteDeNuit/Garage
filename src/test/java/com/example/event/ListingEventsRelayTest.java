package com.example.event;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingEventsRelayTest {
    @Mock
    private KafkaTemplate<Long, ListingPublishedEvent> kafkaTemplate;
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private ListingEventsRelay relay;
    private final ListingPublishedEvent event = new ListingPublishedEvent(1L, 7L, "Toyota", "Supra", new BigDecimal("4500000.00"), Instant.parse("2026-10-08T12:00:00Z"));

    @BeforeEach
    void setUp()
    {
        relay = new ListingEventsRelay(kafkaTemplate, registry);
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulSendCountsPublished()
    {
        when(kafkaTemplate.send(anyString(), anyLong(), any())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.onListingPublished(event);

        assertThat(counter("listings.published")).isEqualTo(1.0);
        assertThat(counter("listings.events.failed")).isZero();
    }

    @Test
    void failedFutureIsCountedAndNotThrown()
    {
        when(kafkaTemplate.send(anyString(), anyLong(), any())).thenReturn(CompletableFuture.failedFuture(new KafkaException("брокер недоступен")));

        relay.onListingPublished(event);

        assertThat(counter("listings.events.failed")).isEqualTo(1.0);
    }

    // При лежащей Kafka send может бросить сразу, например по таймауту метаданных
    @Test
    void synchronousExceptionIsCountedAndNotThrown()
    {
        when(kafkaTemplate.send(anyString(), anyLong(), any())).thenThrow(new KafkaException("таймаут метаданных"));

        relay.onListingPublished(event);

        assertThat(counter("listings.events.failed")).isEqualTo(1.0);
    }

    // Отправка не ждёт подтверждения: поймает и .get(), и .get(timeout)
    @Test
    void doesNotWaitForBroker()
    {
        CompletableFuture<SendResult<Long, ListingPublishedEvent>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyLong(), any())).thenReturn(pending);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> relay.onListingPublished(event));
        assertThat(counter("listings.events.failed")).isZero();

        pending.completeExceptionally(new KafkaException("брокер не ответил"));
        assertThat(counter("listings.events.failed")).isEqualTo(1.0);
    }

    private double counter(String name)
    {
        return registry.get(name).counter().count();
    }
}
