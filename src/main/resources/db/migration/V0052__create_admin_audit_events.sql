-- V0052: Who changed what. One row per write request by an admin or instructor.
-- Request bodies are never stored, because they can carry personal data and secrets.

CREATE TABLE IF NOT EXISTS admin_audit_events (
    id bigserial PRIMARY KEY,
    actor_id uuid REFERENCES users(id) ON DELETE SET NULL,
    actor_email text NOT NULL,
    actor_role text NOT NULL,
    action text NOT NULL,
    http_method text NOT NULL,
    path_pattern text NOT NULL,
    resource text NOT NULL,
    resource_id text,
    path_variables text,
    status integer NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    request_id text,
    ip_address text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_events_created ON admin_audit_events(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_events_actor ON admin_audit_events(lower(actor_email), created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_events_resource ON admin_audit_events(resource, resource_id);
