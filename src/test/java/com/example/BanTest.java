package com.example;

import com.example.dto.BanRequest;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BanTest extends IntegrationTest {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // Токен выдан до блокировки, но пользователь читается из базы на каждый запрос: блокировка действует сразу
    @Test
    void bannedUserIsStoppedOnTheSameToken() throws Exception
    {
        AppUser user = createUser(Role.USER);
        mockMvc.perform(get("/api/me/listings").header("Authorization", bearer(user))).andExpect(status().isOk());

        ban(user, "Продаёт чужие машины").andExpect(status().isOk());

        mockMvc.perform(get("/api/me/listings").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Аккаунт заблокирован"));
        mockMvc.perform(get("/api/listings")).andExpect(status().isOk());
    }

    // 403 только с верным паролем. С неверным — 401, как у всех: по ответу не узнать, что логин заблокирован
    @Test
    void bannedUserCannotLogIn() throws Exception
    {
        AppUser user = createUser(Role.USER, "correct-horse-battery");
        String username = user.getUsername();
        ban(user, "Мошенничество").andExpect(status().isOk());

        login(username, "correct-horse-battery")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Аккаунт заблокирован"));
        login(username, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверный логин или пароль"));
    }

    // Опубликованное — в архив, ждущее проверки — отклонено с причиной, остальное не трогаем.
    // Карточка, которая уже лежала в кэше, тоже пропадает
    @Test
    void banHidesListings() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long active = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long pending = insertListing(seller, brand, ListingStatus.PENDING_REVIEW);
        Long draft = insertListing(seller, brand, ListingStatus.DRAFT);
        Long sold = insertListing(seller, brand, ListingStatus.SOLD);
        // Чужие объявления того же вида: блокировка не должна их задеть
        AppUser bystander = createUser(Role.USER);
        Long otherActive = insertListing(bystander, brand, ListingStatus.ACTIVE);
        Long otherPending = insertListing(bystander, brand, ListingStatus.PENDING_REVIEW);
        Long moderatorId = createUser(Role.MODERATOR).getId();
        jdbcTemplate.update("update listings set moderator_id = ?, claimed_at = now() where id in (?, ?)", moderatorId, pending, otherPending);
        mockMvc.perform(get("/api/listings/{id}", active)).andExpect(status().isOk());

        ban(seller, "  Спам в объявлениях  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Спам в объявлениях"))
                .andExpect(jsonPath("$.hiddenListings").value(2));

        mockMvc.perform(get("/api/listings/{id}", active)).andExpect(status().isNotFound());
        assertThat(statusOf(active)).isEqualTo("ARCHIVED");
        assertThat(statusOf(pending)).isEqualTo("REJECTED");
        assertThat(statusOf(draft)).isEqualTo("DRAFT");
        assertThat(statusOf(sold)).isEqualTo("SOLD");
        assertThat(jdbcTemplate.queryForObject("select rejection_reason from listings where id = ?", String.class, pending)).isEqualTo("Спам в объявлениях");
        assertThat(jdbcTemplate.queryForObject("select moderator_id from listings where id = ?", Long.class, pending)).isNull();
        assertThat(jdbcTemplate.queryForObject("select claimed_at is null from listings where id = ?", Boolean.class, pending)).isTrue();
        // Массовый UPDATE идёт мимо @Version, версию он поднимает сам: иначе правка со старой версией прошла бы
        assertThat(versionOf(active)).isEqualTo(1L);
        assertThat(versionOf(pending)).isEqualTo(1L);
        assertThat(statusOf(otherActive)).isEqualTo("ACTIVE");
        assertThat(statusOf(otherPending)).isEqualTo("PENDING_REVIEW");
        assertThat(versionOf(otherActive)).isZero();
        assertThat(jdbcTemplate.queryForObject("select moderator_id from listings where id = ?", Long.class, otherPending)).isEqualTo(moderatorId);
        assertThat(jdbcTemplate.queryForList(
                "select from_status || '->' || to_status from listing_status_history where listing_id in (?, ?) and actor_id = ? order by listing_id",
                String.class, active, pending, admin().getId()))
                .containsExactly("ACTIVE->ARCHIVED", "PENDING_REVIEW->REJECTED");
    }

    // После разблокировки нужен новый вход: токены, выданные до блокировки, не оживают.
    // Объявления остаются в архиве: опубликовать снова решает продавец
    @Test
    void unbanRestoresAccessButNotListings() throws Exception
    {
        AppUser seller = createUser(Role.USER, "correct-horse-battery");
        Long active = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        String oldToken = bearer(seller);
        String oldRefresh = login(seller.getUsername(), "correct-horse-battery").andReturn().getResponse().getCookie("refresh_token").getValue();
        ban(seller, "Ошибочная блокировка").andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/{username}/ban", seller.getUsername()).header("Authorization", bearer(admin())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/users/{username}/ban", seller.getUsername()).header("Authorization", bearer(admin())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Токен отозван, войдите заново"));
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", oldRefresh))).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("select revoke_reason from refresh_tokens where user_id = ?", String.class, seller.getId())).isEqualTo("BAN");

        assertThat(statusOf(active)).isEqualTo("ARCHIVED");
        AppUser relogged = userRepository.findById(seller.getId()).orElseThrow();
        mockMvc.perform(post("/api/listings/{id}/publish", active).header("Authorization", bearer(relogged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void banRules() throws Exception
    {
        AppUser user = createUser(Role.USER);
        ban(user, "нет").andExpect(status().isBadRequest());
        ban(user, "  x  ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/users/{username}/ban", "ghost-" + UUID.randomUUID()).header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Нет такого\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/users/{username}/ban", "boss").header("Authorization", bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Себя нельзя\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/users/{username}/ban", user.getUsername()).header("Authorization", bearer(createUser(Role.MODERATOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Не админ\"}"))
                .andExpect(status().isForbidden());

        ban(user, "Первая блокировка").andExpect(status().isOk());
        ban(user, "Вторая блокировка")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Пользователь уже заблокирован"));
    }

    // Двойной клик: вторая блокировка приходит, пока первая не закоммичена. Первую здесь изображает транзакция теста,
    // которая держит строку пользователя. Вторая ждёт её, читает уже заблокированного и получает 409.
    // Без SELECT ... FOR UPDATE вторая прочитала бы «не заблокирован», дождалась бы строки на UPDATE и перезаписала бы первую
    @Test
    void secondBanWaitsForFirstAndGets409() throws Exception
    {
        AppUser user = createUser(Role.USER);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        EntityManager first = entityManagerFactory.createEntityManager();
        try
        {
            first.getTransaction().begin();
            AppUser locked = first.find(AppUser.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
            Future<Integer> second = pool.submit(() -> ban(user, "Вторая блокировка").andReturn().getResponse().getStatus());
            awaitLockWait();

            locked.ban("Первая блокировка", Instant.now());
            first.getTransaction().commit();

            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        }
        finally
        {
            first.close();
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select ban_reason from users where id = ?", String.class, user.getId())).isEqualTo("Первая блокировка");
    }

    // Смена роли прочитала пользователя до блокировки, а записала после. UPDATE только изменённых колонок
    // (@DynamicUpdate) не трогает banned_at, и блокировка остаётся
    @Test
    void staleRoleChangeKeepsBan() throws Exception
    {
        AppUser user = createUser(Role.USER);
        EntityManager stale = entityManagerFactory.createEntityManager();
        try
        {
            stale.getTransaction().begin();
            AppUser snapshot = stale.find(AppUser.class, user.getId());

            ban(user, "Блокировка посреди смены роли").andExpect(status().isOk());

            snapshot.changeRole(Role.MODERATOR);
            stale.getTransaction().commit();
        }
        finally
        {
            stale.close();
        }
        assertThat(jdbcTemplate.queryForMap("select role, ban_reason from users where id = ?", user.getId()))
                .containsEntry("role", "MODERATOR")
                .containsEntry("ban_reason", "Блокировка посреди смены роли");
    }

    private ResultActions ban(AppUser user, String reason) throws Exception
    {
        return mockMvc.perform(post("/api/admin/users/{username}/ban", user.getUsername()).header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new BanRequest(reason))));
    }

    // Ждём, пока запрос встанет в очередь за блокировкой строки
    private void awaitLockWait() throws InterruptedException
    {
        for(int i = 0; i < 100; i++)
        {
            Integer waiting = jdbcTemplate.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Integer.class);
            if(waiting != null && waiting > 0)
            {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Второй запрос не встал в ожидание блокировки");
    }

    private Long versionOf(Long id)
    {
        return jdbcTemplate.queryForObject("select version from listings where id = ?", Long.class, id);
    }

    private String statusOf(Long id)
    {
        return jdbcTemplate.queryForObject("select status from listings where id = ?", String.class, id);
    }
}
