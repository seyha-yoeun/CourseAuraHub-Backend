-- V0005: Create students table and index
CREATE TABLE IF NOT EXISTS students (
    id integer PRIMARY KEY,
    name text NOT NULL,
    email text NOT NULL UNIQUE,
    avatar text NOT NULL,
    total_spent numeric(10, 2) NOT NULL DEFAULT 0 CHECK (total_spent >= 0),
    joined_at date NOT NULL,
    last_active date NOT NULL,
    status text NOT NULL,
    country text NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_students_status ON students(status);
