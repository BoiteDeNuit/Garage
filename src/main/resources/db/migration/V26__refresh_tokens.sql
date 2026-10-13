-- Refresh-токены. В базе только SHA-256 от токена: утечка таблицы не даёт войти ни под кем.
-- Семья — цепочка токенов от одного входа: каждый обмен гасит старый и выдаёт новый в той же семье.
-- Погашенный токен пришёл снова — его украли или клиент прислал старый: гасится вся семья.
-- revoke_reason отличает такой повтор от выхода и блокировки: повтор после выхода — не кража
CREATE TABLE refresh_tokens (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      UUID         NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoke_reason  VARCHAR(20),
    replaced_by_id BIGINT       REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_revoke CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT chk_refresh_tokens_reason CHECK (revoke_reason IN ('ROTATED', 'REUSE', 'LOGOUT', 'BAN'))
);

-- Отзыв семьи при повторе и всех токенов пользователя при выходе отовсюду и блокировке: только живые
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
