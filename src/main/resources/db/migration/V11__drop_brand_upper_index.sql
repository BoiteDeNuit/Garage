-- Индекс по UPPER(brand) из V6 стал лишним: марку ищут только в ленте, а её закрывает
-- idx_listings_brand_feed из V10. Лишний индекс замедляет каждую запись в таблицу.
-- CONCURRENTLY: обычный DROP INDEX берёт эксклюзивную блокировку таблицы и ждёт конца всех
-- открытых транзакций, а за ним в очередь встают все чтения и записи listings
DROP INDEX CONCURRENTLY IF EXISTS idx_listings_brand_upper;
