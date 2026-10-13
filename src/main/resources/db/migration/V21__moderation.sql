-- Модерация: роль MODERATOR, статусы PENDING_REVIEW (ждёт проверки) и REJECTED (отклонено с причиной).
-- CHECK-и пересоздаются с NOT VALID: старые строки сейчас не проверяются, эксклюзивная блокировка на мгновение.
-- Новые колонки без DEFAULT и NOT NULL, внешний ключ на только что добавленной пустой колонке не проверяется:
-- в этой транзакции ничего не читает таблицу целиком. Индекс очереди — V22 (CONCURRENTLY), проверка старых
-- строк — V23 (VALIDATE): любая из них здесь держала бы эксклюзивную блокировку на всё время чтения таблицы.
-- lock_timeout: если таблицу держит долгий запрос, миграция упадёт через 5 секунд, а не встанет в очередь
-- с уже взятой блокировкой users, за которой встали бы все входы и запросы с токеном
SET LOCAL lock_timeout = '5s';

ALTER TABLE users
    DROP CONSTRAINT chk_users_role,
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'MODERATOR')) NOT VALID;

ALTER TABLE listings
    DROP CONSTRAINT chk_listings_status,
    ADD CONSTRAINT chk_listings_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'ACTIVE', 'SOLD', 'ARCHIVED')) NOT VALID,
    -- Когда отправили на проверку: очередь модерации идёт от старых к новым
    ADD COLUMN submitted_at     TIMESTAMPTZ,
    ADD COLUMN rejection_reason VARCHAR(500),
    -- Кто из модераторов взял объявление и когда. Захват — условный UPDATE, а не блокировка строки
    ADD COLUMN moderator_id     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN claimed_at       TIMESTAMPTZ;

-- Журнал смены статусов: кто, когда, из какого в какой и почему. Пишется в той же транзакции, что и переход
CREATE TABLE listing_status_history (
    id          BIGSERIAL    PRIMARY KEY,
    listing_id  BIGINT       NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    reason      VARCHAR(500),
    changed_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_listing_status_history_listing ON listing_status_history (listing_id, changed_at);
