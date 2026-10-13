package com.example.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// V8 должна пройти не только на пустой базе (CI), но и на базе, где уже лежат машины из старого Garage.
// Свой контейнер и своя база на каждый тест: общая база тестов уже домигрирована до конца
@Testcontainers
class MigrationOnLegacyDataTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Test
    void legacyCarsBecomeArchivedListings() throws SQLException
    {
        String url = createDatabase("legacy");
        flyway(url).target("7").load().migrate();
        try (Connection connection = connect(url); Statement st = connection.createStatement())
        {
            st.execute("insert into owners (name, city) values ('Иван', 'Самара')");
            st.execute("insert into cars (brand, model, engine_code, horse_power, year, owner_id, price) " +
                    "values ('Toyota', 'Supra', '2JZ', 320, 1998, (select id from owners), 4500000)");
            st.execute("insert into cars (brand, model, engine_code, horse_power, year, price) values ('Lada', 'Niva', '21214', 83, 2020, null)");
            st.execute("insert into cars (brand, model, engine_code, horse_power, year, price) values ('BMW', 'M4', 'S58', 510, 2024, 0)");
            st.execute("insert into users (username, password_hash, role) values ('boss', 'x', 'ADMIN')");
        }

        flyway(url).load().migrate();

        try (Connection connection = connect(url); Statement st = connection.createStatement())
        {
            List<String> rows = new ArrayList<>();
            ResultSet listings = st.executeQuery(
                    "select l.id, l.status, u.username, l.price from listings l join users u on u.id = l.seller_id order by l.id");
            while (listings.next())
            {
                rows.add(listings.getLong(1) + " " + listings.getString(2) + " " + listings.getString(3) + " " + listings.getBigDecimal(4));
            }
            assertThat(rows).containsExactly(
                    "1 ARCHIVED garage-legacy 4500000.00",
                    "2 ARCHIVED garage-legacy null",
                    "3 ARCHIVED garage-legacy null");

            ResultSet legacy = st.executeQuery("select password_hash, role from users where username = 'garage-legacy'");
            assertThat(legacy.next()).isTrue();
            assertThat(legacy.getString(1)).isEqualTo("!");
            assertThat(legacy.getString(2)).isEqualTo("USER");

            ResultSet next = st.executeQuery("insert into listings (seller_id, status, brand, model, horse_power, year) " +
                    "values ((select id from users where username = 'boss'), 'DRAFT', 'Kia', 'Rio', 123, 2019) returning id");
            next.next();
            assertThat(next.getLong(1)).isEqualTo(4);

            ResultSet owners = st.executeQuery("select count(*) from owners");
            owners.next();
            assertThat(owners.getLong(1)).isEqualTo(1);
        }
    }

    // V14 собирает справочник подсказок из того, что уже публиковали. Черновик с опечаткой и старая
    // машина без даты публикации в него не попадают, пара в другом регистре — дубль
    @Test
    void modelCatalogStartsFromPublishedListings() throws SQLException
    {
        String url = createDatabase("catalog");
        flyway(url).target("13").load().migrate();
        try (Connection connection = connect(url); Statement st = connection.createStatement())
        {
            st.execute("insert into users (username, password_hash, role) values ('seller', 'x', 'USER')");
            String insert = "insert into listings (seller_id, status, brand, model, horse_power, year, price, published_at) "
                    + "values ((select id from users), '%s', '%s', '%s', 200, 2015, 2000000, %s)";
            st.execute(insert.formatted("ACTIVE", "Toyota", "Camry", "now()"));
            st.execute(insert.formatted("SOLD", "TOYOTA", "camry", "now()"));
            st.execute(insert.formatted("ARCHIVED", "Kia", "Rio", "now()"));
            st.execute(insert.formatted("DRAFT", "Toyta", "Camri", "null"));
            st.execute(insert.formatted("ARCHIVED", "Lada", "Niva", "null"));
        }

        flyway(url).load().migrate();

        try (Connection connection = connect(url); Statement st = connection.createStatement())
        {
            List<String> models = new ArrayList<>();
            ResultSet rows = st.executeQuery("select brand || ' ' || model from car_models order by id");
            while (rows.next())
            {
                models.add(rows.getString(1));
            }
            assertThat(models).containsExactlyInAnyOrder("Toyota Camry", "Kia Rio");
        }
    }

    @Test
    void emptyDatabaseGetsNoTechnicalUser() throws SQLException
    {
        String url = createDatabase("empty");

        flyway(url).load().migrate();

        try (Connection connection = connect(url); Statement st = connection.createStatement())
        {
            ResultSet legacy = st.executeQuery("select count(*) from users where username = 'garage-legacy'");
            legacy.next();
            assertThat(legacy.getLong(1)).isZero();
        }
    }

    private FluentConfiguration flyway(String url)
    {
        // То же, что spring.flyway.postgresql.transactional-lock: false в application.yml.
        // Без этого CREATE INDEX CONCURRENTLY из V10 ждёт транзакцию блокировки самого Flyway и висит
        return Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
    }

    private String createDatabase(String name) throws SQLException
    {
        try (Connection connection = connect(postgres.getJdbcUrl()); Statement st = connection.createStatement())
        {
            st.execute("create database " + name);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
    }

    private Connection connect(String url) throws SQLException
    {
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }
}
