-- Фото объявления. Сам файл лежит в S3, здесь только ключ и состояние.
-- PENDING — ссылка на загрузку выдана, файла может ещё не быть. READY — загрузку подтвердили, фото видно в карточке.
-- Ключ генерирует сервер (UUID), из пользовательского ввода в нём ничего нет
CREATE TABLE listing_photos (
    id           BIGSERIAL    PRIMARY KEY,
    listing_id   BIGINT       NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    object_key   VARCHAR(200) NOT NULL UNIQUE,
    status       VARCHAR(20)  NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    position     INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_listing_photos_status CHECK (status IN ('PENDING', 'READY')),
    CONSTRAINT chk_listing_photos_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_listing_photos_size CHECK (size_bytes > 0),
    -- Не больше 20 фото на объявление держит сама база: место 1–20 и одно фото на место.
    -- Две одновременные загрузки на 20-е место не пройдут обе
    CONSTRAINT chk_listing_photos_position CHECK (position BETWEEN 1 AND 20),
    -- DEFERRABLE INITIALLY DEFERRED: уникальность проверяется при коммите, а не после каждой строки.
    -- Иначе обмен местами двух фото падал бы на промежуточном состоянии, где у обоих одно место
    CONSTRAINT uq_listing_photos_position UNIQUE (listing_id, position) DEFERRABLE INITIALLY DEFERRED
);
