package com.example.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

// Экземпляров приложения может быть несколько, а задача по расписанию должна пройти один раз.
// ShedLock перед запуском обновляет строку задачи в shedlock, если её блокировка истекла. Кто обновил — выполняет,
// остальные пропускают. Время блокировки берётся из базы (usingDbTime): часы экземпляров могут расходиться.
// lockAtMostFor — страховка: упал экземпляр посреди задачи — через это время её возьмёт другой
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource)
    {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
    // Расписание отдельно от блокировок: в тестах оно выключено (scheduling.enabled=false), задачи вызываются
    // напрямую, а блокировка при этом работает, и её можно проверить
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Enabled {
    }
}
