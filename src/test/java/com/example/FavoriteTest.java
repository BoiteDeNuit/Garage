package com.example;

import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FavoriteTest extends IntegrationTest {
    // PUT и DELETE идемпотентны: повтор не ошибка и не дубль
    @Test
    void addAndRemoveAreIdempotent() throws Exception
    {
        AppUser buyer = createUser(Role.USER);
        Long listingId = insertListing(createUser(Role.USER), uniqueBrand(), ListingStatus.ACTIVE);

        mockMvc.perform(put("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        assertThat(favoriteRows(buyer, listingId)).isEqualTo(1);

        mockMvc.perform(delete("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/listings/{id}/favorite", listingId).header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        assertThat(favoriteRows(buyer, listingId)).isZero();
    }

    // Чужой черновик в избранное не добавить: 404, как при просмотре. Без входа — 401
    @Test
    void onlyVisibleListingCanBeAdded() throws Exception
    {
        AppUser buyer = createUser(Role.USER);
        Long draft = insertListing(createUser(Role.USER), uniqueBrand(), ListingStatus.DRAFT);

        mockMvc.perform(put("/api/listings/{id}/favorite", draft).header("Authorization", bearer(buyer))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/listings/{id}/favorite", 999_999_999L).header("Authorization", bearer(buyer))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/listings/{id}/favorite", draft)).andExpect(status().isUnauthorized());
        assertThat(favoriteRows(buyer, draft)).isZero();
    }

    // Недавно добавленные первыми. Проданное остаётся, снятое в архив пропадает. Чужое избранное не видно
    @Test
    void myFavoritesShowVisibleListingsNewestFirst() throws Exception
    {
        AppUser buyer = createUser(Role.USER);
        AppUser seller = createUser(Role.USER);
        Long first = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        Long second = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        Long archived = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        for (Long id : List.of(first, second, archived))
        {
            mockMvc.perform(put("/api/listings/{id}/favorite", id).header("Authorization", bearer(buyer))).andExpect(status().isNoContent());
        }
        jdbcTemplate.update("update listings set status = 'SOLD' where id = ?", first);
        jdbcTemplate.update("update listings set status = 'ARCHIVED' where id = ?", archived);

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.get("content").valueStream().map(item -> item.get("id").asLong()).toList()).containsExactly(second, first);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(2);
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(createUser(Role.USER))))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void favoritesHaveFixedOrder() throws Exception
    {
        mockMvc.perform(get("/api/me/favorites").param("sort", "price,asc").header("Authorization", bearer(createUser(Role.USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Избранное сортируется только по дате добавления"));
    }

    // Строки избранного уходят вместе с черновиком: каскад в базе
    @Test
    void deletedDraftLeavesNoFavorites() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long draft = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        mockMvc.perform(put("/api/listings/{id}/favorite", draft).header("Authorization", bearer(seller))).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/listings/{id}", draft).header("Authorization", bearer(seller))).andExpect(status().isNoContent());

        assertThat(favoriteRows(seller, draft)).isZero();
    }

    private long favoriteRows(AppUser user, Long listingId)
    {
        return jdbcTemplate.queryForObject("select count(*) from favorites where user_id = ? and listing_id = ?", Long.class, user.getId(), listingId);
    }
}
