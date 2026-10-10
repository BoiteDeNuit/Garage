package com.example;

import com.example.model.AppUser;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenTest extends IntegrationTest {
    private static final String PASSWORD = "correct-horse-battery";

    // Кука недоступна скриптам страницы и уходит только на /auth: refresh не утечёт ни через XSS, ни в логи API
    @Test
    void loginSetsRefreshCookie() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);

        String cookie = login(user.getUsername(), PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(cookie).startsWith("refresh_token=").contains("HttpOnly", "SameSite=Strict", "Path=/auth", "Max-Age=1209600");
        String raw = refreshValue(cookie);
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, user.getId())).isEqualTo(1);
    }

    // Обмен даёт новый access-токен и новый refresh, старый refresh больше не работает
    @Test
    void refreshRotatesToken() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);
        String first = refreshValue(login(user.getUsername(), PASSWORD).andReturn());

        MvcResult rotated = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String second = refreshValue(rotated);
        String access = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("token").asString();

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.username").value(user.getUsername()));
        refresh(second).andExpect(status().isOk());
        // Цепочка в базе: каждый обменянный токен ссылается на того, кто его сменил
        assertThat(jdbcTemplate.queryForList("select replaced_by_id is not null from refresh_tokens where user_id = ? order by id", Boolean.class, user.getId()))
                .containsExactly(true, true, false);
    }

    // Обменянный токен пришёл снова: копия у кого-то ещё. Отзывается вся семья, и новый токен тоже,
    // хотя ответ на повтор — 401: отзыв не откатывается вместе с ошибкой
    @Test
    void reusedTokenRevokesWholeFamily() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);
        String first = refreshValue(login(user.getUsername(), PASSWORD).andReturn());
        String second = refreshValue(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(first).andExpect(status().isUnauthorized());

        refresh(second).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForList("select revoke_reason from refresh_tokens where user_id = ? order by id", String.class, user.getId()))
                .containsExactly("ROTATED", "REUSE");
    }

    // Два обмена одного токена одновременно: условный UPDATE пропускает один, второй — 401
    @Test
    void onlyOneOfTwoConcurrentRefreshesWins() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);
        String token = refreshValue(login(user.getUsername(), PASSWORD).andReturn());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try
        {
            Future<Integer> a = pool.submit(() -> { start.await(); return refresh(token).andReturn().getResponse().getStatus(); });
            Future<Integer> b = pool.submit(() -> { start.await(); return refresh(token).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 401);
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredMissingAndUnknownTokensAre401() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);
        String token = refreshValue(login(user.getUsername(), PASSWORD).andReturn());
        jdbcTemplate.update("update refresh_tokens set expires_at = now() - interval '1 minute' where user_id = ?", user.getId());

        refresh(token).andExpect(status().isUnauthorized());
        refresh("not-a-token").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    }

    // Заблокированный не продлевает сессию: 403, семья отозвана
    @Test
    void bannedUserCannotRefresh() throws Exception
    {
        AppUser user = createUser(Role.USER, PASSWORD);
        String token = refreshValue(login(user.getUsername(), PASSWORD).andReturn());
        jdbcTemplate.update("update users set banned_at = now(), ban_reason = 'Проверка' where id = ?", user.getId());

        refresh(token).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("select revoke_reason from refresh_tokens where user_id = ?", String.class, user.getId()))
                .isEqualTo("BAN");
    }

    private ResultActions refresh(String token) throws Exception
    {
        return mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", token)));
    }

    private String refreshValue(MvcResult result)
    {
        return refreshValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String refreshValue(String setCookie)
    {
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }
}
