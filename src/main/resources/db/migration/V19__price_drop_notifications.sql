-- Уведомление о снижении цены у объявления в избранном: новый тип и цены «было — стало»
ALTER TABLE notifications
    ADD COLUMN old_price NUMERIC(12, 2),
    ADD COLUMN new_price NUMERIC(12, 2);

-- CHECK пересоздаётся с новым значением. NOT VALID: старые строки сейчас не проверяются, только новые,
-- поэтому ALTER держит эксклюзивную блокировку мгновение, а не всё время проверки таблицы.
-- Проверка старых строк — VALIDATE CONSTRAINT в V20: она идёт под SHARE UPDATE EXCLUSIVE и запись не блокирует.
-- В этом же скрипте она бы не помогла: эксклюзивная блокировка от ALTER держится до конца транзакции миграции
ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_LISTING', 'PRICE_DROP')) NOT VALID;
