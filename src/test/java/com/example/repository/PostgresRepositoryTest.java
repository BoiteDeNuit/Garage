package com.example.repository;

import com.example.model.AppUser;
import com.example.model.BodyType;
import com.example.model.FuelType;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.model.Transmission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.repository.ListingSpecifications.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;
    private AppUser seller;

    @BeforeEach
    void setUp()
    {
        seller = userRepository.save(new AppUser("seller", "!", Role.USER));
    }

    @Test
    void migrationsRemoveDefaultAdmin()
    {
        assertThat(userRepository.findByUsername("admin")).isEmpty();
    }

    @Test
    void emptyDatabaseHasNoLegacySeller()
    {
        assertThat(userRepository.findByUsername("garage-legacy")).isEmpty();
    }

    @Test
    void userWithoutRoleIsRejected()
    {
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into users (username, password_hash) values ('ghost', 'x')")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("role");
    }

    @Test
    void userWithUnknownRoleIsRejected()
    {
        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into users (username, password_hash, role) values ('ghost', 'x', 'SUPERUSER')")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_users_role");
    }

    @Test
    void feedIndexesReplaceOldBrandIndex()
    {
        List<String> indexes = entityManager.getEntityManager()
                .createNativeQuery("select indexname from pg_indexes where tablename = 'listings'")
                .getResultList().stream().map(Object::toString).toList();

        assertThat(indexes).contains("idx_listings_feed", "idx_listings_brand_feed", "idx_listings_seller");
        assertThat(indexes).doesNotContain("idx_listings_brand_upper");
    }

    // Частичный индекс подходит, только если условие запроса совпадает с его WHERE, а выражение — с колонкой индекса.
    // SQL взят из show-sql, статус уходит параметром, как у Hibernate. На пустой таблице планировщику дешевле
    // прочитать её целиком, поэтому seq scan выключен: проверяется, что индекс подходит к форме запроса
    @Test
    void feedQueryCanUseFeedIndex()
    {
        assertThat(explain("select l1_0.id from listings l1_0 where l1_0.status=?1 "
                + "order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only", "ACTIVE"))
                .contains("idx_listings_feed")
                .doesNotContain("Sort");
    }

    @Test
    void brandQueryCanUseBrandFeedIndex()
    {
        assertThat(explain("select l1_0.id from listings l1_0 where l1_0.status=?1 and upper(l1_0.brand)=upper(?2) "
                + "order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only", "ACTIVE", "toyota"))
                .contains("idx_listings_brand_feed")
                .doesNotContain("Sort");
    }

    @Test
    void savesAndReadsDraft()
    {
        Long id = listingRepository.save(draft("Porsche", "Taycan", 700, 2024)).getId();
        entityManager.flush();
        entityManager.clear();

        Listing found = listingRepository.findById(id).orElseThrow();
        assertThat(found.getBrand()).isEqualTo("Porsche");
        assertThat(found.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(found.getPrice()).isEqualByComparingTo("8500000.00");
        assertThat(found.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void versionGrowsOnChange()
    {
        Listing listing = listingRepository.save(draft("Lada", "Niva", 83, 2020));
        entityManager.flush();

        ReflectionTestUtils.setField(listing, "price", new BigDecimal("900000"));
        entityManager.flush();

        assertThat(listing.getVersion()).isEqualTo(1L);
    }

    @Test
    void findsActiveBrandIgnoringCase()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        listingRepository.save(draft("Toyota", "Chaser", 280, 1998));
        listingRepository.save(active("BMW", "M4", 510, 2024));
        entityManager.flush();

        assertThat(search(hasStatus(ListingStatus.ACTIVE), brand("toyota")))
                .extracting(Listing::getModel)
                .containsExactly("Supra");
    }

    // Регистр меняет Postgres с обеих сторон. В Java "Straße".toUpperCase() = "STRASSE", а upper() базы даёт "STRAßE":
    // сравнение с Java-версией строку бы не нашло
    @Test
    void caseIsFoldedByDatabaseOnBothSides()
    {
        listingRepository.save(Listing.draft(seller, new ListingDetails("Lada", "Niva", null, 83, 2020, 50000, null, null, null, null, "Straße", null), Instant.now()));
        entityManager.flush();

        assertThat(search(city("straße"))).extracting(Listing::getCity).containsExactly("Straße");
    }

    // Пустая строка и пробелы — не фильтр, а не поиск марки ""
    @Test
    void blankTextFilterIsIgnored()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        entityManager.flush();

        assertThat(search(hasStatus(ListingStatus.ACTIVE), brand("  "), model(""), city(null))).hasSize(1);
    }

    // Границы включаются с обеих сторон. Черновик без цены под ценовой фильтр не попадает: null не больше и не меньше
    @Test
    void rangesIncludeBoundsAndSkipNulls()
    {
        listingRepository.save(priced("Supra", 1998, "4500000", 154000));
        listingRepository.save(priced("Chaser", 2001, "1200000", 250000));
        listingRepository.save(priced("Camry", 2015, "2000000", 90000));
        listingRepository.save(draftWithoutPrice());
        entityManager.flush();

        assertThat(search(yearBetween(1998, 2001))).extracting(Listing::getModel).containsExactlyInAnyOrder("Supra", "Chaser");
        assertThat(search(yearBetween(2001, null))).extracting(Listing::getModel).containsExactlyInAnyOrder("Chaser", "Camry", "Niva");
        assertThat(search(priceBetween(new BigDecimal("1200000"), new BigDecimal("2000000"))))
                .extracting(Listing::getModel).containsExactlyInAnyOrder("Chaser", "Camry");
        assertThat(search(priceBetween(null, new BigDecimal("1999999.99")))).extracting(Listing::getModel).containsExactly("Chaser");
        assertThat(search(mileageAtMost(154000))).extracting(Listing::getModel).containsExactlyInAnyOrder("Supra", "Camry", "Niva");
    }

    @Test
    void textAndEnumFiltersMatch()
    {
        listingRepository.save(Listing.draft(seller, new ListingDetails("Toyota", "Supra", null, 320, 1998, 154000,
                FuelType.PETROL, Transmission.MANUAL, BodyType.COUPE, new BigDecimal("4500000"), "Самара", null), Instant.now()));
        listingRepository.save(Listing.draft(seller, new ListingDetails("Toyota", "Camry", null, 218, 2015, 90000,
                FuelType.HYBRID, Transmission.CVT, BodyType.SEDAN, new BigDecimal("2000000"), "Самара", null), Instant.now()));
        listingRepository.save(Listing.draft(seller, new ListingDetails("Toyota", "Chaser", null, 280, 2001, 250000,
                FuelType.DIESEL, Transmission.AUTOMATIC, BodyType.SEDAN, new BigDecimal("1200000"), "Тольятти", null), Instant.now()));
        entityManager.flush();

        assertThat(search(city("САМАРА"))).extracting(Listing::getModel).containsExactlyInAnyOrder("Supra", "Camry");
        assertThat(search(brand(" toyota "))).hasSize(3);
        assertThat(search(model("supra"))).extracting(Listing::getModel).containsExactly("Supra");
        assertThat(search(fuelType(FuelType.DIESEL))).extracting(Listing::getModel).containsExactly("Chaser");
        assertThat(search(transmission(Transmission.CVT))).extracting(Listing::getModel).containsExactly("Camry");
        assertThat(search(bodyType(BodyType.SEDAN), city("самара"))).extracting(Listing::getModel).containsExactly("Camry");
    }

    @Test
    void pagesAreSortedAndCounted()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        listingRepository.save(active("BMW", "M4", 510, 2024));
        listingRepository.save(active("Lada", "Niva", 83, 2020));
        listingRepository.save(draft("Kia", "Rio", 123, 2019));
        entityManager.flush();

        Page<Listing> page = listingRepository.findAll(hasStatus(ListingStatus.ACTIVE), PageRequest.of(0, 2, Sort.by("year")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Listing::getYear)
                .containsExactly(1998, 2020);
    }

    @Test
    void aggregatesCountOnlyActive()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        listingRepository.save(active("Lada", "Niva", 83, 2020));
        listingRepository.save(draft("Porsche", "Taycan", 700, 2024));
        entityManager.flush();

        assertThat(listingRepository.countByStatus(ListingStatus.ACTIVE)).isEqualTo(2);
        assertThat(listingRepository.averageHorsePower(ListingStatus.ACTIVE)).isCloseTo(201.5, within(0.01));
        assertThat(listingRepository.findFirstByStatusOrderByHorsePowerDesc(ListingStatus.ACTIVE)).map(Listing::getModel).contains("Supra");
    }

    @Test
    void findsListingsOfOneSeller()
    {
        AppUser other = userRepository.save(new AppUser("other", "!", Role.USER));
        listingRepository.save(draft("Toyota", "Supra", 320, 1998));
        listingRepository.save(active("BMW", "M4", 510, 2024));
        listingRepository.save(Listing.draft(other, new ListingDetails("Lada", "Niva", null, 83, 2020, 50000, null, null, null, null, null, null), Instant.now()));
        entityManager.flush();

        assertThat(listingRepository.findBySellerId(seller.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Listing::getModel)
                .containsExactlyInAnyOrder("Supra", "M4");
        assertThat(listingRepository.findBySellerIdAndStatus(seller.getId(), ListingStatus.ACTIVE, PageRequest.of(0, 10)).getContent())
                .extracting(Listing::getModel)
                .containsExactly("M4");
    }

    @Test
    void averageOfEmptyCatalogIsZero()
    {
        assertThat(listingRepository.averageHorsePower(ListingStatus.ACTIVE)).isZero();
    }

    @Test
    void activeWithoutPriceIsRejected()
    {
        Long id = listingRepository.save(draftWithoutPrice()).getId();
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("update listings set status = 'ACTIVE', published_at = now() where id = " + id)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_listings_published");
    }

    @Test
    void unknownStatusIsRejected()
    {
        Long id = listingRepository.save(draft("Lada", "Niva", 83, 2020)).getId();
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("update listings set status = 'BLOCKED' where id = " + id)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_listings_status");
    }

    // N+1: страница из 10 объявлений от 10 разных продавцов. С графом продавцы приходят тем же запросом:
    // 2 SQL (страница и count). flush + clear обязательны: иначе продавцы уже лежат в persistence context
    // и запросов не будет даже без графа, тест ничего бы не доказал
    @Test
    void adminPageLoadsSellersWithoutNPlusOne()
    {
        Statistics statistics = prepareTwentyFiveSellers();

        Page<Listing> page = listingRepository.findAllWithSeller(PageRequest.of(0, 10, Sort.by("id")));
        page.getContent().forEach(listing -> listing.getSeller().getUsername());

        assertThat(page.getContent()).hasSize(10);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    // Контроль: тот же сценарий без графа. Страница, count и по запросу на каждого продавца
    @Test
    void withoutEntityGraphItIsNPlusOne()
    {
        Statistics statistics = prepareTwentyFiveSellers();

        Page<Listing> page = listingRepository.findAll(PageRequest.of(0, 10, Sort.by("id")));
        page.getContent().forEach(listing -> listing.getSeller().getUsername());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(12);
    }

    @Test
    void adminPageFiltersByStatusWithSellers()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        listingRepository.save(draft("Lada", "Niva", 83, 2020));
        entityManager.flush();
        entityManager.clear();

        Page<Listing> page = listingRepository.findWithSellerByStatus(ListingStatus.DRAFT, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Listing::getModel).containsExactly("Niva");
        assertThat(page.getContent().get(0).getSeller().getUsername()).isEqualTo("seller");
    }

    // Списки в enum и в CHECK из V9 ведутся руками. Тест падает, если значение есть в коде, но база его не примет
    @Test
    void everySpecValueFitsDatabaseCheck()
    {
        for (FuelType fuel : FuelType.values())
        {
            listingRepository.save(withSpecs(fuel, null, null));
        }
        for (Transmission transmission : Transmission.values())
        {
            listingRepository.save(withSpecs(null, transmission, null));
        }
        for (BodyType body : BodyType.values())
        {
            listingRepository.save(withSpecs(null, null, body));
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(listingRepository.findAll())
                .extracting(Listing::getFuelType)
                .containsAll(List.of(FuelType.values()));
    }

    @ParameterizedTest
    @CsvSource({"fuel_type, chk_listings_fuel_type", "transmission, chk_listings_transmission", "body_type, chk_listings_body_type"})
    void unknownSpecIsRejected(String column, String constraint)
    {
        Long id = listingRepository.save(draft("Lada", "Niva", 83, 2020)).getId();
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("update listings set " + column + " = 'COAL' where id = " + id)
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining(constraint);
    }

    // clear между загрузками: второй findById идёт в базу и собирает новый объект, как в другом persistence context
    @Test
    void sameRowFromTwoPersistenceContextsIsOneListing()
    {
        Long id = listingRepository.save(draft("Toyota", "Supra", 320, 1998)).getId();
        entityManager.flush();
        entityManager.clear();

        Listing first = listingRepository.findById(id).orElseThrow();
        entityManager.clear();
        Listing second = listingRepository.findById(id).orElseThrow();

        assertThat(second).isNotSameAs(first);
        assertThat(second).isEqualTo(first);
        assertThat(new HashSet<>(List.of(first, second))).hasSize(1);
    }

    // Прокси от getReference с пустыми полями. Сравнение с ним не грузит строку: id прокси отдаёт через геттер.
    // В обратную сторону прокси сам инициализируется и отдаёт equals настоящему объекту
    @Test
    void listingEqualsProxyWithoutLoadingIt()
    {
        Long id = listingRepository.save(draft("Toyota", "Supra", 320, 1998)).getId();
        entityManager.flush();
        entityManager.clear();
        Listing loaded = listingRepository.findById(id).orElseThrow();
        entityManager.clear();

        Listing proxy = entityManager.getEntityManager().getReference(Listing.class, id);

        assertThat(proxy.getClass()).isNotEqualTo(Listing.class);
        assertThat(loaded.equals(proxy)).isTrue();
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        assertThat(proxy.equals(loaded)).isTrue();
    }

    // Прокси пережил свой persistence context: сессии у него нет, загрузить он уже ничего не может.
    // equals всё равно отвечает, а не бросает LazyInitializationException
    @Test
    void listingEqualsDetachedProxy()
    {
        Long id = listingRepository.save(draft("Toyota", "Supra", 320, 1998)).getId();
        entityManager.flush();
        entityManager.clear();
        Listing loaded = listingRepository.findById(id).orElseThrow();
        entityManager.clear();
        Listing proxy = entityManager.getEntityManager().getReference(Listing.class, id);
        AppUser sellerProxy = entityManager.getEntityManager().getReference(AppUser.class, seller.getId());
        entityManager.clear();

        assertThat(loaded.equals(proxy)).isTrue();
        assertThat(loaded.equals(sellerProxy)).isFalse();
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
    }

    @Test
    void listingStaysInHashSetAfterSave()
    {
        Listing listing = draft("Lada", "Niva", 83, 2020);
        Set<Listing> set = new HashSet<>();
        set.add(listing);

        listingRepository.save(listing);
        entityManager.flush();

        assertThat(listing.getId()).isNotNull();
        // set.contains, а не assertThat(set).contains: AssertJ перебирает элементы через equals и хэш не проверяет
        assertThat(set.contains(listing)).isTrue();
    }

    private Statistics prepareTwentyFiveSellers()
    {
        for (int i = 0; i < 25; i++)
        {
            AppUser owner = userRepository.save(new AppUser("owner" + i, "!", Role.USER));
            listingRepository.save(Listing.draft(owner, new ListingDetails("Lada", "Vesta", null, 106, 2021, 30000, null, null, null, null, null, null), Instant.now()));
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private Listing draft(String brand, String model, int horsePower, int year)
    {
        return Listing.draft(seller, new ListingDetails(brand, model, null, horsePower, year, 50000, null, null, null, new BigDecimal("8500000"), "Самара", null), Instant.now());
    }

    private String explain(String sql, Object... parameters)
    {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("set local enable_seqscan = off").executeUpdate();
        Query query = em.createNativeQuery("explain " + sql);
        for (int i = 0; i < parameters.length; i++)
        {
            query.setParameter(i + 1, parameters[i]);
        }
        List<?> plan = query.getResultList();
        return plan.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    @SafeVarargs
    private List<Listing> search(Specification<Listing>... filters)
    {
        return listingRepository.findAll(Specification.allOf(filters));
    }

    private Listing priced(String model, int year, String price, int mileage)
    {
        return Listing.draft(seller, new ListingDetails("Toyota", model, null, 200, year, mileage, null, null, null, new BigDecimal(price), "Самара", null), Instant.now());
    }

    private Listing withSpecs(FuelType fuel, Transmission transmission, BodyType body)
    {
        return Listing.draft(seller, new ListingDetails("Lada", "Niva", null, 83, 2020, 50000, fuel, transmission, body, null, null, null), Instant.now());
    }

    private Listing draftWithoutPrice()
    {
        return Listing.draft(seller, new ListingDetails("Lada", "Niva", null, 83, 2020, 50000, null, null, null, null, "Самара", null), Instant.now());
    }

    // Публикации в сущности пока нет: статус и дату ставим напрямую, как это сделает будущий publish
    private Listing active(String brand, String model, int horsePower, int year)
    {
        Listing listing = draft(brand, model, horsePower, year);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ACTIVE);
        ReflectionTestUtils.setField(listing, "publishedAt", Instant.now());
        return listing;
    }
}
