package com.example;

import com.example.job.ListingExpiry;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.ListingRepository;
import com.example.support.IntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListingExpiryTest extends IntegrationTest {
    @Autowired
    private ListingExpiry listingExpiry;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;

    // Строку блокировки не удаляем: ShedLock её не вернёт (см. CleanupJobsTest)
    @BeforeEach
    @AfterEach
    void releaseLocks()
    {
        jdbcTemplate.update("update shedlock set lock_until = now()");
    }

    // Опубликованное 61 день назад уходит в архив: запись в журнале без автора, уведомление продавцу,
    // карточка из кэша пропадает. Опубликованное 59 дней назад и проданное не трогаются
    @Test
    void expiresOnlyOldActiveListings() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long old = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long recent = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long sold = insertListing(seller, brand, ListingStatus.SOLD);
        publishedDaysAgo(old, 61);
        publishedDaysAgo(recent, 59);
        publishedDaysAgo(sold, 100);
        mockMvc.perform(get("/api/listings/{id}", old)).andExpect(status().isOk());

        listingExpiry.run();

        mockMvc.perform(get("/api/listings/{id}", old)).andExpect(status().isNotFound());
        assertThat(statusOf(old)).isEqualTo("ARCHIVED");
        assertThat(statusOf(recent)).isEqualTo("ACTIVE");
        assertThat(statusOf(sold)).isEqualTo("SOLD");
        assertThat(jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, old)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForMap("select from_status, to_status, actor_id, reason from listing_status_history where listing_id = ?", old))
                .containsEntry("from_status", "ACTIVE")
                .containsEntry("to_status", "ARCHIVED")
                .containsEntry("actor_id", null)
                .containsEntry("reason", "Срок публикации истёк");
        mockMvc.perform(get("/api/me/notifications").header("Authorization", bearer(seller)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("LISTING_EXPIRED"))
                .andExpect(jsonPath("$.content[0].listingId").value(old));
    }

    // Из архива продавец публикует снова, срок идёт заново: следующий запуск объявление не тронет
    @Test
    void republishedListingGetsNewPeriod() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long id = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        publishedDaysAgo(id, 61);
        listingExpiry.run();

        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        releaseLocks();
        listingExpiry.run();

        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from notifications where listing_id = ?", Integer.class, id)).isEqualTo(1);
    }

    // Пачки по одному, не больше двух за запуск: из трёх старых снимаются два самых старых, третье ждёт следующего запуска
    @Test
    void stopsAtMaxPerRun()
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long oldest = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long older = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long old = insertListing(seller, brand, ListingStatus.ACTIVE);
        // Глубже любых чужих тестовых объявлений: пачка из самых старых — наши
        publishedDaysAgo(oldest, 5003);
        publishedDaysAgo(older, 5002);
        publishedDaysAgo(old, 5001);
        ListingExpiry limited = new ListingExpiry(listingRepository, events, transactionManager, clock,
                Duration.ofDays(60), 1, 2, new SimpleMeterRegistry());

        limited.run();

        assertThat(statusOf(oldest)).isEqualTo("ARCHIVED");
        assertThat(statusOf(older)).isEqualTo("ARCHIVED");
        assertThat(statusOf(old)).isEqualTo("ACTIVE");
    }

    private void publishedDaysAgo(Long id, int days)
    {
        jdbcTemplate.update("update listings set published_at = now() - make_interval(days => ?) where id = ?", days, id);
    }

    private String statusOf(Long id)
    {
        return jdbcTemplate.queryForObject("select status from listings where id = ?", String.class, id);
    }
}
