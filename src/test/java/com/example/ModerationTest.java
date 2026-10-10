package com.example;

import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModerationTest extends IntegrationTest {
    // С модерацией публикация — отправка на проверку: не в ленте, не в Kafka, правка и повторная публикация — 409
    @Test
    void publishWithModerationWaitsForReview() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long id = createDraft(seller, brand);

        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());

        mockMvc.perform(get("/api/listings/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Объявление на модерации, менять его нельзя"));
        verify(notificationsListener, after(2000).never())
                .onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(id)));
        // В подсказки марка попадёт только после одобрения
        mockMvc.perform(get("/api/models").param("q", brand)).andExpect(jsonPath("$[?(@.brand == '" + brand + "')]").isEmpty());
    }

    // Журнал от создания черновика: кто и из какого статуса в какой. Видят продавец и админ
    @Test
    void historyRecordsEveryTransition() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long id = createDraft(seller);
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller))).andExpect(status().isOk());
        mockMvc.perform(post("/api/listings/{id}/archive", id).header("Authorization", bearer(admin()))).andExpect(status().isOk());

        JsonNode history = objectMapper.readTree(mockMvc.perform(get("/api/listings/{id}/history", id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(history.valueStream().map(change -> change.get("fromStatus").asString("") + "->" + change.get("toStatus").asString()).toList())
                .containsExactly("->DRAFT", "DRAFT->ACTIVE", "ACTIVE->ARCHIVED");
        assertThat(history.get(2).get("actorId").asLong()).isEqualTo(admin().getId());
        mockMvc.perform(get("/api/listings/{id}/history", id).header("Authorization", bearer(admin()))).andExpect(status().isOk());
    }

    @Test
    void historyIsHiddenFromOthers() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long id = createDraft(seller);
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller))).andExpect(status().isOk());

        mockMvc.perform(get("/api/listings/{id}/history", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/listings/{id}/history", id).header("Authorization", bearer(createUser(Role.USER)))).andExpect(status().isNotFound());
    }

    private Long createDraft(AppUser seller) throws Exception
    {
        return createDraft(seller, uniqueBrand());
    }

    private Long createDraft(AppUser seller, String brand) throws Exception
    {
        String body = """
                {"brand": "%s", "model": "Supra", "horsePower": 320, "year": 1998, "mileageKm": 154000,
                 "price": 4500000, "city": "Самара"}""".formatted(brand);
        return objectMapper.readTree(mockMvc.perform(post("/api/listings").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
