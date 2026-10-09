-- Вектор для полнотекстового поиска. Марка и модель с весом A, описание с весом B:
-- при ранжировании совпадение в марке и модели весит больше, чем в описании.
-- Словарь 'russian' указан явно. С ним to_tsvector IMMUTABLE, без него функция зависит
-- от default_text_search_config, она STABLE, и Postgres не примет её в generated column.
-- Латиница в словаре russian идёт через английский стеммер, поэтому марки на латинице тоже ищутся.
-- STORED: вектор считается при INSERT и UPDATE и хранится в строке. Запросу не нужно пересчитывать
-- to_tsvector, ни для ts_rank, ни для перепроверки строк после GIN.
-- Сущность колонку не маппит (ddl-auto validate лишние колонки не проверяет), Hibernate её не пишет.
-- ADD COLUMN ... STORED переписывает таблицу под ACCESS EXCLUSIVE: пока идёт перезапись, таблица закрыта
-- и для чтения. Замер на миллионе строк — в perf/README.md. Индекс отдельно, в V13: CONCURRENTLY нельзя
-- смешивать в одном скрипте с командами, которым нужна транзакция
ALTER TABLE listings
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('russian', coalesce(brand, '') || ' ' || coalesce(model, '')), 'A') ||
        setweight(to_tsvector('russian', coalesce(description, '')), 'B')
    ) STORED;
