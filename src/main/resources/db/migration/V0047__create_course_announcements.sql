-- V0047: Announcements an instructor or admin sends to everyone enrolled in a course.

CREATE TABLE IF NOT EXISTS course_announcements (
    id bigserial PRIMARY KEY,
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    author_id uuid REFERENCES users(id) ON DELETE SET NULL,
    author_name text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_course_announcements_course ON course_announcements(course_id, created_at DESC);
