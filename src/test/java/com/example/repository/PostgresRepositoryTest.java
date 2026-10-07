package com.example.repository;

import com.example.model.AppUser;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingStatus;
import com.example.model.Role;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
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
    void brandIndexUsesUpperLikeHibernate()
    {
        List<String> indexes = entityManager.getEntityManager()
                .createNativeQuery("select indexname from pg_indexes where tablename = 'listings'")
                .getResultList().stream().map(Object::toString).toList();

        assertThat(indexes).contains("idx_listings_brand_upper", "idx_listings_seller");
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

        assertThat(listingRepository.findByStatusAndBrandIgnoreCase(ListingStatus.ACTIVE, "toyota", PageRequest.of(0, 10)).getContent())
                .extracting(Listing::getModel)
                .containsExactly("Supra");
    }

    @Test
    void pagesAreSortedAndCounted()
    {
        listingRepository.save(active("Toyota", "Supra", 320, 1998));
        listingRepository.save(active("BMW", "M4", 510, 2024));
        listingRepository.save(active("Lada", "Niva", 83, 2020));
        listingRepository.save(draft("Kia", "Rio", 123, 2019));
        entityManager.flush();

        Page<Listing> page = listingRepository.findByStatus(ListingStatus.ACTIVE, PageRequest.of(0, 2, Sort.by("year")));

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

    private Listing draft(String brand, String model, int horsePower, int year)
    {
        return Listing.draft(seller, new ListingDetails(brand, model, null, horsePower, year, 50000, new BigDecimal("8500000"), "Самара", null), Instant.now());
    }

    private Listing draftWithoutPrice()
    {
        return Listing.draft(seller, new ListingDetails("Lada", "Niva", null, 83, 2020, 50000, null, "Самара", null), Instant.now());
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
