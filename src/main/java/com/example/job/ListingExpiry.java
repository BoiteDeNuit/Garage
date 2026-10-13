package com.example.job;

import com.example.event.ListingCardsChanged;
import com.example.repository.ListingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Опубликованное висит в ленте срок (60 дней), потом уходит в архив: проданную машину продавцы часто не отмечают.
// Продавцу приходит уведомление, из архива он публикует снова, и срок идёт заново.
// Пачки по своей транзакции: карточки пачки сбрасываются из кэша после её коммита (ListingCardsEvictor).
// За запуск не больше maxPerRun: на базе, где срок вводится впервые, первый запуск иначе снял бы разом всё старое
// и разослал столько же уведомлений. Остальное заберут следующие запуски, по часу на каждые 10 000
@Component
public class ListingExpiry {
    private static final Logger log = LoggerFactory.getLogger(ListingExpiry.class);
    static final String REASON = "Срок публикации истёк";
    private final ListingRepository listings;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration period;
    private final int batchSize;
    private final int maxPerRun;
    private final Counter expired;
    public ListingExpiry(ListingRepository listings,
                         ApplicationEventPublisher events,
                         PlatformTransactionManager transactionManager,
                         Clock clock,
                         @Value("${listings.publication-period:60d}") Duration period,
                         @Value("${cleanup.batch-size:1000}") int batchSize,
                         @Value("${listings.expiry-max-per-run:10000}") int maxPerRun,
                         MeterRegistry meterRegistry)
    {
        this.listings=listings;
        this.events=events;
        this.transaction=new TransactionTemplate(transactionManager);
        this.clock=clock;
        this.period=period;
        this.batchSize=batchSize;
        this.maxPerRun=maxPerRun;
        this.expired=Counter.builder("listings.expired")
                .description("Объявлений, снятых в архив по сроку публикации")
                .register(meterRegistry);
    }
    @Scheduled(cron = "${listings.expiry-cron:0 10 * * * *}")
    @SchedulerLock(name = "listingExpiry", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run()
    {
        Instant now = Instant.now(clock);
        Instant before = now.minus(period);
        int total = 0;
        boolean more = true;
        while(more && total < maxPerRun)
        {
            int limit = Math.min(batchSize, maxPerRun - total);
            List<Long> ids = transaction.execute(status -> {
                List<Long> archived = listings.expireBatch(before, now, REASON, limit);
                events.publishEvent(new ListingCardsChanged(archived));
                return archived;
            });
            int batch = ids == null ? 0 : ids.size();
            total += batch;
            more = batch == limit;
        }
        expired.increment(total);
        log.info("Снято по сроку публикации: {}, опубликованных раньше {}", total, before);
    }
}
