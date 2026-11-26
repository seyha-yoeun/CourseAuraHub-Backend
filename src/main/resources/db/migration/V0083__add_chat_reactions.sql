-- V0083: Emoji reactions on chat messages. One reaction per person per message (choosing another replaces it);
-- the allowed emoji are checked by the service (ChatReactions), so changing the set needs no migration.

CREATE TABLE IF NOT EXISTS chat_message_reactions (
    message_id integer NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_reactions_user ON chat_message_reactions(user_id);
