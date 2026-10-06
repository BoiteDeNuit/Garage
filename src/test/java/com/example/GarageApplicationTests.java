package com.example;

import com.example.dto.CarDto;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.event.CarCreatedEvent;
import com.example.security.JwtService;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GarageApplicationTests extends IntegrationTest {
    @Autowired
    JwtService jwtService;

    @Test
    void adminCreatesReadsAndDeletesCar() throws Exception
    {
        String token = login();
        Long id = createCar(token);

        mockMvc.perform(get("/api/cars/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Supra"))
                .andExpect(jsonPath("$.price").value(4500000));

        mockMvc.perform(delete("/api/cars/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cars/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(post("/api/cars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"));
    }

    @Test
    void wrongPasswordIs401() throws Exception
    {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("boss", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void carIsCachedInRedisAndEvictedOnDelete() throws Exception
    {
        String token = login();
        Long id = createCar(token);

        mockMvc.perform(get("/api/cars/" + id)).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey("cars::" + id)).isTrue();

        mockMvc.perform(delete("/api/cars/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(redisTemplate.hasKey("cars::" + id)).isFalse();
    }

    @Test
    void createdCarReachesKafkaListener() throws Exception
    {
        Long id = createCar(login());

        verify(notificationsListener, timeout(15000))
                .onCarCreated(argThat((CarCreatedEvent event) -> event.id().equals(id)));
    }

    @Test
    void prometheusExposesCarsCounter() throws Exception
    {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("garage_cars_added_total")))
                .andExpect(content().string(containsString("cache_gets_total{cache=\"cars\"")));
    }

    @Test
    void loginTokenCarriesOnlyRoles() throws Exception
    {
        assertThat(jwtService.extractRoles(login())).containsExactly("ROLE_ADMIN");
    }

    @Test
    void unknownSortFieldIs400() throws Exception
    {
        mockMvc.perform(get("/api/cars").param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нельзя сортировать по полю: foo"));
    }

    private String login() throws Exception
    {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("boss", "boss-password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, LoginResponse.class).token();
    }

    private Long createCar(String token) throws Exception
    {
        String body = mockMvc.perform(post("/api/cars")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CarDto.class).id();
    }

    private CarDto supra()
    {
        return new CarDto(null, "Toyota", "Supra", "2JZ", 320, 1998, new BigDecimal("4500000"));
    }
}
