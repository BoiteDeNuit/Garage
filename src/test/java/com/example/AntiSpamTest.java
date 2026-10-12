package com.example;

import com.example.dto.RegisterRequest;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AntiSpamTest extends IntegrationTest {
    // 20 новых объявлений в час, 21-е — 429. Retry-After — секунды до следующего часа, а не произвольное число
    @Test
    void twentyListingsPerHour() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        for(int i = 0; i < 20; i++)
        {
            createDraft(seller, uniqueBrand()).andExpect(status().isCreated());
        }

        String retryAfter = createDraft(seller, uniqueBrand())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Не больше 20 новых объявлений в час"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 3600L);
        createDraft(createUser(Role.USER), uniqueBrand()).andExpect(status().isCreated());
    }

    // 10 публикаций в сутки. Счётчик у каждого продавца свой
    @Test
    void tenPublicationsPerDay() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        for(int i = 0; i < 10; i++)
        {
            publish(seller, insertListing(seller, uniqueBrand(), ListingStatus.DRAFT)).andExpect(status().isOk());
        }

        publish(seller, insertListing(seller, uniqueBrand(), ListingStatus.DRAFT))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Не больше 10 публикаций в сутки"));
    }

    // Та же машина за ту же цену в том же городе уже в ленте — повтор не публикуется. Проданное повтором не считается:
    // продал и выставил такую же — это новое объявление
    @Test
    void duplicateOfOwnActiveListingIsRejected() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long first = insertListing(seller, brand, ListingStatus.ACTIVE);
        Long copy = insertListing(seller, brand.toLowerCase(), ListingStatus.DRAFT);

        publish(seller, copy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Такое же объявление уже опубликовано или на проверке: id " + first));

        jdbcTemplate.update("update listings set status = 'SOLD' where id = ?", first);
        publish(seller, copy).andExpect(status().isOk());
        // У другого продавца такая же машина — не повтор
        AppUser other = createUser(Role.USER);
        publish(other, insertListing(other, brand, ListingStatus.DRAFT)).andExpect(status().isOk());
    }

    // 10 регистраций в час с одного адреса, 11-я — 429. С другого адреса регистрация идёт
    @Test
    void tenRegistrationsPerHourFromOneAddress() throws Exception
    {
        String address = "10.39." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
        for(int i = 0; i < 10; i++)
        {
            register(address).andExpect(status().isCreated());
        }

        register(address)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Слишком много регистраций с вашего адреса"));
        register("10.40.0." + ThreadLocalRandom.current().nextInt(256)).andExpect(status().isCreated());
    }

    private ResultActions createDraft(AppUser seller, String brand) throws Exception
    {
        return mockMvc.perform(post("/api/listings").header("Authorization", bearer(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brand\": \"" + brand + "\", \"model\": \"Supra\", \"engineCode\": \"2JZ\", \"horsePower\": 320, \"year\": 1998, \"mileageKm\": 154000}"));
    }

    private ResultActions publish(AppUser seller, Long id) throws Exception
    {
        return mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller)));
    }

    private ResultActions register(String address) throws Exception
    {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return mockMvc.perform(post("/auth/register")
                .with(request -> { request.setRemoteAddr(address); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, "correct-horse-battery"))));
    }
}
