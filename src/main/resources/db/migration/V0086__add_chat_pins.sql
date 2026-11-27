-- V0086: Pinned messages. A conversation keeps up to app.chat.max-pins of them in view at the top; who may pin
-- depends on the conversation (ChatAccess.canPin). Pins of unsent or hidden messages stop showing.

CREATE TABLE IF NOT EXISTS chat_pins (
    conversation_id integer NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    message_id integer NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    pinned_by uuid REFERENCES users(id) ON DELETE SET NULL,
    pinned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_pins_message ON chat_pins(message_id);
