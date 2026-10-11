package com.example;

import com.example.security.AdminInitializer;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AdminStartupTest extends IntegrationTest {
    // Два экземпляра стартуют одновременно на пустой базе. Оба не находят админа и создают его. Между поиском
    // и вставкой — BCrypt, десятки миллисекунд: с SELECT и потом INSERT второй падал бы на UNIQUE и не стартовал
    @Test
    void twoInstancesStartingTogetherCreateOneAdmin() throws Exception
    {
        String username = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try
        {
            Future<?> first = pool.submit(() -> { start.await(); new AdminInitializer(userRepository, passwordEncoder, username, "secret-password").run(null); return null; });
            Future<?> second = pool.submit(() -> { start.await(); new AdminInitializer(userRepository, passwordEncoder, username, "secret-password").run(null); return null; });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from users where username = ? and role = 'ADMIN'", Integer.class, username)).isEqualTo(1);
    }
}
