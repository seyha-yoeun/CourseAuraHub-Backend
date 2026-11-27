-- V0087: Pinned conversations. Each person keeps up to app.chat.max-pinned-conversations of their conversations
-- at the top of their list; the first page of the list returns them before the rest.

ALTER TABLE chat_participants ADD COLUMN IF NOT EXISTS pinned_at timestamptz;
