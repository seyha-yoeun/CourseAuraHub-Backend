-- V0019: Add token version to users so issued access tokens can be revoked (logout, password change)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
