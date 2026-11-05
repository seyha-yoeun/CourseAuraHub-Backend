-- V0049: Public instructor profiles. Name, bio, avatar, website and Twitter stay on users;
-- this adds the public handle and the instructor-only fields.

CREATE TABLE IF NOT EXISTS instructor_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    handle text NOT NULL UNIQUE,
    headline text,
    linkedin_url text,
    github_url text,
    youtube_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_courses_owner_status ON courses(owner_id, status);
