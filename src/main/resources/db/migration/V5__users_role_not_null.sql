ALTER TABLE users ALTER COLUMN role set not null;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK ( role in( 'USER','ADMIN' ));