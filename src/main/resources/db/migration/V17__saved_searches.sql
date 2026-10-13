-- Сохранённые поиски: те же фильтры, что у ленты, одним документом jsonb.
-- Колонка на каждый фильтр означала бы миграцию на каждый новый фильтр, а искать внутри документа нужно
-- только при сопоставлении с новым объявлением. Формат документа — ListingSearchCriteria в JSON
CREATE TABLE saved_searches (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    criteria   JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_saved_searches_criteria CHECK (jsonb_typeof(criteria) = 'object')
);

CREATE INDEX idx_saved_searches_user ON saved_searches (user_id);
