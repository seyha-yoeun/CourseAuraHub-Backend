-- V0021: Hashed one-time secrets (email verification OTPs, password reset tokens, 2FA login challenges)
CREATE TABLE IF NOT EXISTS auth_one_time_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_auth_one_time_tokens_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

-- At most one usable secret per user and purpose: issuing a new one must revoke the previous one first
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_one_time_tokens_active
    ON auth_one_time_tokens(user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_one_time_tokens_hash ON auth_one_time_tokens(purpose, token_hash);
CREATE INDEX IF NOT EXISTS idx_auth_one_time_tokens_user_created ON auth_one_time_tokens(user_id, purpose, created_at);
CREATE INDEX IF NOT EXISTS idx_auth_one_time_tokens_expires ON auth_one_time_tokens(expires_at);
