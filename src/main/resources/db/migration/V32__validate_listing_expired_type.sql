-- Проверка старых строк под CHECK из V31. SHARE UPDATE EXCLUSIVE: уведомления пишутся, пока она читает таблицу
ALTER TABLE notifications VALIDATE CONSTRAINT chk_notifications_type;
