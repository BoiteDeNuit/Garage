-- V8. Машина гаража становится объявлением. Данные, id и последовательность сохраняются.
ALTER TABLE cars RENAME TO listings;
ALTER TABLE listings RENAME CONSTRAINT cars_pkey TO listings_pkey;
ALTER SEQUENCE cars_id_seq RENAME TO listings_id_seq;
ALTER INDEX idx_cars_brand_upper RENAME TO idx_listings_brand_upper;

ALTER TABLE listings
    ADD COLUMN seller_id    BIGINT,
    ADD COLUMN status       VARCHAR(20),
    ADD COLUMN mileage_km   INTEGER,
    ADD COLUMN city         VARCHAR(100),
    ADD COLUMN description  VARCHAR(2000),
    ADD COLUMN created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN version      BIGINT      NOT NULL DEFAULT 0;

-- Старые машины добавлял админ, но его id миграции недоступен: AdminInitializer
-- создаёт админа после Flyway. Старым записям назначается технический продавец.
-- '!' не bcrypt-хэш, войти под ним нельзя. На пустой базе пользователь не создаётся.
INSERT INTO users (username, password_hash, role)
SELECT 'garage-legacy', '!', 'USER'
WHERE EXISTS (SELECT 1 FROM listings)
ON CONFLICT (username) DO NOTHING;

-- В гараже цена 0 была допустима. В объявлении 0 значит "не указана".
UPDATE listings SET price = NULL WHERE price <= 0;

-- Старые записи никто не публиковал: в архив, публично их не видно.
UPDATE listings
SET seller_id = (SELECT id FROM users WHERE username = 'garage-legacy'),
    status    = 'ARCHIVED'
WHERE seller_id IS NULL;

ALTER TABLE listings
    ALTER COLUMN seller_id SET NOT NULL,
    ALTER COLUMN status    SET NOT NULL,
    ADD CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    ADD CONSTRAINT chk_listings_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SOLD', 'ARCHIVED')),
    ADD CONSTRAINT chk_listings_price_positive CHECK (price IS NULL OR price > 0),
    ADD CONSTRAINT chk_listings_mileage CHECK (mileage_km IS NULL OR mileage_km >= 0),
    ADD CONSTRAINT chk_listings_published CHECK (
        status NOT IN ('ACTIVE', 'SOLD') OR (price IS NOT NULL AND published_at IS NOT NULL));

-- Postgres не создаёт индекс под внешний ключ сам. Нужен для "мои объявления".
CREATE INDEX idx_listings_seller ON listings (seller_id);
