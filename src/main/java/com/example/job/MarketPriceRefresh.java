package com.example.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Пересчёт рыночных цен (V33). CONCURRENTLY: карточки читают старые цифры, пока считаются новые,
// без него пересчёт закрыл бы представление от чтения на всё время расчёта.
// CONCURRENTLY не работает на ещё не посчитанном представлении: первый раз после миграции — обычный REFRESH
@Component
public class MarketPriceRefresh {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceRefresh.class);
    private final JdbcTemplate jdbc;
    private final Timer duration;
    public MarketPriceRefresh(JdbcTemplate jdbc, MeterRegistry meterRegistry)
    {
        this.jdbc=jdbc;
        this.duration=Timer.builder("market.prices.refresh")
                .description("Время пересчёта рыночных цен")
                .register(meterRegistry);
    }
    @Scheduled(cron = "${market-prices.refresh-cron:0 40 * * * *}")
    @SchedulerLock(name = "marketPriceRefresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run()
    {
        refresh(populated());
    }
    // Первый расчёт сразу после старта, а не в ближайшие :40: на свежей базе иначе час без оценок.
    // Та же блокировка, что у run: два экземпляра стартуют вместе, считает один. Уже посчитано — ничего не делает
    @SchedulerLock(name = "marketPriceRefresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void populateIfEmpty()
    {
        if(!populated())
        {
            refresh(false);
        }
    }
    private boolean populated()
    {
        return Boolean.TRUE.equals(jdbc.queryForObject("select relispopulated from pg_class where relname = 'market_prices'", Boolean.class));
    }
    private void refresh(boolean populated)
    {
        String sql = populated
                ? "refresh materialized view concurrently market_prices"
                : "refresh materialized view market_prices";
        long started = System.nanoTime();
        jdbc.execute(sql);
        long took = System.nanoTime() - started;
        duration.record(took, TimeUnit.NANOSECONDS);
        log.info("Рыночные цены пересчитаны за {} мс", TimeUnit.NANOSECONDS.toMillis(took));
    }
}
