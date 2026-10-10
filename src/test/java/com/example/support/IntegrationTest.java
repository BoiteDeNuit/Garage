package com.example.support;

import com.example.listener.NotificationsListener;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import com.example.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    // S3_SKIP_SIGNATURE_VALIDATION=0: LocalStack по умолчанию подпись presigned-ссылок не проверяет,
    // а тестам нужно, чтобы файл не того размера или типа получал 403, как в настоящем S3
    protected static final LocalStackContainer s3 = new LocalStackContainer("localstack/localstack:4.9.2")
            .withServices("s3")
            .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
    protected static final String BUCKET = "tachkiosk-photos";
    static
    {
        Startables.deepStart(postgres, kafka, redis, s3).join();
        // Бакет заводит не приложение, а тот, кто разворачивает хранилище: в compose — скрипт LocalStack
        try (S3Client client = testS3Client())
        {
            client.createBucket(request -> request.bucket(BUCKET));
        }
    }
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry)
    {
        registry.add("storage.endpoint", () -> s3.getEndpoint().toString());
        registry.add("storage.public-endpoint", () -> s3.getEndpoint().toString());
        registry.add("storage.region", s3::getRegion);
        registry.add("storage.access-key", s3::getAccessKey);
        registry.add("storage.secret-key", s3::getSecretKey);
        registry.add("storage.bucket", () -> BUCKET);
    }
    protected static S3Client testS3Client()
    {
        return S3Client.builder()
                .endpointOverride(s3.getEndpoint())
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected StringRedisTemplate redisTemplate;
    @Autowired
    protected AppUserRepository userRepository;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    protected NotificationsListener notificationsListener;

    // База общая на все классы: у каждого теста свои пользователи и марки, на пустые таблицы не рассчитываем
    protected AppUser createUser(Role role)
    {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new AppUser(username, "!", role));
    }
    protected AppUser admin()
    {
        return userRepository.findByUsername("boss").orElseThrow();
    }
    // Токен напрямую через JwtService: вход через /auth/login упирается в общий лимит попыток
    protected String bearer(AppUser user)
    {
        return "Bearer " + jwtService.generateToken(user.getUsername(), List.of("ROLE_" + user.getRole()));
    }
    protected String uniqueBrand()
    {
        return "B" + UUID.randomUUID().toString().substring(0, 6);
    }
    // Объявление в любом статусе прямо в базу: так тест не зависит от того, как устроена публикация.
    // Цена и город есть у всех статусов: скрытое объявление должно отдавать 404 из-за статуса, а не из-за пустой цены
    protected Long insertListing(AppUser seller, String brand, ListingStatus status)
    {
        boolean published = status == ListingStatus.ACTIVE || status == ListingStatus.SOLD;
        return jdbcTemplate.queryForObject(
                "insert into listings (seller_id, status, brand, model, engine_code, horse_power, year, mileage_km, price, city, published_at) " +
                        "values (?, ?, ?, 'Supra', '2JZ', 320, 1998, 150000, 4500000, 'Самара', ?) returning id",
                Long.class,
                seller.getId(), status.name(), brand,
                published ? Timestamp.from(Instant.now()) : null);
    }
}
