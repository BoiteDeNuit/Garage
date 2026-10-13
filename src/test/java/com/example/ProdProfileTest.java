package com.example;

import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Профиль prod поднимается целиком на тех же контейнерах: опечатка в application-prod.yml уронила бы этот тест,
// а не запуск на сервере. Это второй контекст рядом с общим: его слушатели Kafka не запускаются, иначе они вошли бы
// в те же группы и забрали часть партиций у общего контекста, и тесты уведомлений ждали бы событий впустую.
// После класса контекст закрывается, а не висит в кэше до конца прогона
@ActiveProfiles("prod")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProdProfileTest extends IntegrationTest {
    @Test
    void swaggerIsOff() throws Exception
    {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    // health отвечает балансировщику, но не рассказывает, что внутри
    @Test
    void healthWithoutDetails() throws Exception
    {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        // info не выставлен совсем: даже с токеном админа его нет
        mockMvc.perform(get("/actuator/info").header("Authorization", bearer(admin()))).andExpect(status().isNotFound());
    }

    @Test
    void apiWorks() throws Exception
    {
        mockMvc.perform(get("/api/listings")).andExpect(status().isOk());
    }
}
