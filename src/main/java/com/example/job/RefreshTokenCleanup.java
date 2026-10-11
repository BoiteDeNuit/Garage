package com.example.job;

import com.example.repository.RefreshTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Истёкшие refresh-токены ни на что не годятся, но копятся: каждый вход и каждый обмен — строка.
// Неделю после срока они ещё лежат: по цепочке replaced_by видно, кто и когда обменивал, если надо разобрать кражу.
// Удаление пачками, каждая в своей транзакции: одно большое DELETE держало бы блокировки и раздувало WAL разом
@Component
public class RefreshTokenCleanup {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);
    private final RefreshTokenRepository tokens;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration keepAfterExpiry;
    private final int batchSize;
    private final Counter deleted;
    public RefreshTokenCleanup(RefreshTokenRepository tokens,
                               PlatformTransactionManager transactionManager,
                               Clock clock,
                               @Value("${cleanup.refresh-tokens.keep-after-expiry:7d}") Duration keepAfterExpiry,
                               @Value("${cleanup.batch-size:1000}") int batchSize,
                               MeterRegistry meterRegistry)
    {
        this.tokens=tokens;
        this.transaction=new TransactionTemplate(transactionManager);
        this.clock=clock;
        this.keepAfterExpiry=keepAfterExpiry;
        this.batchSize=batchSize;
        this.deleted=Counter.builder("cleanup.refresh.tokens.deleted")
                .description("Удалено истёкших refresh-токенов")
                .register(meterRegistry);
    }
    // void: если блокировку держит другой экземпляр, ShedLock метод не вызывает и вернул бы null вместо числа
    @Scheduled(cron = "${cleanup.refresh-tokens.cron:0 30 3 * * *}")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run()
    {
        Instant before = Instant.now(clock).minus(keepAfterExpiry);
        int total = 0;
        int batch;
        do
        {
            Integer removed = transaction.execute(status -> tokens.deleteExpiredBatch(before, batchSize));
            batch = removed == null ? 0 : removed;
            total += batch;
        }
        while(batch == batchSize);
        deleted.increment(total);
        log.info("Уборка refresh-токенов: удалено {}, истёкших раньше {}", total, before);
    }
}
