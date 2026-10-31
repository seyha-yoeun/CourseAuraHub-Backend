-- V0033: Connects an account to one private Telegram chat, so each person only receives their own notifications.

CREATE TABLE IF NOT EXISTS telegram_links (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    chat_id bigint NOT NULL UNIQUE,
    telegram_name text,
    enabled boolean NOT NULL DEFAULT true,
    linked_at timestamptz NOT NULL DEFAULT now()
);
