-- V0082: Chat replies (a message can quote an earlier one in the same conversation) and "delete for me"
-- (a person removes a message from their own view only; "unsend" for everyone stays chat_messages.deleted_at).

ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS reply_to_message_id integer
    REFERENCES chat_messages(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS chat_message_hidden_for (
    message_id integer NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_hidden_for_user ON chat_message_hidden_for(user_id);
