-- Очередь модерации: только ждущие проверки, по времени отправки. Частичный индекс всё равно читает всю таблицу,
-- чтобы проверить условие у каждой строки, поэтому CONCURRENTLY и отдельная миграция, как в V10 и V13
DROP INDEX CONCURRENTLY IF EXISTS idx_listings_review_queue;
CREATE INDEX CONCURRENTLY idx_listings_review_queue
    ON listings (submitted_at)
    WHERE status = 'PENDING_REVIEW';
