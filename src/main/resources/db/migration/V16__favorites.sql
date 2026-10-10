-- Избранное: пара «пользователь, объявление». Первичный ключ и есть защита от дублей:
-- повторное добавление ничего не меняет (insert ... on conflict do nothing)
CREATE TABLE favorites (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    listing_id BIGINT      NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, listing_id)
);

-- Обратный путь «кто добавил объявление»: каскад при удалении черновика и, дальше, уведомления
-- о снижении цены. Первичный ключ начинается с user_id и здесь не помогает
CREATE INDEX idx_favorites_listing ON favorites (listing_id);
