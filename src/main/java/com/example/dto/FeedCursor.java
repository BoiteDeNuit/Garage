package com.example.dto;

import com.example.exception.InvalidRequestException;
import com.example.model.Listing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

// Позиция в ленте: последняя отданная строка. Лента идёт по (published_at desc, id desc),
// следующая страница начинается строго после этой пары. id нужен, когда у нескольких
// объявлений одинаковое время публикации.
// Наружу уходит непрозрачная строка: клиент передаёт её обратно и не собирает сам
public record FeedCursor(Instant publishedAt, Long id) {
    private static final String VERSION = "v1";

    public static FeedCursor after(Listing listing)
    {
        return new FeedCursor(listing.getPublishedAt(), listing.getId());
    }

    // Время в микросекундах: столько хранит timestamptz, и сравнение с базой точное
    public String encode()
    {
        long micros = Math.addExact(Math.multiplyExact(publishedAt.getEpochSecond(), 1_000_000L), publishedAt.getNano() / 1_000);
        String raw = VERSION + ":" + micros + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String cursor)
    {
        try
        {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if(parts.length != 3 || !VERSION.equals(parts[0]))
            {
                throw invalid();
            }
            long micros = Long.parseLong(parts[1]);
            long id = Long.parseLong(parts[2]);
            if(micros < 0 || id <= 0)
            {
                throw invalid();
            }
            return new FeedCursor(Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L), id);
        }
        catch (IllegalArgumentException e)
        {
            // NumberFormatException — наследник IllegalArgumentException, его ловит тот же блок
            throw invalid();
        }
    }

    private static InvalidRequestException invalid()
    {
        return new InvalidRequestException("Неверный курсор");
    }
}
