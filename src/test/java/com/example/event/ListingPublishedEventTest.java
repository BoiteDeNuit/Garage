package com.example.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ListingPublishedEventTest {
    private static final Instant AT = Instant.parse("2026-10-10T12:00:00Z");

    // Повторная доставка того же сообщения — тот же id, новая публикация из архива — новый.
    // Цена и марка в id не входят: правка между доставками не делает событие другим
    @Test
    void eventIdDependsOnListingAndPublicationMoment()
    {
        ListingPublishedEvent event = new ListingPublishedEvent(5L, 7L, "Toyota", "Supra", new BigDecimal("4500000"), AT);

        assertThat(event.eventId()).isEqualTo(new ListingPublishedEvent(5L, 7L, "TOYOTA", "Camry", BigDecimal.ONE, AT).eventId());
        assertThat(event.eventId()).isNotEqualTo(new ListingPublishedEvent(5L, 7L, "Toyota", "Supra", new BigDecimal("4500000"), AT.plusSeconds(60)).eventId());
        assertThat(event.eventId()).isNotEqualTo(new ListingPublishedEvent(6L, 7L, "Toyota", "Supra", new BigDecimal("4500000"), AT).eventId());
    }
}
