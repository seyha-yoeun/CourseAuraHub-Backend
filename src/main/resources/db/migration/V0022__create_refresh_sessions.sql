-- V0022: Multi-device refresh sessions. Each row is one refresh token; rotation revokes the row and
-- inserts a child in the same family. family_id is the session id exposed to clients.
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    session_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(40),
    replaced_by UUID,
    user_agent VARCHAR(255),
    ip_address VARCHAR(64)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_sessions_token_hash ON refresh_sessions(token_hash);
-- A family can never have two usable refresh tokens at once
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_sessions_active_family
    ON refresh_sessions(family_id)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_active ON refresh_sessions(user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_family ON refresh_sessions(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires ON refresh_sessions(expires_at);
