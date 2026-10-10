package com.example;

import com.example.dto.BanRequest;
import com.example.dto.LoginRequest;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BanTest extends IntegrationTest {
    @Autowired
    private PasswordEncoder passwordEncoder;

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
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode("correct-horse-battery"), Role.USER));
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
        jdbcTemplate.update("update listings set moderator_id = ?, claimed_at = now() where id = ?", createUser(Role.MODERATOR).getId(), pending);
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
        assertThat(jdbcTemplate.queryForList(
                "select from_status || '->' || to_status from listing_status_history where listing_id in (?, ?) and actor_id = ? order by listing_id",
                String.class, active, pending, admin().getId()))
                .containsExactly("ACTIVE->ARCHIVED", "PENDING_REVIEW->REJECTED");
    }

    // После разблокировки вход работает, объявления остаются в архиве: опубликовать снова решает продавец
    @Test
    void unbanRestoresAccessButNotListings() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long active = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        ban(seller, "Ошибочная блокировка").andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/{username}/ban", seller.getUsername()).header("Authorization", bearer(admin())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/users/{username}/ban", seller.getUsername()).header("Authorization", bearer(admin())))
                .andExpect(status().isNoContent());

        assertThat(statusOf(active)).isEqualTo("ARCHIVED");
        mockMvc.perform(post("/api/listings/{id}/publish", active).header("Authorization", bearer(seller)))
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

    private ResultActions ban(AppUser user, String reason) throws Exception
    {
        return mockMvc.perform(post("/api/admin/users/{username}/ban", user.getUsername()).header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new BanRequest(reason))));
    }

    // Свой адрес на каждый вход: лимит попыток считается по адресу, а другие тесты тоже входят
    private ResultActions login(String username, String password) throws Exception
    {
        String address = "10.28." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return mockMvc.perform(post("/auth/login")
                .with(request -> { request.setRemoteAddr(address); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private String statusOf(Long id)
    {
        return jdbcTemplate.queryForObject("select status from listings where id = ?", String.class, id);
    }
}
