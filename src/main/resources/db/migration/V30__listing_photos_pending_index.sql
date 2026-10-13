-- Уборка брошенных загрузок ищет PENDING-фото. Их единицы, а всех фото — до 20 на объявление:
-- частичный индекс маленький, и запуск уборки не читает таблицу целиком. CONCURRENTLY — отдельной миграцией, как V22
DROP INDEX CONCURRENTLY IF EXISTS idx_listing_photos_pending;
CREATE INDEX CONCURRENTLY idx_listing_photos_pending ON listing_photos (id) WHERE status = 'PENDING';
