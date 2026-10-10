-- Проверка старых строк под CHECK из V24. SHARE UPDATE EXCLUSIVE: входы и регистрации идут, пока она читает users
ALTER TABLE users VALIDATE CONSTRAINT chk_users_ban;
