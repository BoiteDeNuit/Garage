package com.example.repository;

import com.example.dto.ListingSearchCriteria;
import com.example.model.AppUser;
import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.model.SavedSearch;
import com.example.model.Transmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// SQL сопоставления сохранённых поисков и фильтр ленты — две реализации одних условий: jsonb в SQL и Specification.
// Для каждого набора фильтров и каждого объявления они должны ответить одинаково
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SavedSearchMatchTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    private final ObjectMapper json = JsonMapper.builder().build();
    @Autowired
    private ListingRepository listings;
    @Autowired
    private SavedSearchRepository searches;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TestEntityManager entityManager;
    private AppUser seller;
    private AppUser buyer;
    private List<Listing> catalog;

    @BeforeEach
    void setUp()
    {
        seller = users.save(new AppUser("seller", "!", Role.USER));
        buyer = users.save(new AppUser("buyer", "!", Role.USER));
        catalog = List.of(
                active("Toyota", "Supra", 1998, "4500000", 154000, "Самара", FuelType.PETROL, Transmission.MANUAL, BodyType.COUPE, "Один владелец, небольшой пробег"),
                active("Toyota", "Camry", 2015, "2000000", 90000, "Тольятти", FuelType.HYBRID, Transmission.CVT, BodyType.SEDAN, "Зимняя резина в подарок"),
                active("Lada", "Niva", 2020, "1200000", null, "самара", FuelType.PETROL, Transmission.MANUAL, BodyType.SUV, null),
                active("BMW", "X5", 2010, "3000000", 250000, "Москва", FuelType.DIESEL, Transmission.AUTOMATIC, BodyType.SUV, "После ДТП, восстановлена"));
        Listing draft = listings.save(Listing.draft(seller, details("Toyota", "Supra", 1998, "4500000", 1000, "Самара", null, null, null, null), Instant.now()));
        entityManager.flush();
        catalog = Stream.concat(catalog.stream(), Stream.of(draft)).toList();
    }

    static Stream<Arguments> criteria()
    {
        return Stream.of(
                Arguments.of(criteria(null, "TOYOTA", null, null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria(null, " toyota ", "supra", null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria(null, "  ", null, 2015, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria(null, null, null, 1998, 2015, null, null, null, null, null, null, null)),
                Arguments.of(criteria(null, null, null, null, null, new BigDecimal("2000000"), new BigDecimal("3000000"), null, null, null, null, null)),
                Arguments.of(criteria(null, null, null, null, null, null, new BigDecimal("1999999.99"), null, null, null, null, null)),
                Arguments.of(criteria(null, null, null, null, null, null, null, 154000, null, null, null, null)),
                Arguments.of(criteria(null, null, null, null, null, null, null, null, "САМАРА", null, null, null)),
                Arguments.of(criteria(null, null, null, null, null, null, null, null, null, FuelType.PETROL, Transmission.MANUAL, null)),
                Arguments.of(criteria(null, null, null, null, null, null, null, null, null, null, null, BodyType.SUV)),
                Arguments.of(criteria("небольшим пробегом", null, null, null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria("резина -дтп", null, null, null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria("camry or x5", null, null, null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria("и на", null, null, null, null, null, null, null, null, null, null, null)),
                Arguments.of(criteria(null, "volvo", null, null, null, null, null, null, null, null, null, null)));
    }

    @ParameterizedTest
    @MethodSource("criteria")
    void sqlMatchAgreesWithFeedFilter(ListingSearchCriteria criteria)
    {
        SavedSearch search = searches.save(new SavedSearch(buyer.getId(), "поиск", json.writeValueAsString(criteria), Instant.now()));
        entityManager.flush();
        List<Long> inFeed = listings.findAll(ListingSpecifications.publicFeed(criteria)).stream().map(Listing::getId).toList();

        for (Listing listing : catalog)
        {
            boolean matched = searches.findMatching(listing.getId()).stream().anyMatch(match -> match.getSearchId().equals(search.getId()));
            assertThat(matched).as("%s и %s %s", criteria, listing.getBrand(), listing.getModel()).isEqualTo(inFeed.contains(listing.getId()));
        }
    }

    // Свой поиск продавца на своё же объявление не срабатывает: о собственной публикации не сообщаем
    @ParameterizedTest
    @MethodSource("criteria")
    void sellerIsNotNotifiedAboutOwnListing(ListingSearchCriteria criteria)
    {
        SavedSearch own = searches.save(new SavedSearch(seller.getId(), "свой", json.writeValueAsString(criteria), Instant.now()));
        entityManager.flush();

        for (Listing listing : catalog)
        {
            assertThat(searches.findMatching(listing.getId())).noneMatch(match -> match.getSearchId().equals(own.getId()));
        }
    }

    private Listing active(String brand, String model, int year, String price, Integer mileage, String city,
                           FuelType fuel, Transmission transmission, BodyType body, String description)
    {
        Listing listing = Listing.draft(seller, details(brand, model, year, price, mileage, city, fuel, transmission, body, description), Instant.now());
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ACTIVE);
        ReflectionTestUtils.setField(listing, "publishedAt", Instant.now());
        return listings.save(listing);
    }

    private static ListingDetails details(String brand, String model, int year, String price, Integer mileage, String city,
                                          FuelType fuel, Transmission transmission, BodyType body, String description)
    {
        return new ListingDetails(brand, model, null, 200, year, mileage, fuel, transmission, body, new BigDecimal(price), city, description);
    }

    private static ListingSearchCriteria criteria(String q, String brand, String model, Integer yearFrom, Integer yearTo,
                                                  BigDecimal priceFrom, BigDecimal priceTo, Integer mileageTo, String city,
                                                  FuelType fuel, Transmission transmission, BodyType body)
    {
        return new ListingSearchCriteria(q, brand, model, yearFrom, yearTo, priceFrom, priceTo, mileageTo, city, fuel, transmission, body);
    }
}
