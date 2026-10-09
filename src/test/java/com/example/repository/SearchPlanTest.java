package com.example.repository;

import com.example.model.AppUser;
import com.example.model.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// План полнотекстового поиска на данных со статистикой. Отдельный контейнер: ANALYZE пишет reltuples
// в pg_class мимо транзакции, откат теста его не вернёт, и планы в PostgresRepositoryTest поехали бы.
// На пустой таблице дешевле любой индекс, даже idx_listings_brand_feed целиком (он тоже только ACTIVE),
// поэтому 5000 строк и редкое слово: GIN выбирается по селективности
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SearchPlanTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private TestEntityManager entityManager;

    // SQL из show-sql для GET /api/listings?q=... Функции из FullTextFunctions разворачиваются
    // в search_vector @@ websearch_to_tsquery, и под это условие подходит частичный GIN
    @Test
    void rareWordSearchUsesGinIndex()
    {
        AppUser seller = userRepository.save(new AppUser("seller", "!", Role.USER));
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("insert into listings (seller_id, status, brand, model, horse_power, year, price, city, description, "
                        + "created_at, updated_at, published_at, version) "
                        + "select ?1, 'ACTIVE', 'Lada', 'Vesta', 106, 2020, 1000000, 'Самара', 'Обычное описание ' || n, now(), now(), now(), 0 "
                        + "from generate_series(1, 5000) as n")
                .setParameter(1, seller.getId()).executeUpdate();
        em.createNativeQuery("update listings set description = 'Редкая комплектация' where id = (select max(id) from listings)").executeUpdate();
        em.createNativeQuery("analyze listings").executeUpdate();
        // Новые строки GIN сначала копит в pending list (fastupdate) и читает его целиком при каждом поиске.
        // Планировщик это учитывает, и с 5000 строк в списке seq scan дешевле. В работе список сливает autovacuum
        em.createNativeQuery("select gin_clean_pending_list('idx_listings_search')").getSingleResult();

        String plan = explain("select l1_0.id from listings l1_0 where l1_0.status=?1 "
                + "and (l1_0.search_vector @@ websearch_to_tsquery('russian', ?2)) "
                + "order by ts_rank(l1_0.search_vector, websearch_to_tsquery('russian', ?3)) desc,l1_0.published_at desc,l1_0.id desc "
                + "offset 0 rows fetch first 20 rows only", "ACTIVE", "редкая комплектация", "редкая комплектация");

        assertThat(plan).contains("Bitmap Index Scan on idx_listings_search");
    }

    private String explain(String sql, Object... parameters)
    {
        Query query = entityManager.getEntityManager().createNativeQuery("explain " + sql);
        for (int i = 0; i < parameters.length; i++)
        {
            query.setParameter(i + 1, parameters[i]);
        }
        List<?> plan = query.getResultList();
        return plan.stream().map(Object::toString).collect(Collectors.joining("\n"));
    }
}
