package com.example.ratelimit;

import com.example.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Пределы против спама: сколько объявлений пользователь создаёт за час и публикует за сутки, сколько аккаунтов
// регистрируют с одного адреса за час. Счётчики в Redis, общие для всех экземпляров. Окно фиксированное:
// час или сутки по часам сервера, в ответе 429 — сколько секунд до следующего окна.
// Считаются попытки, а не успехи: отказ по другой причине тоже тратит попытку, иначе перебор шёл бы бесплатно
@Component
public class SpamGuard {
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private final RateLimiter limiter;
    private final Clock clock;
    private final int listingsPerHour;
    private final int publicationsPerDay;
    private final int registrationsPerHour;
    public SpamGuard(RateLimiter limiter,
                     Clock clock,
                     @Value("${rate-limit.listings.per-hour:20}") int listingsPerHour,
                     @Value("${rate-limit.publications.per-day:10}") int publicationsPerDay,
                     @Value("${rate-limit.registrations.per-hour:10}") int registrationsPerHour)
    {
        this.limiter=limiter;
        this.clock=clock;
        this.listingsPerHour=listingsPerHour;
        this.publicationsPerDay=publicationsPerDay;
        this.registrationsPerHour=registrationsPerHour;
    }
    public void listingCreated(Long userId)
    {
        check("rate:listings:" + userId, listingsPerHour, HOUR, "Не больше " + listingsPerHour + " новых объявлений в час");
    }
    public void listingPublished(Long userId)
    {
        check("rate:publications:" + userId, publicationsPerDay, DAY, "Не больше " + publicationsPerDay + " публикаций в сутки");
    }
    // Адрес клиента, а не прокси: за nginx его даёт X-Forwarded-For (server.forward-headers-strategy)
    public void registration(String address)
    {
        check("rate:registrations:" + address, registrationsPerHour, HOUR, "Слишком много регистраций с вашего адреса");
    }
    private void check(String scope, int limit, Duration window, String message)
    {
        long now = Instant.now(clock).getEpochSecond();
        long bucket = now / window.toSeconds();
        if(!limiter.allow(scope + ":" + bucket, limit, window))
        {
            throw new TooManyRequestsException(message, (bucket + 1) * window.toSeconds() - now);
        }
    }
}
