-- Уборка удаляет refresh-токены, истёкшие давно, по expires_at. Без индекса каждый запуск читал бы таблицу целиком.
-- CONCURRENTLY не блокирует выдачу и обмен токенов, но не работает внутри транзакции: отдельная миграция, как V22
DROP INDEX CONCURRENTLY IF EXISTS idx_refresh_tokens_expires;
CREATE INDEX CONCURRENTLY idx_refresh_tokens_expires ON refresh_tokens (expires_at);
