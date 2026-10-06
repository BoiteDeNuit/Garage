package com.example.support;

import com.example.listener.NotificationsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

// Общая база для всех @SpringBootTest. Контейнеры стартуют один раз на весь прогон,
// контекст тоже один: спаи и свойства объявлять только здесь, иначе Spring поднимет второй контекст
// и его консьюмер Kafka заберёт часть партиций у первого
@SpringBootTest(properties = {
        "jwt.secret=garage-test-secret-garage-test-secret",
        "admin.username=boss",
        "admin.password=boss-password",
        "currency.api.url=http://localhost:1",
        "rate-limit.login.per-minute=1000"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @ServiceConnection
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:8.3.2");
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static
    {
        Startables.deepStart(postgres, kafka, redis).join();
    }
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected StringRedisTemplate redisTemplate;
    @MockitoSpyBean
    protected NotificationsListener notificationsListener;
}
