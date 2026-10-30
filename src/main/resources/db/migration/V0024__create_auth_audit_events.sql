-- V0024: Persistent authentication audit trail. No FK to users so records outlive account deletion.
CREATE TABLE IF NOT EXISTS auth_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    user_id UUID,
    email VARCHAR(150),
    session_id UUID,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    reason VARCHAR(100),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_events_user_created ON auth_audit_events(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auth_audit_events_type_created ON auth_audit_events(event_type, created_at DESC);
