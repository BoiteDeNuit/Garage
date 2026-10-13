-- Блокировки задач по расписанию (ShedLock). Строка на задачу: экземпляр, который обновил lock_until первым,
-- выполняет запуск, остальные его пропускают. Время — время базы, часы экземпляров могут расходиться
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
