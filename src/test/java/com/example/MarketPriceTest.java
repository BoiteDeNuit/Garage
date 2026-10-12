package com.example;

import com.example.job.MarketPriceRefresh;
import com.example.model.AppUser;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketPriceTest extends IntegrationTest {
    @Autowired
    private MarketPriceRefresh marketPriceRefresh;

    // Строку блокировки не удаляем: ShedLock её не вернёт (см. CleanupJobsTest)
    @BeforeEach
    @AfterEach
    void releaseLocks()
    {
        jdbcTemplate.update("update shedlock set lock_until = now()");
    }

    // Пять опубликованных той же марки, модели и года: 1, 2, 3, 4 и 10 млн. Квартили 2, 3 и 4 млн.
    // Черновик и проданное в расчёт не входят, марка без учёта регистра
    @Test
    void quartilesOfSameCar() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long cheap = listing(seller, brand, ListingStatus.ACTIVE, 1_000_000);
        listing(seller, brand.toLowerCase(), ListingStatus.ACTIVE, 2_000_000);
        Long fair = listing(seller, brand, ListingStatus.ACTIVE, 3_000_000);
        listing(seller, brand, ListingStatus.ACTIVE, 4_000_000);
        Long dear = listing(seller, brand, ListingStatus.ACTIVE, 10_000_000);
        listing(seller, brand, ListingStatus.DRAFT, 100);
        listing(seller, brand, ListingStatus.SOLD, 100);

        marketPriceRefresh.run();

        mockMvc.perform(get("/api/listings/{id}/market-price", fair))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("IN_MARKET"))
                .andExpect(jsonPath("$.comparable").value(5))
                .andExpect(jsonPath("$.p25").value(2000000.0))
                .andExpect(jsonPath("$.median").value(3000000.0))
                .andExpect(jsonPath("$.p75").value(4000000.0))
                .andExpect(jsonPath("$.deltaPercent").value(0.0))
                .andExpect(jsonPath("$.calculatedAt").isNotEmpty());
        mockMvc.perform(get("/api/listings/{id}/market-price", cheap))
                .andExpect(jsonPath("$.verdict").value("BELOW_MARKET"))
                .andExpect(jsonPath("$.deltaPercent").value(-66.7));
        mockMvc.perform(get("/api/listings/{id}/market-price", dear))
                .andExpect(jsonPath("$.verdict").value("ABOVE_MARKET"))
                .andExpect(jsonPath("$.deltaPercent").value(233.3));
    }

    // Четыре похожих — оценки нет. Черновик не оценивается совсем: 404, как цена в валюте
    @Test
    void fewComparablesGiveNoEstimate() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long id = listing(seller, brand, ListingStatus.ACTIVE, 1_000_000);
        for(int i = 0; i < 3; i++)
        {
            listing(seller, brand, ListingStatus.ACTIVE, 2_000_000);
        }
        Long draft = listing(seller, brand, ListingStatus.DRAFT, 1_000_000);

        marketPriceRefresh.run();

        mockMvc.perform(get("/api/listings/{id}/market-price", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("NOT_ENOUGH_DATA"))
                .andExpect(jsonPath("$.median").doesNotExist());
        mockMvc.perform(get("/api/listings/{id}/market-price", draft)).andExpect(status().isNotFound());
    }

    // Свежая база: представление создано WITH NO DATA и ещё не посчитано. Оценки нет, но и 500 нет.
    // Первый расчёт делает populateIfEmpty при старте приложения, а не ближайший запуск по расписанию
    @Test
    void unpopulatedViewGivesNoEstimateUntilFirstRefresh() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        String brand = uniqueBrand();
        Long id = listing(seller, brand, ListingStatus.ACTIVE, 1_000_000);
        for(int i = 0; i < 4; i++)
        {
            listing(seller, brand, ListingStatus.ACTIVE, 2_000_000);
        }
        jdbcTemplate.execute("refresh materialized view market_prices with no data");
        try
        {
            mockMvc.perform(get("/api/listings/{id}/market-price", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verdict").value("NOT_ENOUGH_DATA"));

            marketPriceRefresh.populateIfEmpty();

            mockMvc.perform(get("/api/listings/{id}/market-price", id))
                    .andExpect(jsonPath("$.verdict").value("BELOW_MARKET"));
        }
        finally
        {
            releaseLocks();
            marketPriceRefresh.run();
        }
    }

    private Long listing(AppUser seller, String brand, ListingStatus status, long price)
    {
        Long id = insertListing(seller, brand, status);
        jdbcTemplate.update("update listings set price = ? where id = ?", price, id);
        return id;
    }
}
