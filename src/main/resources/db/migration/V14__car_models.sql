-- Справочник марок и моделей для подсказок в поиске. Объявлений миллион, а разных пар «марка модель»
-- сотни: подсказку на каждое нажатие клавиши дешевле искать здесь, чем группировать listings.
-- pg_trgm сравнивает строки по триграммам и прощает опечатки («toyta» -> Toyota). Расширение из contrib,
-- с Postgres 13 доверенное: ставит владелец базы, суперпользователь не нужен
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE car_models (
    id    BIGSERIAL    PRIMARY KEY,
    brand VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL
);

-- Регистр не различаем, как фильтр ленты: Toyota Camry и TOYOTA CAMRY — одна пара, остаётся первое написание
CREATE UNIQUE INDEX uq_car_models_brand_model ON car_models (UPPER(brand), UPPER(model));
-- Под условие q <% (brand || ' ' || model) из запроса подсказок. Новая таблица, поэтому без CONCURRENTLY
CREATE INDEX idx_car_models_trgm ON car_models USING GIN ((brand || ' ' || model) gin_trgm_ops);

-- Только то, что публиковали: черновики с опечатками в подсказки не попадают.
-- Старые машины из Garage (V8) архивные и без даты публикации, их тоже нет
INSERT INTO car_models (brand, model)
SELECT DISTINCT ON (UPPER(brand), UPPER(model)) brand, model
FROM listings
WHERE published_at IS NOT NULL
ORDER BY UPPER(brand), UPPER(model), id;
