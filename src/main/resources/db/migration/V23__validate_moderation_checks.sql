-- Проверка старых строк под CHECK-и из V21. Отдельной транзакцией: VALIDATE берёт SHARE UPDATE EXCLUSIVE
-- и не мешает писать в users и listings, пока читает их
ALTER TABLE users VALIDATE CONSTRAINT chk_users_role;
ALTER TABLE listings VALIDATE CONSTRAINT chk_listings_status;
