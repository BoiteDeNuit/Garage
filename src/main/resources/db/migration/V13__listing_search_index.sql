-- GIN — инвертированный индекс: лексема -> строки, где она есть. B-tree по tsvector бесполезен,
-- он сравнивает вектор целиком. Частичный, как индексы ленты из V10: ищут только по опубликованным.
-- CONCURRENTLY и повторный запуск — как в V10
DROP INDEX CONCURRENTLY IF EXISTS idx_listings_search;
CREATE INDEX CONCURRENTLY idx_listings_search
    ON listings USING GIN (search_vector)
    WHERE status = 'ACTIVE';
