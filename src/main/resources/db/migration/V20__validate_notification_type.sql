-- Проверка старых строк уведомлений под новый CHECK из V19. Отдельной миграцией, то есть отдельной транзакцией:
-- VALIDATE берёт SHARE UPDATE EXCLUSIVE и не мешает вставлять уведомления, пока читает таблицу
ALTER TABLE notifications VALIDATE CONSTRAINT chk_notifications_type;
