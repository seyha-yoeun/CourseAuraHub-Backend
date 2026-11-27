-- V0088: Searching message history. Trigram matching (pg_trgm) finds text anywhere inside words, which Postgres'
-- word search can't do for Khmer (it doesn't put spaces between words); the index keeps ILIKE '%term%' fast.
-- pg_trgm ships with Postgres and is a trusted extension, so the database owner can create it.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_chat_messages_body_trgm ON chat_messages USING gin (body gin_trgm_ops)
    WHERE kind = 'TEXT' AND deleted_at IS NULL AND hidden_at IS NULL;
