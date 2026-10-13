package com.example;

import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // Весь путь: отправка, захват, одобрение. Дата публикации — момент одобрения, объявление в ленте и в Kafka
    @Test
    void approvedListingGoesLive() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        AppUser moderator = createUser(Role.MODERATOR);
        String brand = uniqueBrand();
        Long id = submit(seller, brand);

        mockMvc.perform(get("/api/listings/{id}", id).header("Authorization", bearer(moderator))).andExpect(status().isOk());
        claim(id, moderator).andExpect(status().isOk()).andExpect(jsonPath("$.moderatorId").value(moderator.getId()));
        mockMvc.perform(post("/api/moderation/listings/{id}/approve", id).header("Authorization", bearer(moderator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publishedAt").exists());

        mockMvc.perform(get("/api/listings/{id}", id)).andExpect(status().isOk());
        verify(notificationsListener, timeout(15000))
                .onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(id)));
        mockMvc.perform(get("/api/models").param("q", brand)).andExpect(jsonPath("$[0].brand").value(brand));
        assertThat(transitions(id, seller)).containsExactly("->DRAFT", "DRAFT->PENDING_REVIEW", "PENDING_REVIEW->ACTIVE");
    }

    // Отказ с причиной: продавец видит её в карточке, исправляет и отправляет снова — объявление опять в очереди
    @Test
    void rejectedListingIsFixedAndResubmitted() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        AppUser moderator = createUser(Role.MODERATOR);
        Long id = submit(seller, uniqueBrand());
        claim(id, moderator).andExpect(status().isOk());

        mockMvc.perform(post("/api/moderation/listings/{id}/reject", id).header("Authorization", bearer(moderator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"нет\"}"))
                .andExpect(status().isBadRequest());
        // Пять символов с пробелами проходят @Size, но после обрезки остаётся один
        mockMvc.perform(post("/api/moderation/listings/{id}/reject", id).header("Authorization", bearer(moderator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  x  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/moderation/listings/{id}/reject", id).header("Authorization", bearer(moderator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  На фото другая машина  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/listings/{id}", id).header("Authorization", bearer(seller)))
                .andExpect(jsonPath("$.rejectionReason").value("На фото другая машина"));
        mockMvc.perform(get("/api/listings/{id}", id)).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        assertThat(queueIds(moderator)).contains(id);
        // Решение снимает захват: исправленное объявление сразу может взять другой модератор, не дожидаясь 30 минут
        assertThat(jdbcTemplate.queryForObject("select moderator_id from listings where id = ?", Long.class, id)).isNull();
        claim(id, createUser(Role.MODERATOR)).andExpect(status().isOk());
    }

    // Двое берут одно объявление одновременно. Условный UPDATE пропускает одного, второй получает 409
    // и не может принять решение
    @Test
    void onlyOneOfTwoModeratorsGetsTheListing() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        AppUser first = createUser(Role.MODERATOR);
        AppUser second = createUser(Role.MODERATOR);
        Long id = submit(seller, uniqueBrand());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try
        {
            Future<Integer> a = pool.submit(() -> { start.await(); return claim(id, first).andReturn().getResponse().getStatus(); });
            Future<Integer> b = pool.submit(() -> { start.await(); return claim(id, second).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        }
        finally
        {
            pool.shutdownNow();
        }
        Long holder = jdbcTemplate.queryForObject("select moderator_id from listings where id = ?", Long.class, id);
        AppUser loser = holder.equals(first.getId()) ? second : first;

        mockMvc.perform(post("/api/moderation/listings/{id}/approve", id).header("Authorization", bearer(loser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Сначала возьмите объявление в работу"));
        claim(id, loser).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Объявление уже проверяет другой модератор"));
    }

    // Модератор взял и пропал: через 30 минут объявление может забрать другой
    @Test
    void abandonedClaimCanBeTaken() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser first = createUser(Role.MODERATOR);
        AppUser second = createUser(Role.MODERATOR);
        Long id = submit(createUser(Role.USER), uniqueBrand());
        claim(id, first).andExpect(status().isOk());
        jdbcTemplate.update("update listings set claimed_at = now() - interval '31 minutes' where id = ?", id);

        claim(id, second).andExpect(status().isOk()).andExpect(jsonPath("$.moderatorId").value(second.getId()));
    }

    // Модератор не проверяет своё объявление. Обычный пользователь в очередь не попадает, без токена — 401
    @Test
    void moderationNeedsRoleAndForeignListing() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser moderatorSeller = createUser(Role.MODERATOR);
        Long own = submit(moderatorSeller, uniqueBrand());

        claim(own, moderatorSeller).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/moderation/queue").header("Authorization", bearer(createUser(Role.USER)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/moderation/queue")).andExpect(status().isUnauthorized());
        claim(createDraft(createUser(Role.USER)), createUser(Role.MODERATOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Объявление не ждёт модерации"));
    }

    // Сначала те, что ждут дольше
    @Test
    void queueIsOldestFirst() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        Long newer = submit(seller, uniqueBrand());
        Long older = submit(seller, uniqueBrand());
        // id у старшего больше: порядок по id дал бы обратный ответ, и тест бы это поймал
        jdbcTemplate.update("update listings set submitted_at = now() - interval '1 day' where id = ?", older);

        List<Long> queue = queueIds(createUser(Role.MODERATOR));
        assertThat(queue).contains(older, newer);
        assertThat(queue.indexOf(older)).isLessThan(queue.indexOf(newer));
    }

    // Админ назначает модератора, роль действует сразу на том же токене. Админа через API не назначить и не снять
    @Test
    void adminAppointsModerator() throws Exception
    {
        AppUser user = createUser(Role.USER);
        mockMvc.perform(get("/api/moderation/queue").header("Authorization", bearer(user))).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/users/{username}/role", user.getUsername()).header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"MODERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
        mockMvc.perform(get("/api/moderation/queue").header("Authorization", bearer(user))).andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/{username}/role", user.getUsername()).header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/{username}/role", "boss").header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"USER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/users/{username}/role", user.getUsername()).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"USER\"}"))
                .andExpect(status().isForbidden());
    }

    // Ни разу не публиковавшееся можно удалить и с проверки
    @Test
    void pendingListingCanBeDeleted() throws Exception
    {
        when(moderationSettings.required()).thenReturn(true);
        AppUser seller = createUser(Role.USER);
        Long id = submit(seller, uniqueBrand());

        mockMvc.perform(delete("/api/listings/{id}", id).header("Authorization", bearer(seller))).andExpect(status().isNoContent());
    }

    private Long submit(AppUser seller, String brand) throws Exception
    {
        Long id = createDraft(seller, brand);
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        return id;
    }

    private ResultActions claim(Long id, AppUser moderator) throws Exception
    {
        return mockMvc.perform(post("/api/moderation/listings/{id}/claim", id).header("Authorization", bearer(moderator)));
    }

    private List<Long> queueIds(AppUser moderator) throws Exception
    {
        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/moderation/queue").param("size", "100").header("Authorization", bearer(moderator)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return page.get("content").valueStream().map(item -> item.get("listing").get("id").asLong()).toList();
    }

    private List<String> transitions(Long id, AppUser viewer) throws Exception
    {
        JsonNode history = objectMapper.readTree(mockMvc.perform(get("/api/listings/{id}/history", id).header("Authorization", bearer(viewer)))
                .andReturn().getResponse().getContentAsString());
        return history.valueStream().map(change -> change.get("fromStatus").asString("") + "->" + change.get("toStatus").asString()).toList();
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
