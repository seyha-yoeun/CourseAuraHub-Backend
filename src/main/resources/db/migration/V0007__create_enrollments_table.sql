-- V0007: Create enrollments table and index
CREATE TABLE IF NOT EXISTS enrollments (
    id integer PRIMARY KEY,
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    enrolled_at date NOT NULL,
    amount numeric(10, 2) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_enrolled_at ON enrollments(enrolled_at DESC);
