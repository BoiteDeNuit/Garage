package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class TimeConfig {
    // Всё время в домене берётся отсюда: в тестах подменяется на Clock.fixed.
    // Шаг в микросекунду, как у timestamptz: иначе POST отдавал бы наносекунды, а GET то, что округлила база
    @Bean
    public Clock clock()
    {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
    }
}
