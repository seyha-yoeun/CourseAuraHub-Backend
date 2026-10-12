-- V0008: Create platform_settings table
CREATE TABLE IF NOT EXISTS platform_settings (
    id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    platform_name text NOT NULL,
    platform_tagline text NOT NULL,
    currency text NOT NULL,
    default_language text NOT NULL,
    notifications jsonb NOT NULL,
    profile jsonb NOT NULL
);
