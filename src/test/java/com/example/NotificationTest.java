package com.example;

import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.service.NotificationService;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Публикация -> событие в Kafka после коммита -> группа notifications -> сопоставление с сохранёнными поисками
class NotificationTest extends IntegrationTest {
    @Autowired
    NotificationService notificationService;

    @Test
    void publishedListingNotifiesMatchingSearch() throws Exception
    {
        AppUser buyer = createUser(Role.USER);
        AppUser other = createUser(Role.USER);
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        saveSearch(buyer, "{\"name\": \"Нужная марка\", \"criteria\": {\"brand\": \"" + brand.toLowerCase() + "\", \"priceTo\": 5000000}}");
        saveSearch(other, "{\"name\": \"Слишком дёшево\", \"criteria\": {\"brand\": \"" + brand + "\", \"priceTo\": 1000000}}");
        Long listingId = createAndPublish(seller, brand);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(unread(buyer)).isEqualTo(1));
        mockMvc.perform(get("/api/me/notifications").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.content[0].type").value("NEW_LISTING"))
                .andExpect(jsonPath("$.content[0].listingId").value(listingId))
                .andExpect(jsonPath("$.content[0].read").value(false));
        assertThat(unread(other)).isZero();
        assertThat(unread(seller)).isZero();

        mockMvc.perform(post("/api/me/notifications/read").header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        assertThat(unread(buyer)).isZero();
    }

    // Kafka доставляет at-least-once. Повтор того же события упирается в UNIQUE (user_id, event_id)
    @Test
    void redeliveredEventCreatesNoDuplicate() throws Exception
    {
        AppUser buyer = createUser(Role.USER);
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        saveSearch(buyer, "{\"name\": \"Марка\", \"criteria\": {\"brand\": \"" + brand + "\"}}");
        Long listingId = createAndPublish(seller, brand);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(unread(buyer)).isEqualTo(1));
        Instant publishedAt = jdbcTemplate.queryForObject("select published_at from listings where id = ?", java.sql.Timestamp.class, listingId).toInstant();

        int created = notificationService.notifyMatchingSearches(
                new ListingPublishedEvent(listingId, seller.getId(), brand, "Supra", new BigDecimal("4500000"), publishedAt));

        assertThat(created).isZero();
        assertThat(unread(buyer)).isEqualTo(1);
    }

    // Снижение цены — тем, у кого объявление в избранном, с ценами «было — стало». Рост цены — не новость
    @Test
    void priceDropNotifiesFavorites() throws Exception
    {
        AppUser fan = createUser(Role.USER);
        AppUser seller = createUser(Role.USER);
        Long listingId = createAndPublish(seller, uniqueBrand());
        mockMvc.perform(put("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(fan))).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(seller))).andExpect(status().isNoContent());

        long version = changePrice(seller, listingId, currentVersion(listingId), "3900000");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(unread(fan)).isEqualTo(1));
        mockMvc.perform(get("/api/me/notifications").header("Authorization", bearer(fan)))
                .andExpect(jsonPath("$.content[0].type").value("PRICE_DROP"))
                .andExpect(jsonPath("$.content[0].oldPrice").value(4500000.00))
                .andExpect(jsonPath("$.content[0].newPrice").value(3900000.00));

        changePrice(seller, listingId, version, "4100000");
        Thread.sleep(2000);
        assertThat(unread(fan)).isEqualTo(1);
        assertThat(unread(seller)).isZero();
    }

    @Test
    void notificationsHaveFixedOrder() throws Exception
    {
        mockMvc.perform(get("/api/me/notifications").param("sort", "createdAt,asc").header("Authorization", bearer(createUser(Role.USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Уведомления идут только от новых к старым"));
    }

    private void saveSearch(AppUser user, String body) throws Exception
    {
        mockMvc.perform(post("/api/me/searches").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private Long createAndPublish(AppUser seller, String brand) throws Exception
    {
        String body = """
                {"brand": "%s", "model": "Supra", "horsePower": 320, "year": 1998, "mileageKm": 154000,
                 "price": 4500000, "city": "Самара", "description": "Один владелец"}""".formatted(brand);
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/listings").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        Long id = created.get("id").asLong();
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller))).andExpect(status().isOk());
        return id;
    }

    private long currentVersion(Long listingId)
    {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, listingId);
    }

    private long changePrice(AppUser seller, Long listingId, long version, String price) throws Exception
    {
        String body = """
                {"version": %d, "brand": "Toyota", "model": "Supra", "horsePower": 320, "year": 1998, "mileageKm": 154000,
                 "price": %s, "city": "Самара", "description": "Один владелец"}""".formatted(version, price);
        return objectMapper.readTree(mockMvc.perform(put("/api/listings/{id}", listingId).header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
    }

    private long unread(AppUser user) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", bearer(user)))
                .andReturn().getResponse().getContentAsString()).get("unread").asLong();
    }
}
