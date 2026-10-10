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

    private long unread(AppUser user) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(get("/api/me/notifications/unread-count").header("Authorization", bearer(user)))
                .andReturn().getResponse().getContentAsString()).get("unread").asLong();
    }
}
