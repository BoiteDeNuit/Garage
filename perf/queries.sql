-- Запросы ленты в том виде, в каком их строит Hibernate (show-sql), с подставленными значениями.
-- На каждую страницу Spring Data делает два запроса: саму страницу и count для totalElements.
-- Запуск: psql -h localhost -p 5433 -U garage -d garage -f perf/queries.sql
-- Значения вписаны литералами. Драйвер шлёт параметры, но первые 5 выполнений Postgres всё равно
-- строит план под конкретные значения, так что планы совпадают

-- feed: Лента без фильтров, первая страница
EXPLAIN (ANALYZE, BUFFERS)
select l1_0.id,l1_0.body_type,l1_0.brand,l1_0.city,l1_0.created_at,l1_0.description,l1_0.engine_code,l1_0.fuel_type,l1_0.horse_power,l1_0.mileage_km,l1_0.model,l1_0.price,l1_0.published_at,l1_0.seller_id,l1_0.status,l1_0.transmission,l1_0.updated_at,l1_0.version,l1_0.year from listings l1_0 where l1_0.status='ACTIVE' order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only;
-- feed_count
EXPLAIN (ANALYZE, BUFFERS)
select count(l1_0.id) from listings l1_0 where l1_0.status='ACTIVE';

-- brand: Марка
EXPLAIN (ANALYZE, BUFFERS)
select l1_0.id,l1_0.body_type,l1_0.brand,l1_0.city,l1_0.created_at,l1_0.description,l1_0.engine_code,l1_0.fuel_type,l1_0.horse_power,l1_0.mileage_km,l1_0.model,l1_0.price,l1_0.published_at,l1_0.seller_id,l1_0.status,l1_0.transmission,l1_0.updated_at,l1_0.version,l1_0.year from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.brand)=upper('Toyota') order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only;
-- brand_count
EXPLAIN (ANALYZE, BUFFERS)
select count(l1_0.id) from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.brand)=upper('Toyota');

-- brand_year_price: Марка + год + цена
EXPLAIN (ANALYZE, BUFFERS)
select l1_0.id,l1_0.body_type,l1_0.brand,l1_0.city,l1_0.created_at,l1_0.description,l1_0.engine_code,l1_0.fuel_type,l1_0.horse_power,l1_0.mileage_km,l1_0.model,l1_0.price,l1_0.published_at,l1_0.seller_id,l1_0.status,l1_0.transmission,l1_0.updated_at,l1_0.version,l1_0.year from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.brand)=upper('Toyota') and l1_0.year between 2010 and 2020 and l1_0.price<=3000000 order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only;
-- brand_year_price_count
EXPLAIN (ANALYZE, BUFFERS)
select count(l1_0.id) from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.brand)=upper('Toyota') and l1_0.year between 2010 and 2020 and l1_0.price<=3000000;

-- city_body: Город + кузов
EXPLAIN (ANALYZE, BUFFERS)
select l1_0.id,l1_0.body_type,l1_0.brand,l1_0.city,l1_0.created_at,l1_0.description,l1_0.engine_code,l1_0.fuel_type,l1_0.horse_power,l1_0.mileage_km,l1_0.model,l1_0.price,l1_0.published_at,l1_0.seller_id,l1_0.status,l1_0.transmission,l1_0.updated_at,l1_0.version,l1_0.year from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.city)=upper('Самара') and l1_0.body_type='SEDAN' order by l1_0.published_at desc,l1_0.id desc offset 0 rows fetch first 20 rows only;
-- city_body_count
EXPLAIN (ANALYZE, BUFFERS)
select count(l1_0.id) from listings l1_0 where l1_0.status='ACTIVE' and upper(l1_0.city)=upper('Самара') and l1_0.body_type='SEDAN';

-- deep_page: Лента, страница 5000 (offset 100000)
EXPLAIN (ANALYZE, BUFFERS)
select l1_0.id,l1_0.body_type,l1_0.brand,l1_0.city,l1_0.created_at,l1_0.description,l1_0.engine_code,l1_0.fuel_type,l1_0.horse_power,l1_0.mileage_km,l1_0.model,l1_0.price,l1_0.published_at,l1_0.seller_id,l1_0.status,l1_0.transmission,l1_0.updated_at,l1_0.version,l1_0.year from listings l1_0 where l1_0.status='ACTIVE' order by l1_0.published_at desc,l1_0.id desc offset 100000 rows fetch first 20 rows only;
