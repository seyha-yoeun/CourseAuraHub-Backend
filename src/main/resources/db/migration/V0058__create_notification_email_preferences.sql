-- V0058: Email delivery of notifications. Each user picks instant, daily digest or off per notification type;
-- types without a row use the app's default. emailed_at marks rows already sent so digests never repeat them.

CREATE TABLE IF NOT EXISTS notification_email_preferences (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type text NOT NULL,
    mode text NOT NULL CHECK (mode IN ('INSTANT', 'DIGEST', 'OFF')),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type)
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS emailed_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_notifications_digest ON notifications(created_at)
    WHERE read_at IS NULL AND emailed_at IS NULL;
