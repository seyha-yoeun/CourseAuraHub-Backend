-- V0037: Tracks files uploaded to object storage, so unfinished and replaced uploads can be cleaned up.

CREATE TABLE IF NOT EXISTS stored_files (
    id uuid PRIMARY KEY,
    uploaded_by uuid REFERENCES users(id) ON DELETE SET NULL,
    purpose text NOT NULL,
    object_key text NOT NULL UNIQUE,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL,
    original_name text NOT NULL,
    status text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stored_files_status_updated ON stored_files (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_stored_files_uploader_created ON stored_files (uploaded_by, created_at);
