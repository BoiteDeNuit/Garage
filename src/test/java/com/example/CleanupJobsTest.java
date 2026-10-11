package com.example;

import com.example.job.RefreshTokenCleanup;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.RefreshTokenRepository;
import com.example.support.IntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupJobsTest extends IntegrationTest {
    @Autowired
    private RefreshTokenCleanup refreshTokenCleanup;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;

    // Блокировки задач общие на все тесты: оставленная одним тестом минута lockAtLeastFor пропустила бы запуск в другом.
    // Строку не удаляем: ShedLock помнит, что уже вставил её, и дальше только обновляет. Удалённую он не вернёт,
    // и задача больше не запустится
    @BeforeEach
    @AfterEach
    void releaseLocks()
    {
        jdbcTemplate.update("update shedlock set lock_until = now()");
    }

    // Неделю после срока токен ещё хранится, потом удаляется. Живые не трогаются
    @Test
    void removesOnlyLongExpiredRefreshTokens()
    {
        AppUser user = createUser(Role.USER);
        insertToken(user, "8 days");
        Long recent = insertToken(user, "1 day");
        Long live = insertToken(user, "-14 days");

        refreshTokenCleanup.run();

        assertThat(tokensOf(user)).containsExactly(recent, live);
    }

    // Другой экземпляр держит блокировку: этот запуск пропускается целиком. Освободил — следующий запуск проходит
    @Test
    void skipsRunWhileAnotherInstanceHoldsLock()
    {
        AppUser user = createUser(Role.USER);
        Long old = insertToken(user, "8 days");
        jdbcTemplate.update("insert into shedlock (name, lock_until, locked_at, locked_by) values ('refreshTokenCleanup', now() + interval '10 minutes', now(), 'app2') "
                + "on conflict (name) do update set lock_until = excluded.lock_until, locked_at = excluded.locked_at, locked_by = excluded.locked_by");

        refreshTokenCleanup.run();
        assertThat(tokensOf(user)).containsExactly(old);

        releaseLocks();
        refreshTokenCleanup.run();
        assertThat(tokensOf(user)).isEmpty();
    }

    // Задача прошла за миллисекунды, но блокировка держится минуту (lockAtLeastFor). Иначе второй экземпляр,
    // чей таймер сработал на пару секунд позже, нашёл бы её свободной и прошёл бы второй раз
    @Test
    void lockOutlivesFastRun()
    {
        AppUser user = createUser(Role.USER);
        refreshTokenCleanup.run();
        Long old = insertToken(user, "8 days");

        refreshTokenCleanup.run();

        assertThat(tokensOf(user)).containsExactly(old);
        assertThat(jdbcTemplate.queryForObject("select lock_until > now() + interval '50 seconds' from shedlock where name = 'refreshTokenCleanup'", Boolean.class)).isTrue();
    }

    // Пачки по две: пять истёкших уходят за три транзакции, ни одна не остаётся
    @Test
    void deletesInBatchesUntilNothingLeft()
    {
        AppUser user = createUser(Role.USER);
        for(int i = 0; i < 5; i++)
        {
            insertToken(user, "8 days");
        }
        RefreshTokenCleanup smallBatches = new RefreshTokenCleanup(refreshTokenRepository, transactionManager, clock,
                Duration.ofDays(7), 2, new SimpleMeterRegistry());

        smallBatches.run();

        assertThat(tokensOf(user)).isEmpty();
    }

    // expiredAgo — интервал Postgres: "8 days" — истёк восемь дней назад, "-14 days" — истечёт через две недели
    private Long insertToken(AppUser user, String expiredAgo)
    {
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return jdbcTemplate.queryForObject(
                "insert into refresh_tokens (user_id, family_id, token_hash, created_at, expires_at) "
                        + "values (?, gen_random_uuid(), ?, now() - interval '30 days', now() - ?::interval) returning id",
                Long.class, user.getId(), hash, expiredAgo);
    }

    private List<Long> tokensOf(AppUser user)
    {
        return jdbcTemplate.queryForList("select id from refresh_tokens where user_id = ? order by id", Long.class, user.getId());
    }
}
