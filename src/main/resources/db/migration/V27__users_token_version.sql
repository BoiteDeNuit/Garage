-- Версия токенов пользователя: она записана в каждый access-токен, и выход отовсюду или блокировка её поднимают.
-- Токены со старой версией фильтр отвергает, хотя срок у них ещё не вышел.
-- NOT NULL DEFAULT с константой Postgres с 11-й версии хранит в каталоге: таблица не переписывается
SET LOCAL lock_timeout = '5s';

ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
