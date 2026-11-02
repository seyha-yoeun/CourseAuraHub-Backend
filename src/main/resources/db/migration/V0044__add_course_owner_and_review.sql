-- V0044: Instructors own the courses they create. Courses without an owner stay admin-managed.

ALTER TABLE courses ADD COLUMN IF NOT EXISTS owner_id uuid REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS review_note text;

CREATE INDEX IF NOT EXISTS idx_courses_owner ON courses (owner_id) WHERE owner_id IS NOT NULL;
