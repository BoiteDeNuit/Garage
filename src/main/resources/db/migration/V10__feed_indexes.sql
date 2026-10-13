-- Индексы под ленту. Частичные: в ленту попадают только ACTIVE, черновики, архив и проданные
-- в индекс не входят, он меньше и дешевле в обновлении.
-- Колонки идут в порядке сортировки ленты (published_at desc, id desc): страница читается
-- из индекса по порядку и останавливается на 20-й строке, без сортировки всей выборки.
-- CONCURRENTLY: обычный CREATE INDEX на всё время построения запрещает запись в таблицу.
-- Flyway видит CONCURRENTLY и запускает такой скрипт вне транзакции, поэтому других команд здесь нет.
-- Если построение оборвалось, в базе остаётся невалидный индекс (pg_index.indisvalid = false).
-- DROP ... IF EXISTS перед каждым CREATE убирает его при повторном запуске. Если упала сама миграция,
-- Flyway пометит версию 10 как неудачную: перед перезапуском нужен flyway repair
-- (или удалить строку версии 10 с success = false из flyway_schema_history)
DROP INDEX CONCURRENTLY IF EXISTS idx_listings_feed;
CREATE INDEX CONCURRENTLY idx_listings_feed
    ON listings (published_at DESC, id DESC)
    WHERE status = 'ACTIVE';

-- upper(brand) — то же выражение, что строит фильтр по марке (upper(колонка) = upper(?))
DROP INDEX CONCURRENTLY IF EXISTS idx_listings_brand_feed;
CREATE INDEX CONCURRENTLY idx_listings_brand_feed
    ON listings (UPPER(brand), published_at DESC, id DESC)
    WHERE status = 'ACTIVE';
