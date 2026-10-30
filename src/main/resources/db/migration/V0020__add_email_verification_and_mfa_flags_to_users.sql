-- V0020: Email verification and two-factor flags on users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Accounts that existed before email verification was introduced are grandfathered as verified
UPDATE users
SET email_verified = TRUE,
    email_verified_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE email_verified = FALSE;
