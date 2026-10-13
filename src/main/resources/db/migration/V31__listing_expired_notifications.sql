-- Уведомление продавцу: срок публикации вышел, объявление ушло в архив. Новый тип пересоздаёт CHECK с NOT VALID,
-- старые строки проверит V32 отдельной транзакцией, как V19 и V20
SET LOCAL lock_timeout = '5s';

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_LISTING', 'PRICE_DROP', 'LISTING_EXPIRED')) NOT VALID;
