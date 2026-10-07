package com.example;

import com.example.config.KafkaTopicsConfig;
import com.example.dto.ListingDto;
import com.example.dto.ListingRequest;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GarageApplicationTests extends IntegrationTest {
    @Autowired
    KafkaTemplate<Long, ListingPublishedEvent> kafkaTemplate;

    @Test
    void sellerCreatesDraftVisibleOnlyToSellerAndAdmin() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        String brand = uniqueBrand();

        String body = mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra(brand))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/listings/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.sellerId").value(seller.getId()))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(body, ListingDto.class).id();

        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(seller))).andExpect(status().isOk());
        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(admin()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/listings").param("brand", brand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void createWithoutTokenIs401() throws Exception
    {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra("Toyota"))))
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
    void loginTokenCarriesOnlyRoles() throws Exception
    {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("boss", "boss-password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(body, LoginResponse.class).token();

        assertThat(jwtService.extractRoles(token)).containsExactly("ROLE_ADMIN");
    }

    @Test
    void publicFeedShowsOnlyActive() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long active = insertListing(seller, brand, ListingStatus.ACTIVE);
        insertListing(seller, brand, ListingStatus.DRAFT);
        insertListing(seller, brand, ListingStatus.SOLD);
        insertListing(seller, brand, ListingStatus.ARCHIVED);

        mockMvc.perform(get("/api/listings").param("brand", brand.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active));
    }

    @Test
    void unknownSortFieldIs400() throws Exception
    {
        mockMvc.perform(get("/api/listings").param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нельзя сортировать по полю: foo"));
    }

    @Test
    void sortBySellerPasswordHashIs400() throws Exception
    {
        mockMvc.perform(get("/api/listings").param("sort", "seller.passwordHash"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cardIsCachedInRedis() throws Exception
    {
        Long id = insertListing(createUser(Role.USER), uniqueBrand(), ListingStatus.ACTIVE);

        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey("listings::" + id)).isTrue();
    }

    @Test
    void priceOfHiddenListingIs404WithoutCallingCbr() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long draft = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        Long archived = insertListing(seller, uniqueBrand(), ListingStatus.ARCHIVED);

        // У обоих есть цена, так что 404 только из-за статуса. Адрес ЦБ в тестах localhost:1:
        // если бы сервис пошёл к ЦБ, ответ был бы 503
        mockMvc.perform(get("/api/listings/" + draft + "/price"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Объявление с id: " + draft + " не найдено"));
        mockMvc.perform(get("/api/listings/" + archived + "/price"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Объявление с id: " + archived + " не найдено"));
    }

    @Test
    void createResponseMatchesStoredListing() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String created = mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra(uniqueBrand()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ListingDto posted = objectMapper.readValue(created, ListingDto.class);

        String read = mockMvc.perform(get("/api/listings/" + posted.id()).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readValue(read, ListingDto.class)).isEqualTo(posted);
    }

    @Test
    void sortWithIgnoreCaseOnNumberIsNot500() throws Exception
    {
        mockMvc.perform(get("/api/listings").param("sort", "price,desc,ignorecase"))
                .andExpect(status().isOk());
    }

    @Test
    void hugePageNumberIs400() throws Exception
    {
        mockMvc.perform(get("/api/listings").param("page", "30000000").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Слишком большой номер страницы"));
    }

    @Test
    void publishedEventReachesKafkaListener()
    {
        ListingPublishedEvent event = new ListingPublishedEvent(777L, 1L, "Toyota", "Supra", new BigDecimal("4500000"), Instant.now());

        kafkaTemplate.send(KafkaTopicsConfig.LISTING_PUBLISHED, event.listingId(), event);

        verify(notificationsListener, timeout(15000))
                .onListingPublished(argThat((ListingPublishedEvent received) -> received.listingId().equals(777L)));
    }

    @Test
    void prometheusExposesListingCounters() throws Exception
    {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("listings_added_total")))
                .andExpect(content().string(containsString("cache_gets_total{cache=\"listings\"")));
    }

    private ListingRequest supra(String brand)
    {
        return new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4500000"), "Самара", "Один владелец");
    }
}
