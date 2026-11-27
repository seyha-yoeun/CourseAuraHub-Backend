-- V0089: @mentions in group and course chats. A message keeps who it mentions (only people who could read it);
-- they get a notification even in a muted conversation, and an unread-mention count.

CREATE TABLE IF NOT EXISTS chat_message_mentions (
    message_id integer NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_mentions_user ON chat_message_mentions(user_id, message_id);
