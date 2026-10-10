-- Уведомления пользователю. Пока один тип: новое объявление под сохранённый поиск.
-- UNIQUE (user_id, event_id) делает потребителя Kafka идемпотентным: повторная доставка того же события
-- упирается в ключ (insert ... on conflict do nothing) и второго уведомления не создаёт
CREATE TABLE notifications (
    id              BIGSERIAL   PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id        UUID        NOT NULL,
    type            VARCHAR(30) NOT NULL,
    listing_id      BIGINT      NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    saved_search_id BIGINT      REFERENCES saved_searches (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ,
    CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_LISTING')),
    CONSTRAINT uq_notifications_event UNIQUE (user_id, event_id)
);

-- Лента уведомлений пользователя, сначала новые
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
-- Счётчик непрочитанных: частичный индекс только по ним, прочитанные его не раздувают
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
