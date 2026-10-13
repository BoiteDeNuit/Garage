-- Блокировка пользователей: когда и за что. Обе колонки пустые у всех, пока не заблокирован.
-- Колонки без DEFAULT — только запись в каталоге, таблица не переписывается. CHECK с NOT VALID старые строки
-- не читает, их проверит V25 отдельной транзакцией
SET LOCAL lock_timeout = '5s';

ALTER TABLE users
    ADD COLUMN banned_at  TIMESTAMPTZ,
    ADD COLUMN ban_reason VARCHAR(500),
    ADD CONSTRAINT chk_users_ban CHECK ((banned_at IS NULL) = (ban_reason IS NULL)) NOT VALID;
