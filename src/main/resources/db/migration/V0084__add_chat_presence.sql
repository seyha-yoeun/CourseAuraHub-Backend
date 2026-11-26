-- V0084: Chat presence ("Active now", "Active 5m ago"). Each app instance keeps one row per user it has a live
-- WebSocket session for, refreshing seen_at on a heartbeat; a user is online while any of their rows is connected
-- and fresh, so the rows of an instance that stopped go stale on their own. People can hide their status.

CREATE TABLE IF NOT EXISTS chat_presence (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instance_id uuid NOT NULL,
    connected boolean NOT NULL,
    seen_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, instance_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_presence_seen ON chat_presence(seen_at);

ALTER TABLE chat_settings ADD COLUMN IF NOT EXISTS show_presence boolean NOT NULL DEFAULT true;
