package com.example;

import com.example.dto.ListingDto;
import com.example.dto.ListingRequest;
import com.example.dto.ListingUpdateRequest;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.dto.RegisterRequest;
import com.example.dto.UserDto;
import com.example.exception.UsernameTakenException;
import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.model.Transmission;
import com.example.service.AuthService;
import com.example.service.ListingService;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GarageApplicationTests extends IntegrationTest {
    @Autowired
    ListingService listingService;
    @Autowired
    AuthService authService;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    ApplicationEventPublisher applicationEvents;
    @Autowired
    MeterRegistry meterRegistry;

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
    void registeredUserLogsInAndSellsCar() throws Exception
    {
        String username = "r" + uniqueBrand().toLowerCase();
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readValue(body, UserDto.class).id();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, "another-password"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Логин уже занят"));

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(login, LoginResponse.class).token();
        assertThat(jwtService.extractRoles(token)).containsExactly("ROLE_USER");

        mockMvc.perform(post("/api/listings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supra(uniqueBrand()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(userId));
    }

    // Две регистрации одного логина одновременно: обе проходят existsByUsername, базу пишет одна,
    // вторая упирается в UNIQUE и должна получить 409, а не 500
    @Test
    void concurrentRegistrationGivesExactlyOneUser() throws Exception
    {
        for (int round = 0; round < 5; round++)
        {
            String username = "race" + round + uniqueBrand().toLowerCase();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++)
            {
                results.add(pool.submit(() -> {
                    start.await();
                    try
                    {
                        authService.register(new RegisterRequest(username, "correct-horse-battery"));
                        return "ok";
                    }
                    catch (UsernameTakenException e)
                    {
                        return "taken";
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results)
            {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();

            assertThat(outcomes).containsExactlyInAnyOrder("ok", "taken");
            assertThat(jdbcTemplate.queryForObject("select count(*) from users where username = ?", Long.class, username)).isEqualTo(1);
        }
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

    // Характеристики проходят через POST, базу и GET как строки enum и стираются PUT без них
    @Test
    void specsGoThroughApi() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long id = create(seller, new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000,
                FuelType.PETROL, Transmission.MANUAL, BodyType.COUPE, new BigDecimal("4500000"), "Самара", null));

        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelType").value("PETROL"))
                .andExpect(jsonPath("$.transmission").value("MANUAL"))
                .andExpect(jsonPath("$.bodyType").value("COUPE"));
        assertThat(jdbcTemplate.queryForObject("select fuel_type from listings where id = ?", String.class, id)).isEqualTo("PETROL");

        edit(id, seller, new ListingUpdateRequest(0L, brand, "Supra", "2JZ", 320, 1998, 154000,
                FuelType.DIESEL, Transmission.AUTOMATIC, BodyType.SEDAN, new BigDecimal("4500000"), "Самара", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelType").value("DIESEL"))
                .andExpect(jsonPath("$.transmission").value("AUTOMATIC"))
                .andExpect(jsonPath("$.bodyType").value("SEDAN"));
        assertThat(jdbcTemplate.queryForObject("select body_type from listings where id = ?", String.class, id)).isEqualTo("SEDAN");

        edit(id, seller, new ListingUpdateRequest(1L, brand, "Supra", "2JZ", 320, 1998, 154000, null, null, null, new BigDecimal("4500000"), "Самара", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelType").isEmpty())
                .andExpect(jsonPath("$.transmission").isEmpty())
                .andExpect(jsonPath("$.bodyType").isEmpty());
    }

    @Test
    void listingLifecycleThroughApi() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        String brand = uniqueBrand();
        Long withoutPrice = create(seller, new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000, null, null, null, null, "Самара", null));
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
    void editWithClientVersion() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        String brand = uniqueBrand();
        Long id = create(seller, supra(brand));
        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(seller))).andExpect(status().isOk());

        edit(id, seller, new ListingUpdateRequest(0L, brand, "Supra", "2JZ", 330, 1998, 160000, null, null, null, new BigDecimal("3900000"), "Тольятти", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.price").value(3900000.00))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        // кэш выкинут: GET показывает новую цену, а не ту, что лежала в Redis
        mockMvc.perform(get("/api/listings/" + id).header("Authorization", bearer(seller)))
                .andExpect(jsonPath("$.city").value("Тольятти"));

        // вторая вкладка с формой, открытой до правки
        edit(id, seller, new ListingUpdateRequest(0L, brand, "Supra", "2JZ", 330, 1998, 160000, null, null, null, new BigDecimal("1000000"), "Самара", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Объявление уже изменили, актуальная версия 1. Обновите и повторите"));

        change(id, "publish", seller).andExpect(status().isOk());
        edit(id, seller, new ListingUpdateRequest(2L, brand, "Supra", "2JZ", 330, 1998, 160000, null, null, null, null, "Самара", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("У опубликованного объявления должны быть цена и город"));
        edit(id, stranger, new ListingUpdateRequest(2L, brand, "Supra", "2JZ", 330, 1998, 160000, null, null, null, new BigDecimal("1"), "Самара", null))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/listings/" + id))
                .andExpect(jsonPath("$.price").value(3900000.00))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void adminSeesAllStatusesWithSellerUsername() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long archived = insertListing(seller, uniqueBrand(), ListingStatus.ARCHIVED);

        mockMvc.perform(get("/api/admin/listings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/listings").header("Authorization", bearer(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Недостаточно прав"));
        // база общая для всех тестов: ищем своё объявление по id, а не по позиции
        mockMvc.perform(get("/api/admin/listings").header("Authorization", bearer(admin()))
                        .param("status", "ARCHIVED").param("size", "100").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.listing.id == " + archived + ")].sellerUsername").value(seller.getUsername()));
    }

    // Второй уровень: даже если URL-правило забудут, сервис сам не пустит не-админа
    @Test
    @WithMockUser(roles = "USER")
    void adminServiceMethodIsProtected()
    {
        assertThatThrownBy(() -> listingService.findAllForAdmin(null, PageRequest.of(0, 10)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void myListingsBelongOnlyToMe() throws Exception
    {
        AppUser first = createUser(Role.USER);
        AppUser second = createUser(Role.USER);
        Long firstDraft = insertListing(first, uniqueBrand(), ListingStatus.DRAFT);
        Long firstActive = insertListing(first, uniqueBrand(), ListingStatus.ACTIVE);
        Long secondDraft = insertListing(second, uniqueBrand(), ListingStatus.DRAFT);

        mockMvc.perform(get("/api/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.username").value(first.getUsername()));
        mockMvc.perform(get("/api/me/listings").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(firstDraft.intValue(), firstActive.intValue())));
        mockMvc.perform(get("/api/me/listings").param("status", "DRAFT").header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(firstDraft));
        mockMvc.perform(get("/api/me/listings").header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(secondDraft));
        mockMvc.perform(get("/api/me/listings")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlySellerDeletesOnlyDraft() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        Long draft = create(seller, supra(uniqueBrand()));
        mockMvc.perform(get("/api/listings/" + draft).header("Authorization", bearer(seller))).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey("listings::" + draft)).isTrue();

        mockMvc.perform(delete("/api/listings/" + draft).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/listings/" + draft).header("Authorization", bearer(admin()))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/listings/" + draft).header("Authorization", bearer(seller))).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey("listings::" + draft)).isFalse();
        mockMvc.perform(get("/api/listings/" + draft).header("Authorization", bearer(seller))).andExpect(status().isNotFound());

        Long active = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        mockMvc.perform(delete("/api/listings/" + active).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/listings/" + active).header("Authorization", bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Удалить можно только черновик. Опубликованное объявление снимите в архив"));
        mockMvc.perform(get("/api/listings/" + active)).andExpect(status().isOk());
    }

    // Откаченная публикация не уходит в Kafka и не попадает в счётчик
    @Test
    void rolledBackPublicationIsNotSent()
    {
        double before = meterRegistry.get("listings.published").counter().count();
        ListingPublishedEvent event = event(910001L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            applicationEvents.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(notificationsListener, after(3000).never())
                .onListingPublished(argThat((ListingPublishedEvent received) -> received.listingId().equals(910001L)));
        assertThat(meterRegistry.get("listings.published").counter().count()).isEqualTo(before);
    }

    @Test
    void committedPublicationIsSent()
    {
        ListingPublishedEvent event = event(910002L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> applicationEvents.publishEvent(event));

        verify(notificationsListener, timeout(15000))
                .onListingPublished(argThat((ListingPublishedEvent received) -> received.listingId().equals(910002L)));
    }

    // fallbackExecution: без транзакции событие тоже уходит, а не теряется молча
    @Test
    void publicationOutsideTransactionIsSent()
    {
        applicationEvents.publishEvent(event(910003L));

        verify(notificationsListener, timeout(15000))
                .onListingPublished(argThat((ListingPublishedEvent received) -> received.listingId().equals(910003L)));
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
                .andExpect(content().string(containsString("listings_events_failed_total")))
                .andExpect(content().string(containsString("cache_gets_total{cache=\"listings\"")));
    }

    private ListingPublishedEvent event(Long listingId)
    {
        return new ListingPublishedEvent(listingId, 1L, "Toyota", "Supra", new BigDecimal("4500000.00"), Instant.now());
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

    private ResultActions edit(Long id, AppUser actor, ListingUpdateRequest request) throws Exception
    {
        return mockMvc.perform(put("/api/listings/" + id)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions change(Long id, String action, AppUser actor) throws Exception
    {
        return mockMvc.perform(post("/api/listings/" + id + "/" + action).header("Authorization", bearer(actor)));
    }

    private ListingRequest supra(String brand)
    {
        return new ListingRequest(brand, "Supra", "2JZ", 320, 1998, 154000, null, null, null, new BigDecimal("4500000"), "Самара", "Один владелец");
    }
}
