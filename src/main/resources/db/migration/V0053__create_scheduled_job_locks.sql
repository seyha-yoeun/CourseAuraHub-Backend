-- V0053: Leases that let only one instance run a scheduled job at a time.

CREATE TABLE IF NOT EXISTS scheduled_job_locks (
    name text PRIMARY KEY,
    locked_until timestamptz NOT NULL,
    locked_at timestamptz NOT NULL,
    locked_by text NOT NULL
);
