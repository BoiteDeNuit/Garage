package com.example;

import com.example.dto.ListingDto;
import com.example.dto.ListingRequest;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.service.ListingService;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

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
    ListingService listingService;

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
    void listingLifecycleThroughApi() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        String brand = uniqueBrand();
        Long withoutPrice = create(seller, new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000, null, "Самара", null));
        change(withoutPrice, "publish", seller)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Для публикации нужна цена"));

        Long id = create(seller, supra(brand));
        change(id, "publish", stranger).andExpect(status().isNotFound());
        change(id, "publish", seller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isOk());

        change(id, "sold", stranger)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Недостаточно прав"));
        change(id, "publish", seller)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя перевести объявление из ACTIVE в ACTIVE"));
        change(id, "sold", admin()).andExpect(status().isForbidden());
        change(id, "archive", stranger).andExpect(status().isForbidden());
        change(id, "archive", admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isNotFound());

        change(id, "publish", seller).andExpect(status().isOk());
        change(id, "sold", seller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
        change(id, "archive", seller).andExpect(status().isConflict());
        mockMvc.perform(post("/api/listings/" + id + "/archive")).andExpect(status().isUnauthorized());

        // две публикации: первая и повторная из архива
        verify(notificationsListener, timeout(15000).times(2))
                .onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(id)));
    }

    @Test
    void archiveEvictsCachedCard() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long id = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey("listings::" + id)).isTrue();

        change(id, "archive", seller).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey("listings::" + id)).isFalse();
        mockMvc.perform(get("/api/listings/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void publishAndSoldEvictCachedCard() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long archived = insertListing(seller, uniqueBrand(), ListingStatus.ARCHIVED);
        mockMvc.perform(get("/api/listings/" + archived)).andExpect(status().isNotFound());
        assertThat(redisTemplate.hasKey("listings::" + archived)).isTrue();

        change(archived, "publish", seller).andExpect(status().isOk());

        mockMvc.perform(get("/api/listings/" + archived))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        change(archived, "sold", seller).andExpect(status().isOk());
        mockMvc.perform(get("/api/listings/" + archived))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
    }

    // Порядок задан явно, а не держится на том, в каком порядке Spring зарегистрировал конфигурации.
    // Меньший order — внешний прокси: кэш снаружи транзакции, evict выполняется уже после коммита
    @Test
    void cacheProxyWrapsTransaction()
    {
        List<Advisor> advisors = Arrays.asList(((Advised) listingService).getAdvisors());
        Advisor cache = advisors.stream().filter(BeanFactoryCacheOperationSourceAdvisor.class::isInstance).findFirst().orElseThrow();
        Advisor transaction = advisors.stream().filter(BeanFactoryTransactionAttributeSourceAdvisor.class::isInstance).findFirst().orElseThrow();

        assertThat(((Ordered) cache).getOrder()).isLessThan(((Ordered) transaction).getOrder());
        assertThat(advisors.indexOf(cache)).isLessThan(advisors.indexOf(transaction));
    }

    @Test
    void prometheusExposesListingCounters() throws Exception
    {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("listings_added_total")))
                .andExpect(content().string(containsString("listings_published_total")))
                .andExpect(content().string(containsString("cache_gets_total{cache=\"listings\"")));
    }

    private Long create(AppUser seller, ListingRequest request) throws Exception
    {
        String body = mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ListingDto.class).id();
    }

    private ResultActions change(Long id, String action, AppUser actor) throws Exception
    {
        return mockMvc.perform(post("/api/listings/" + id + "/" + action).header("Authorization", bearer(actor)));
    }

    private ListingRequest supra(String brand)
    {
        return new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000, new BigDecimal("4500000"), "Самара", "Один владелец");
    }
}
