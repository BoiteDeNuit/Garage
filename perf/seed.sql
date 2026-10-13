-- Миллион объявлений для замеров. Не миграция: запускать руками и только на локальной базе,
-- которую уже подняло приложение (Flyway прогнал V1–V9).
--   psql -h localhost -p 5433 -U garage -d garage -f perf/seed.sql
-- Данные детерминированы (setseed), поэтому замеры повторяются от прогона к прогону.
\set ON_ERROR_STOP on
\timing on

DO $$
BEGIN
    IF (SELECT count(*) FROM listings) > 1000 THEN
        RAISE EXCEPTION 'В listings уже больше 1000 строк. Сид только для пустой локальной базы';
    END IF;
END $$;

SELECT setseed(0.42);

-- 500 продавцов. Хэш '!' не совпадёт ни с одним паролем BCrypt: войти под ними нельзя
INSERT INTO users (username, password_hash, role)
SELECT 'perf_seller_' || n, '!', 'USER'
FROM generate_series(1, 500) AS n;

-- Распределение похоже на живую площадку: 70% опубликованы, 10% продано, 10% в архиве, 10% черновики.
-- Марки неравномерные (первые в списке встречаются чаще), год 1995–2025, цена растёт с годом,
-- опубликованы в течение последнего года
WITH sellers AS (
    SELECT array_agg(id) AS ids FROM users WHERE username LIKE 'perf_seller_%'
), raw AS (
    SELECT n,
           floor(power(random(), 2) * 15)::int                        AS brand_i,
           floor(random() * 3)::int                                   AS model_i,
           1995 + floor(random() * 31)::int                           AS year,
           random()                                                   AS status_r,
           floor(random() * 20)::int                                  AS city_i,
           random()                                                   AS spec_r
    FROM generate_series(1, 1000000) AS n
)
INSERT INTO listings (seller_id, status, brand, model, engine_code, horse_power, year, mileage_km, price, city,
                      description, created_at, updated_at, published_at, version, fuel_type, transmission, body_type)
SELECT s.ids[1 + (r.n % 500)],
       st.status,
       (ARRAY['Lada','Toyota','Kia','Hyundai','Volkswagen','Skoda','Renault','BMW','Mercedes-Benz','Nissan',
              'Mazda','Ford','Chery','Haval','Geely'])[r.brand_i + 1],
       (ARRAY['Vesta','Granta','Niva','Camry','Corolla','RAV4','Rio','Sportage','Ceed','Solaris','Creta','Tucson',
              'Polo','Tiguan','Passat','Octavia','Rapid','Kodiaq','Logan','Duster','Arkana','X5','3 Series','5 Series',
              'E-Class','C-Class','GLE','Qashqai','X-Trail','Almera','CX-5','Mazda6','Mazda3','Focus','Kuga','Mondeo',
              'Tiggo 7','Tiggo 4','Arrizo 8','Jolion','F7','H6','Coolray','Monjaro','Atlas'])[r.brand_i * 3 + r.model_i + 1],
       NULL,
       80 + floor(random() * 300)::int,
       r.year,
       floor((2026 - r.year) * 12000 + random() * 30000)::int,
       CASE WHEN st.status = 'DRAFT' AND random() < 0.5 THEN NULL
            ELSE round((300000 + (r.year - 1995) * 120000 + random() * 1500000)::numeric, -3) END,
       (ARRAY['Москва','Санкт-Петербург','Самара','Казань','Новосибирск','Екатеринбург','Нижний Новгород',
              'Тольятти','Уфа','Краснодар','Ростов-на-Дону','Воронеж','Пермь','Волгоград','Челябинск','Омск',
              'Саратов','Тюмень','Ижевск','Барнаул'])[r.city_i + 1],
       'Сид для замеров #' || r.n,
       st.created_at,
       st.created_at,
       CASE WHEN st.status = 'DRAFT' THEN NULL ELSE st.created_at + interval '1 hour' END,
       0,
       (ARRAY['PETROL','PETROL','PETROL','DIESEL','HYBRID','ELECTRIC','GAS'])[1 + floor(r.spec_r * 7)::int],
       (ARRAY['MANUAL','AUTOMATIC','AUTOMATIC','ROBOT','CVT'])[1 + floor(random() * 5)::int],
       (ARRAY['SEDAN','SEDAN','HATCHBACK','WAGON','SUV','SUV','COUPE','CONVERTIBLE','MINIVAN','PICKUP'])[1 + floor(random() * 10)::int]
FROM raw r
CROSS JOIN sellers s
CROSS JOIN LATERAL (
    SELECT CASE WHEN r.status_r < 0.7 THEN 'ACTIVE'
                WHEN r.status_r < 0.8 THEN 'SOLD'
                WHEN r.status_r < 0.9 THEN 'ARCHIVED'
                ELSE 'DRAFT' END AS status,
           now() - interval '365 days' * random() AS created_at
) st;

VACUUM ANALYZE users;
VACUUM ANALYZE listings;

SELECT status, count(*) FROM listings GROUP BY status ORDER BY status;
SELECT pg_size_pretty(pg_total_relation_size('listings')) AS listings_size;
